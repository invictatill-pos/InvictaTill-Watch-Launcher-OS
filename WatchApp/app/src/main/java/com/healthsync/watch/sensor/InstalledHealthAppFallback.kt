package com.healthsync.watch.sensor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

enum class HealthAppFallbackStatus { FOUND, NO_DATA, UNSUPPORTED, PERMISSION_DENIED, ERROR, BUSY, TIMEOUT }

data class HealthAppFallbackResult(
    val reading: HealthAppReading? = null,
    val status: HealthAppFallbackStatus,
    val message: String
)

data class InstalledHealthApp(val packageName: String, val label: String, val suggested: Boolean)

/**
 * Read-only access to public health exports. This never opens another app's private database,
 * arbitrary provider root, files, or raw optical data. Only named metric endpoints are probed.
 *
 * Providers can opt in with string metadata prefixed `com.healthsync.export.heart_rate.` or
 * `com.healthsync.export.spo2.`: `uri`, `value_column`, `timestamp_column`, `unit` (bpm/percent),
 * and `timestamp_unit` (epoch_ms/epoch_seconds). The URI must belong to that exported provider.
 * Otherwise, named heart_rate/heart_rate_bpm/heartrate or spo2/oxygen_saturation endpoints
 * must return explicit metric columns and an original epoch timestamp. Untyped values fail.
 */
class InstalledHealthAppFallback(context: Context) {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager

