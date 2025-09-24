package com.example.travelpractice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.model.ItineraryItem

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
        private val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        private val txtDuration: TextView = itemView.findViewById(R.id.txtDuration)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        private val txtLocation: TextView = itemView.findViewById(R.id.txtLocation)
        private val txtDescription: TextView = itemView.findViewById(R.id.txtDescription)
        private val txtCost: TextView = itemView.findViewById(R.id.txtCost)
        private val itemIcon: ImageView = itemView.findViewById(R.id.itemIcon)
        private val checkBoxCompleted: CheckBox = itemView.findViewById(R.id.checkBoxCompleted)
        private val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)

        fun bind(item: ItineraryItem) {
            txtTime.text = item.startTime
            txtDuration.text = "${item.duration}m"
            txtTitle.text = item.title
            txtLocation.text = item.location.ifEmpty { "No location" }
            txtDescription.text = item.description.ifEmpty { "No description" }
            txtCost.text = if (item.cost > 0) "$${String.format("%.2f", item.cost)}" else ""
            checkBoxCompleted.isChecked = item.isCompleted

            // Hide description if empty
            txtDescription.visibility = if (item.description.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Hide cost if 0
            txtCost.visibility = if (item.cost > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // Set icon and background based on item type
            when {
                item.title.contains("flight", ignoreCase = true) -> {
                    itemIcon.setImageResource(R.drawable.ic_star_24)
                    itemIcon.setBackgroundResource(R.drawable.circle_background_light_blue)
                }
                item.title.contains("hotel", ignoreCase = true) || item.title.contains("check-in", ignoreCase = true) -> {
                    itemIcon.setImageResource(R.drawable.ic_home_24)
                    itemIcon.setBackgroundResource(R.drawable.circle_background_light_green)
                }
                item.title.contains("restaurant", ignoreCase = true) || item.title.contains("dining", ignoreCase = true) || item.title.contains("lunch", ignoreCase = true) -> {
                    itemIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    itemIcon.setBackgroundResource(R.drawable.circle_background_blue)
                }
                item.title.contains("tour", ignoreCase = true) || item.title.contains("visit", ignoreCase = true) || item.title.contains("sightseeing", ignoreCase = true) -> {
                    itemIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    itemIcon.setBackgroundResource(R.drawable.circle_background_light_blue)
                }
                else -> {
                    itemIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    itemIcon.setBackgroundResource(R.drawable.circle_background_blue)
                }
            }

            // Set up checkbox click
            checkBoxCompleted.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != item.isCompleted) {
                    onToggleComplete(item.copy(isCompleted = isChecked))
                }
            }

            // Set up edit button click
            btnEdit.setOnClickListener {
                onEdit(item)
            }

            // Set up item click to edit
            itemView.setOnClickListener {
                onEdit(item)
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


