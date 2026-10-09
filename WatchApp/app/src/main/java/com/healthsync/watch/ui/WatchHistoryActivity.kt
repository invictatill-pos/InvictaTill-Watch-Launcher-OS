package com.healthsync.watch.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.healthsync.watch.R
import com.healthsync.watch.data.WorkoutSessionPayload
import com.healthsync.watch.service.WorkoutTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchHistoryActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_history)
        recycler = findViewById(R.id.historyRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                scaleHistoryItems(recyclerView)
            }
        })
        recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scaleHistoryItems(recycler) }
        recycler.isFocusable = true
        recycler.isFocusableInTouchMode = true
        recycler.setOnGenericMotionListener { _, event ->
            if (Build.VERSION.SDK_INT >= 26 && event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                recycler.scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * 48 * resources.displayMetrics.density).toInt())
                true
            } else false
        }
        findViewById<View>(R.id.btnHistoryStart).setOnClickListener {
            startActivity(Intent(this, WorkoutSelectionActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val workouts = withContext(Dispatchers.IO) { WorkoutHistoryStore.read(applicationContext) }
            recycler.adapter = HistoryAdapter(this@WatchHistoryActivity, workouts)
            findViewById<TextView>(R.id.tvHistoryInfo).text = if (workouts.isEmpty()) "Your first session starts here"
                else "${workouts.size} sessions • ${workouts.sumOf { it.duration_sec.toLong() } / 60} active min"
            findViewById<View>(R.id.historyEmpty).visibility = if (workouts.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (workouts.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}

class HistoryAdapter(
    private val context: Context,
    private val items: List<WorkoutSessionPayload>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    private val dateFormat = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    init { setHasStableIds(true) }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val accentBar: View = view.findViewById(R.id.accentBar)
        val tvType: TextView = view.findViewById(R.id.tvHistoryType)
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val tvDuration: TextView = view.findViewById(R.id.tvHistoryDuration)
        val tvMetric: TextView = view.findViewById(R.id.tvHistoryMetric)
        val tvKcal: TextView = view.findViewById(R.id.tvHistoryKcal)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workout = items[position]
        val type = workout.activity_type.uppercase(Locale.ROOT).replace(" ", "_")
        val accent = when {
            type.contains("RUN") -> "#FFBC6A"
            type.contains("WALK") -> "#8CE7C8"
            type.contains("CYCL") -> "#9FC3FF"
            type.contains("BADMINTON") -> "#86D9E9"
            type.contains("YOGA") -> "#ECAAE9"
            else -> "#C3B0FF"
        }
        holder.accentBar.setBackgroundColor(Color.parseColor(accent))
        val displayType = workout.activity_type.replace("_", " ").lowercase(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        holder.tvType.text = displayType
        holder.tvType.setTextColor(Color.parseColor(accent))
        holder.tvDate.text = dateFormat.format(Date(workout.start_time))
        val seconds = workout.duration_sec.coerceAtLeast(0)
        holder.tvDuration.text = if (seconds >= 3600)
            String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
        holder.tvMetric.text = when {
            type.contains("HOME") || type.contains("STRENGTH") -> "${workout.swings} reps"
            type.contains("BASKETBALL") -> "${workout.swings} moves"
            type.contains("BADMINTON") || type.contains("CRICKET") -> "${workout.swings} swings"
            workout.distance_m > 0 -> String.format(Locale.getDefault(), "%.2f km", workout.distance_m / 1000f)
            else -> "${workout.step_count} steps"
        }
        holder.tvKcal.text = "${workout.calories.toInt()} kcal est."
        holder.itemView.contentDescription = "$displayType, ${holder.tvDate.text}, ${holder.tvDuration.text}, ${holder.tvMetric.text}, ${holder.tvKcal.text}. Tap for details"
        holder.itemView.setOnClickListener {
            context.startActivity(Intent(context, WorkoutSummaryActivity::class.java).apply {
                putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, displayType)
                putExtra("summary_duration_ms", workout.duration_sec.toLong() * 1000L)
                putExtra("summary_distance_m", workout.distance_m)
                putExtra("summary_hr", workout.avg_hr)
                putExtra("summary_calories", workout.calories)
                putExtra("summary_steps", workout.step_count)
                val pts = runCatching {
                    com.google.gson.Gson().fromJson<List<com.healthsync.watch.data.LatLngPoint>>(workout.route_json,
                        object : com.google.gson.reflect.TypeToken<List<com.healthsync.watch.data.LatLngPoint>>() {}.type)?.size ?: 0
                }.getOrDefault(0)
                putExtra("summary_route_count", pts)
                putExtra("summary_start_ms", workout.start_time)
            })
        }
    }
    override fun getItemId(position: Int) = items[position].start_time
    override fun getItemCount() = items.size
}

/** Gentle bezel scaling using the standard Android list, including on non-Wear watches. */
private fun scaleHistoryItems(parent: RecyclerView) {
    if (parent.height <= 0) return
    for (index in 0 until parent.childCount) {
        val child = parent.getChildAt(index)
        val relative = (child.y + child.height / 2f) / parent.height
        val scale = 1f - minOf(kotlin.math.abs(0.5f - relative), 0.5f) * 0.18f
        child.scaleX = scale
        child.scaleY = scale
        child.alpha = scale
    }
}
