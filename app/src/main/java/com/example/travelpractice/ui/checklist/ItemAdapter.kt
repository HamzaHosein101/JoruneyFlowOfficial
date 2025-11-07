package com.example.travelpractice.ui.checklist

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import com.google.android.material.checkbox.MaterialCheckBox
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.model.PackingItem

class ItemAdapter(
    private val items: MutableList<PackingItem>,
    private val onToggle: (PackingItem, Boolean) -> Unit,
    private val onDelete: (PackingItem) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    inner class VH(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_check_item, parent, false)
    ) {
        val check: MaterialCheckBox = itemView.findViewById(R.id.checkItem)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(parent)

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.check.text = item.name
        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = item.checked

        holder.check.setOnCheckedChangeListener { _, isChecked ->
            if (item.checked != isChecked) onToggle(item, isChecked)
        }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun replace(newList: List<PackingItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}


