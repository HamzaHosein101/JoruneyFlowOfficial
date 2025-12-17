package com.example.travelpractice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.model.ItineraryItem
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class ItineraryAdapter(
    private val onEdit: (ItineraryItem) -> Unit,
    private val onDelete: (ItineraryItem) -> Unit,
    private val onToggleComplete: (ItineraryItem) -> Unit
) : ListAdapter<ItineraryItem, ItineraryAdapter.ItineraryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItineraryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_itinerary, parent, false)
        return ItineraryViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItineraryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ItineraryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        private val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        private val txtDuration: TextView = itemView.findViewById(R.id.txtDuration)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        private val txtLocation: TextView = itemView.findViewById(R.id.txtLocation)
        private val txtDescription: TextView = itemView.findViewById(R.id.txtDescription)
        private val txtCost: TextView = itemView.findViewById(R.id.txtCost)
        private val checkBoxCompleted: CheckBox = itemView.findViewById(R.id.checkBoxCompleted)
        private val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        private val typeText: TextView = itemView.findViewById(R.id.typeText)
        private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

        fun bind(item: ItineraryItem) {
            txtDate.text = if (item.date > 0) {
                dateFormat.format(Date(item.date))
            } else {
                "No date"
            }

            txtTime.text = item.startTime
            txtDuration.text = item.endTime
            txtTitle.text = item.title
            txtLocation.text = item.location.ifEmpty { "No location" }
            txtDescription.text = item.description.ifEmpty { "No description" }
            txtCost.text = if (item.cost > 0) "$${String.format("%.2f", item.cost)}" else ""
            checkBoxCompleted.isChecked = item.isCompleted

            typeText.text = item.type

            applyCompletedStyle(item.isCompleted)

            txtDescription.visibility = if (item.description.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            txtCost.visibility = if (item.cost > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

            checkBoxCompleted.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != item.isCompleted) {
                    onToggleComplete(item.copy(isCompleted = isChecked))
                }
            }

            btnEdit.setOnClickListener {
                onEdit(item)
            }

            btnDelete.setOnClickListener {
                onDelete(item)
            }

            itemView.setOnClickListener {
                onEdit(item)
            }
        }

        private fun applyCompletedStyle(isCompleted: Boolean) {
            if (isCompleted) {
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.completed_green_tint)
                )
                txtTitle.alpha = 0.7f
                txtLocation.alpha = 0.7f
                txtDescription.alpha = 0.7f
            } else {
                // Reset to default white background
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
                // Reset text transparency
                txtTitle.alpha = 1.0f
                txtLocation.alpha = 1.0f
                txtDescription.alpha = 1.0f
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ItineraryItem>() {
        override fun areItemsTheSame(oldItem: ItineraryItem, newItem: ItineraryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ItineraryItem, newItem: ItineraryItem): Boolean {
            return oldItem == newItem
        }
    }
}