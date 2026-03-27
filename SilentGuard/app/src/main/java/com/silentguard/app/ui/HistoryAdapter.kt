package com.silentguard.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.silentguard.app.R
import com.silentguard.app.model.DetectionResult
import com.silentguard.app.model.DistressDecision
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val historyList: List<DetectionResult>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.historyIcon)
        val decisionText: TextView = view.findViewById(R.id.historyDecision)
        val timeText: TextView = view.findViewById(R.id.historyTime)
        val confidenceText: TextView = view.findViewById(R.id.historyConfidence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_record, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val result = historyList[position]
        
        holder.timeText.text = dateFormat.format(Date(result.timestamp))
        holder.confidenceText.text = "${(result.finalConfidence * 100).toInt()}%"

        // Style based on decision
        when (result.decision) {
            DistressDecision.HIGH_CONFIDENCE -> {
                holder.decisionText.text = "EMERGENCY ALERT"
                holder.decisionText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.danger))
                holder.icon.setImageResource(R.drawable.ic_warning)
                holder.icon.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.danger)
                holder.confidenceText.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.danger)
            }
            DistressDecision.MEDIUM_CONFIDENCE -> {
                holder.decisionText.text = "Warning/Suspicious"
                holder.decisionText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.warning))
                holder.icon.setImageResource(R.drawable.ic_warning)
                holder.icon.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.warning)
                holder.confidenceText.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.warning)
            }
            DistressDecision.NO_ALERT -> {
                if (result.suppressionReason != null) {
                    holder.decisionText.text = "Suppressed: ${result.suppressionReason}"
                    holder.decisionText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                    holder.icon.setImageResource(R.drawable.ic_lock)
                    holder.icon.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text_secondary)
                    holder.confidenceText.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.overlay_dark)
                } else {
                    holder.decisionText.text = "Normal Routine"
                    holder.decisionText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success))
                    holder.icon.setImageResource(R.drawable.ic_shield_check)
                    holder.icon.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.success)
                    holder.confidenceText.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.success)
                }
            }
        }
    }

    override fun getItemCount() = historyList.size
}
