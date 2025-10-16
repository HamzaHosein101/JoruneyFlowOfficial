package com.example.travelpractice.ui.home

import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.model.Trip
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.Currency



class TripAdapter(
    private val onDelete: (Trip) -> Unit,
    private val onEdit: (Trip) -> Unit,
    private val onOpen: (Trip) -> Unit
) : RecyclerView.Adapter<TripAdapter.VH>() {

    private val data = mutableListOf<Trip>()
    private val dateFmt: DateFormat =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

    // Use USD so it matches ExpenseTracker/header
    private val usdFmt: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            currency = Currency.getInstance("USD")
        }

    fun submitList(newData: List<Trip>) {
        data.clear(); data.addAll(newData); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
        return VH(v, dateFmt, usdFmt)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position], onDelete, onEdit, onOpen)
    }

    override fun getItemCount() = data.size

    class VH(
        itemView: View,
        private val dateFmt: DateFormat,
        private val usdFmt: NumberFormat
    ) : RecyclerView.ViewHolder(itemView) {

        private val title = itemView.findViewById<TextView>(R.id.txtTitle)
        private val destination = itemView.findViewById<TextView>(R.id.txtDestination)
        private val dates = itemView.findViewById<TextView>(R.id.txtDates)
        private val budgetPill = itemView.findViewById<TextView>(R.id.txtBudgetPill)
        private val more = itemView.findViewById<ImageButton>(R.id.btnOverflow)

        fun bind(item: Trip, onDelete: (Trip) -> Unit, onEdit: (Trip) -> Unit, onOpen: (Trip) -> Unit) {
            title.text = item.title.ifBlank { "Untitled trip" }
            destination.text = item.destination.ifBlank { "Destination not set" }
            dates.text = "${dateFmt.format(Date(item.startDate))} — ${dateFmt.format(Date(item.endDate))}"

            if (item.budget > 0.0) {
                budgetPill.visibility = View.VISIBLE

                // Prefer the saved 'remaining' from Firestore; otherwise fall back.
                val rem = when {
                    item.remaining > 0.0 && item.remaining <= item.budget -> item.remaining
                    item.spent > 0.0 -> (item.budget - item.spent).coerceAtLeast(0.0)
                    else -> item.budget
                }
                budgetPill.text = "Remaining ${usdFmt.format(rem)}"
            } else {
                budgetPill.visibility = View.GONE
            }

            more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    MenuInflater(anchor.context).inflate(R.menu.menu_trip_item, menu)
                    setOnMenuItemClickListener { mi ->
                        when (mi.itemId) {
                            R.id.action_edit   -> { onEdit(item); true }
                            R.id.action_delete -> { onDelete(item); true }
                            else -> false
                        }
                    }
                }.show()
            }

            itemView.setOnClickListener { onOpen(item) }
        }
    }
}




