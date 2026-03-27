package com.silentguard.app.ui

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.silentguard.app.R
import com.silentguard.app.model.DetectionResult
import com.silentguard.app.model.DistressDecision

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var filterChipGroup: ChipGroup

    private var fullHistory: List<DetectionResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Back button
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        // Clear button — shake then confirm
        val clearBtn = findViewById<View>(R.id.clearButton)
        clearBtn.setOnClickListener {
            clearBtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
            clearBtn.postDelayed({ confirmClear() }, 180)
        }

        recyclerView = findViewById(R.id.historyRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        filterChipGroup = findViewById(R.id.filterChipGroup)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Staggered entrance animation for list items
        val controller = android.view.animation.AnimationUtils.loadLayoutAnimation(
            this, R.anim.layout_anim_history
        )
        recyclerView.layoutAnimation = controller

        setupFilterChips()
        loadHistory()
    }

    private fun setupFilterChips() {
        filterChipGroup.setOnCheckedStateChangeListener { group, _ ->
            val checkedId = group.checkedChipId
            val filtered = when (checkedId) {
                R.id.chipAlerts -> fullHistory.filter {
                    it.decision == DistressDecision.HIGH_CONFIDENCE
                }
                R.id.chipSuppressed -> fullHistory.filter {
                    it.suppressionReason != null
                }
                else -> fullHistory // "All" chip or nothing
            }
            displayHistory(filtered)
        }
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
        val historyJson = prefs.getString("detection_history", "[]") ?: "[]"
        val typeList = object : TypeToken<List<DetectionResult>>() {}.type
        fullHistory = try {
            Gson().fromJson(historyJson, typeList) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        // Newest first
        fullHistory = fullHistory.reversed()
        displayHistory(fullHistory)
    }

    private fun displayHistory(list: List<DetectionResult>) {
        if (list.isEmpty()) {
            // Crossfade: hide recycler, show empty
            if (recyclerView.visibility == View.VISIBLE) {
                recyclerView.animate().alpha(0f).setDuration(180).withEndAction {
                    recyclerView.visibility = View.GONE
                }.start()
                emptyView.alpha = 0f
                emptyView.visibility = View.VISIBLE
                emptyView.animate().alpha(1f).setDuration(220).start()
            } else {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        } else {
            // Crossfade: hide empty, show recycler
            if (emptyView.visibility == View.VISIBLE) {
                emptyView.animate().alpha(0f).setDuration(150).withEndAction {
                    emptyView.visibility = View.GONE
                }.start()
            }
            recyclerView.alpha = 0f
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = HistoryAdapter(list)
            recyclerView.animate().alpha(1f).setDuration(220).start()
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Delete all detection history? This cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                val prefs = getSharedPreferences("silent_guard_prefs", MODE_PRIVATE)
                prefs.edit().remove("detection_history").apply()
                fullHistory = emptyList()
                displayHistory(emptyList())
                // Reset chip to All
                filterChipGroup.check(R.id.chipAll)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
