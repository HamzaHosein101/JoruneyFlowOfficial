package com.example.travelpractice.ui.home

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.model.Trip
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import com.google.android.material.dialog.MaterialAlertDialogBuilder


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
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
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

        fun bind(
            item: Trip,
            onDelete: (Trip) -> Unit,
            onEdit: (Trip) -> Unit,
            onOpen: (Trip) -> Unit
        ) {
            title.text = item.title.ifBlank { "Untitled trip" }
            destination.text = item.destination.ifBlank { "Destination not set" }
            dates.text = "${dateFmt.format(Date(item.startDate))} — ${dateFmt.format(Date(item.endDate))}"

            if (item.budget > 0.0) {
                budgetPill.visibility = View.VISIBLE

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
                val themed = ContextThemeWrapper(anchor.context, R.style.JourneyFlow_PopupMenu)

                val popup = PopupMenu(themed, anchor, Gravity.END)
                popup.menuInflater.inflate(R.menu.menu_trip_item, popup.menu)

                // Make Delete red
                popup.menu.findItem(R.id.action_delete)?.let { deleteItem ->
                    val spannable = android.text.SpannableString(deleteItem.title)
                    spannable.setSpan(
                        android.text.style.ForegroundColorSpan(0xFFD32F2F.toInt()),
                        0,
                        spannable.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    deleteItem.title = spannable
                }

                popup.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {

                        R.id.action_edit -> {
                            onEdit(item)
                            true
                        }

                        R.id.action_delete -> {

                            val dialog = MaterialAlertDialogBuilder(
                                anchor.context,
                                R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor

                            )
                                .setTitle("Delete trip?")
                                .setMessage("Are you sure you want to delete this trip?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ ->
                                    onDelete(item)
                                }
                                .show()

                            // Make Delete button red
                            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                                ?.setTextColor(0xFFD32F2F.toInt())

                            true
                        }


                        else -> false
                    }
                }


                popup.show()
            }

            itemView.setOnClickListener { onOpen(item) }
        }
    }
}