    /** Run off the UI thread. Returns within five seconds even if a provider ignores cancellation. */
    fun readLatest(metric: HealthAppMetric, now: Long = System.currentTimeMillis(),
                   maximumAgeMs: Long = HealthAppReadingPolicy.MAX_AGE_MS): HealthAppFallbackResult {
        val signal = AtomicReference<CancellationSignal?>()
        val future: Future<HealthAppFallbackResult> = try {
            queryWorker.submit<HealthAppFallbackResult> { readBlocking(metric, now, maximumAgeMs, signal) }
        } catch (_: RejectedExecutionException) {
            return result(HealthAppFallbackStatus.BUSY, "Health app data check is already running")
        }
        return try {
            future.get(TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            cancelWorker.execute { signal.get()?.cancel() }
            result(HealthAppFallbackStatus.TIMEOUT, "Health app data check timed out")
        } catch (_: InterruptedException) {
            future.cancel(true)
            cancelWorker.execute { signal.get()?.cancel() }
            Thread.currentThread().interrupt()
            result(HealthAppFallbackStatus.ERROR, "Health app data check was interrupted")
        } catch (_: Exception) {
            result(HealthAppFallbackStatus.ERROR, "Health app data is unavailable")
        }
    }

    /** Suggested health apps first; all other launchable apps remain available for manual opening. */
    @Suppress("DEPRECATION")
    fun listApps(): List<InstalledHealthApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, 0).mapNotNull { resolved ->
            val info = resolved.activityInfo ?: return@mapNotNull null
            if (!info.enabled || !info.applicationInfo.enabled || info.packageName == context.packageName) return@mapNotNull null
            val label = resolved.loadLabel(packageManager).toString().ifBlank { info.packageName }
            InstalledHealthApp(info.packageName, label,
                HealthAppReadingPolicy.isSuggestedHealthApp(info.packageName, label))
        }.distinctBy { it.packageName }.sortedWith(compareByDescending<InstalledHealthApp> { it.suggested }.thenBy { it.label })
    }

    fun openHealthApp(packageName: String): Boolean = try {
        if (listApps().none { it.packageName == packageName }) false else {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) false else {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    private fun readBlocking(metric: HealthAppMetric, now: Long, maximumAgeMs: Long,
                             signalRef: AtomicReference<CancellationSignal?>): HealthAppFallbackResult {
        val deadline = SystemClock.elapsedRealtime() + TOTAL_TIMEOUT_MS - 100L
        val records = mutableListOf<HealthAppReading>()
        var queried = 0
        var denied = false
        var failed = false
        var available = false
        var providersVisited = 0
        for (app in listApps().take(MAX_DISCOVERY_APPS)) {
            if (shouldStop(deadline)) break
            val providers = try {
                packageManager.getPackageInfo(app.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA).providers.orEmpty()
            } catch (_: Exception) { continue }
            for (provider in providers) {
                if (providersVisited >= MAX_PROVIDERS || queried >= MAX_QUERIES || shouldStop(deadline)) break
                if (!provider.exported || !provider.enabled || !provider.applicationInfo.enabled) continue
                if (!app.suggested && !hasDeclaration(provider, metric)) continue
                providersVisited++
                val endpoints = endpoints(provider, metric)
                if (endpoints.isEmpty()) continue
                for (endpoint in endpoints) {
                    if (queried >= MAX_QUERIES || shouldStop(deadline)) break
                    val permission = provider.readPermission
                    if (!permission.isNullOrEmpty() && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                        denied = true
                        continue
                    }
                    queried++
                    val signal = CancellationSignal()
                    signalRef.set(signal)
                    val cancel = cancelWorker.schedule({ signal.cancel() }, QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    try {
                        // Read column names at a named metric endpoint; never consume untyped rows.
                        val schema = endpoint.schema ?: context.contentResolver.query(endpoint.uri, null,
                            null, null, null, signal)?.use { cursor ->
                            HealthAppReadingPolicy.schemaFromColumns(metric, cursor.columnNames.toList())
                        }
                        if (schema != null && !signal.isCanceled && !shouldStop(deadline) &&
                            (endpoint.schema != null || queried < MAX_QUERIES)) {
                            available = true
                            if (endpoint.schema == null) queried++
                            context.contentResolver.query(endpoint.uri,
                                arrayOf(schema.valueColumn, schema.timestampColumn), null, null,
                                "${schema.timestampColumn} DESC", signal)?.use { cursor ->
                                readCursor(cursor, schema, app, endpoint.uri, metric, now,
                                    maximumAgeMs, deadline, signal, records)
                            }
                        }
                    } catch (_: SecurityException) {
                        denied = true
                    } catch (_: OperationCanceledException) {
                        failed = true
                    } catch (_: IllegalArgumentException) {
                        // Unknown URI/schema means this provider has no compatible export at this path.
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        cancel.cancel(false)
                        signalRef.compareAndSet(signal, null)
                    }
                }
            }
        }
        val latest = HealthAppReadingPolicy.latest(records)
        if (latest != null) return HealthAppFallbackResult(latest, HealthAppFallbackStatus.FOUND,
            "Saved reading from ${latest.sourceLabel}")
        return when {
            denied -> result(HealthAppFallbackStatus.PERMISSION_DENIED, "The health app has not shared permission to read its data")
            failed -> result(HealthAppFallbackStatus.ERROR, "The health app could not share a reading")
            available -> result(HealthAppFallbackStatus.NO_DATA, "No recent timestamped reading was shared by a health app")
            else -> result(HealthAppFallbackStatus.UNSUPPORTED, "The installed health apps do not share readable measurement data. Open the original app to measure there.")
        }
    }

    private fun readCursor(cursor: Cursor, schema: HealthAppColumnSchema, app: InstalledHealthApp,
                           uri: Uri, metric: HealthAppMetric, now: Long, maximumAgeMs: Long,
                           deadline: Long, signal: CancellationSignal, readings: MutableList<HealthAppReading>) {
        val valueIndex = cursor.getColumnIndex(schema.valueColumn)
        val timeIndex = cursor.getColumnIndex(schema.timestampColumn)
        if (valueIndex < 0 || timeIndex < 0) return
        var count = 0
        while (count++ < MAX_ROWS && !shouldStop(deadline) && !signal.isCanceled && cursor.moveToNext()) {
            if (cursor.isNull(valueIndex) || cursor.isNull(timeIndex)) continue
            val reading = HealthAppReadingPolicy.parse(metric, cursor.getString(valueIndex), cursor.getString(timeIndex),
                schema.timestampUnit, now, maximumAgeMs, app.packageName, app.label, uri.toString())
            if (reading != null) readings += reading
        }
    }

    private data class Endpoint(val uri: Uri, val schema: HealthAppColumnSchema? = null)

    private fun hasDeclaration(provider: ProviderInfo, metric: HealthAppMetric): Boolean =
        provider.metaData?.containsKey("${prefix(metric)}uri") == true

    private fun endpoints(provider: ProviderInfo, metric: HealthAppMetric): List<Endpoint> {
        val authorities = provider.authority?.split(';')?.filter { it.isNotBlank() }?.take(2).orEmpty()
        val metadata = provider.metaData
        val key = prefix(metric)
        val declaredUri = metadata?.getString("${key}uri")
        if (declaredUri != null) {
            val uri = Uri.parse(declaredUri)
            val schema = HealthAppReadingPolicy.declaredSchema(metadata.getString("${key}value_column"),
                metadata.getString("${key}timestamp_column"), metadata.getString("${key}unit"),
                metadata.getString("${key}timestamp_unit"), metric)
            if (uri.scheme != "content" || uri.authority !in authorities || uri.path.isNullOrBlank() ||
                uri.path == "/" || uri.query != null || uri.fragment != null || schema == null) return emptyList()
            return listOf(Endpoint(uri, schema))
        }
        val paths = if (metric == HealthAppMetric.HEART_RATE) listOf("heart_rate", "heart_rate_bpm", "heartrate")
            else listOf("spo2", "oxygen_saturation", "spo2_percent")
        return authorities.flatMap { authority -> paths.map { path ->
            Endpoint(Uri.Builder().scheme("content").authority(authority).appendPath(path).build())
        } }
    }

    private fun prefix(metric: HealthAppMetric) = "com.healthsync.export.${if (metric == HealthAppMetric.HEART_RATE) "heart_rate" else "spo2"}."
    private fun shouldStop(deadline: Long) = Thread.currentThread().isInterrupted || SystemClock.elapsedRealtime() >= deadline
    private fun result(status: HealthAppFallbackStatus, message: String) = HealthAppFallbackResult(status = status, message = message)

    companion object {
        private const val TOTAL_TIMEOUT_MS = 5_000L
        private const val QUERY_TIMEOUT_MS = 750L
        private const val MAX_DISCOVERY_APPS = 64
        private const val MAX_PROVIDERS = 12
        private const val MAX_QUERIES = 18
        private const val MAX_ROWS = 100
        // A stuck third-party provider cannot cause unbounded threads or queued retry work.
        private val queryWorker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, SynchronousQueue(),
            { runnable -> Thread(runnable, "HealthAppExportRead").apply { isDaemon = true } })
        private val cancelWorker = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "HealthAppExportCancel").apply { isDaemon = true }
        }
    }
}
