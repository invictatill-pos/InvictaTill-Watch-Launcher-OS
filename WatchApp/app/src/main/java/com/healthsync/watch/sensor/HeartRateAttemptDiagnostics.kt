package com.healthsync.watch.sensor

/** One bounded acquisition summary. No raw pulse values or unbounded event history are kept. */
internal class HeartRateAttemptDiagnostics(
    private val startedAtMs: Long,
    candidates: List<String>,
    private val permissionGranted: Boolean
) {
    private val candidateDescriptions = candidates.take(4).map { it.take(300) }
    private val candidateCount = candidates.size
    private val registrations = mutableListOf<String>()
    private val rejected = mutableMapOf<HeartRateFrameRejection, Int>()
    private val timestampChecks = mutableMapOf<HeartRateFrameRejection, Int>()
    private var plausibleValues = 0
    private var missingValues = 0
    private var nonFiniteValues = 0
    private var outOfRangeValues = 0
    private var unverifiedPreviews = 0
    var events = 0
        private set
    var accepted = 0
        private set
    private var lastAccuracy: Int? = null
    private var selectedSensor: String? = null
    private var registrationCount = 0
    private var lastEventNanos: Long? = null
    private var lastObservedNanos: Long? = null
    private var acquisitionStartNanos: Long? = null

    fun registration(sensor: String, outcome: String, selected: Boolean = false) {
        registrationCount = increment(registrationCount)
        if (selected) selectedSensor = sensor.take(300)
        if (registrations.size < 4) registrations += "${sensor.take(300)} -> ${outcome.take(120)}"
    }

    fun frame(rejection: HeartRateFrameRejection?, accuracy: Int, eventNanos: Long? = null,
              observedNanos: Long? = null, startedNanos: Long? = null, value: Float? = null,
              timestampIssue: HeartRateFrameRejection? = null, unverifiedPreview: Boolean = false) {
        events = increment(events)
        lastAccuracy = accuracy
        lastEventNanos = eventNanos
        lastObservedNanos = observedNanos
        acquisitionStartNanos = startedNanos
        when {
            value == null -> missingValues = increment(missingValues)
            !value.isFinite() -> nonFiniteValues = increment(nonFiniteValues)
            value !in 25f..240f -> outOfRangeValues = increment(outOfRangeValues)
            else -> plausibleValues = increment(plausibleValues)
        }
        if (timestampIssue != null) timestampChecks[timestampIssue] = increment(timestampChecks[timestampIssue] ?: 0)
        if (unverifiedPreview) unverifiedPreviews = increment(unverifiedPreviews)
        if (rejection == null) accepted = increment(accepted)
        else rejected[rejection] = increment(rejected[rejection] ?: 0)
    }

    fun finalStatus(hasUnverifiedPreview: Boolean = false): String = when {
        accepted > 0 -> "reading"
        hasUnverifiedPreview -> "unreliable_accuracy"
        events == 0 -> "no_events"
        rejected[HeartRateFrameRejection.NO_CONTACT] == events -> "no_contact"
        else -> "invalid_samples"
    }

    fun report(status: String): String = buildString {
        appendLine("Last attempt started (wall ms): $startedAtMs")
        appendLine("Attempt permission: ${if (permissionGranted) "granted" else "denied"}; result: $status")
        appendLine("Sampling: SENSOR_DELAY_NORMAL; preferred single sensor; stops after one capture; maximum 60 seconds")
        appendLine("Candidates at acquisition: $candidateCount (first ${candidateDescriptions.size} shown)")
        candidateDescriptions.forEach { appendLine("  $it") }
        appendLine("Registration attempts: $registrationCount (first ${registrations.size} shown)")
        if (registrations.isEmpty()) appendLine("  None") else registrations.forEach { appendLine("  $it") }
        appendLine("Selected sensor: ${selectedSensor ?: "none"}")
        appendLine("Frames received: $events; accepted: $accepted; last accuracy: ${lastAccuracy ?: "none"}")
        appendLine("Last event elapsed nanos: ${lastEventNanos ?: "none"}; observed elapsed nanos: ${lastObservedNanos ?: "none"}")
        appendLine("Acquisition start elapsed nanos: ${acquisitionStartNanos ?: "none"}")
        appendLine("Last frame age: ${elapsedMs(lastObservedNanos, lastEventNanos)}; capture offset from request: ${elapsedMs(lastEventNanos, acquisitionStartNanos)}")
        appendLine("Rejected no contact: ${rejected[HeartRateFrameRejection.NO_CONTACT] ?: 0}")
        appendLine("Rejected accuracy: ${rejected[HeartRateFrameRejection.ACCURACY] ?: 0}; value: ${rejected[HeartRateFrameRejection.VALUE] ?: 0}")
        appendLine("Independent value checks: in BPM range=$plausibleValues; missing=$missingValues; nonfinite=$nonFiniteValues; out of range=$outOfRangeValues")
        appendLine("Unverified sensor previews: $unverifiedPreviews (accuracy 0; never accepted as measurements)")
        val timestampReasons = HeartRateFrameRejection.values().filter { it.name.startsWith("TIMESTAMP_") }
        appendLine("Rejected timestamp: ${timestampReasons.sumOf { rejected[it] ?: 0 }}")
        timestampReasons.forEach { appendLine("  ${it.name.lowercase()}: ${rejected[it] ?: 0}") }
        appendLine("Independent timestamp failures (also checked on rejected accuracy): ${timestampChecks.values.sum()}")
        timestampReasons.forEach { appendLine("  checked_${it.name.lowercase()}: ${timestampChecks[it] ?: 0}") }
        append("Acceptance requires accuracy 1..3, 25..240 BPM, and a new capture timestamp from this acquisition no more than 10 seconds old. Accuracy 0 is displayed as sensor feedback with its capture age; it never enters health history or workout statistics.")
    }.take(6_500)

    private fun increment(count: Int) = (count + 1).coerceAtMost(1_000_000)
    private fun elapsedMs(later: Long?, earlier: Long?): String {
        if (later == null || earlier == null) return "none"
        val difference = later - earlier
        if (((later xor earlier) and (later xor difference)) < 0L) return "invalid / overflow"
        return "${difference / 1_000_000L} ms"
    }
}
