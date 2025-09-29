package com.example.travelpractice.ui.checklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.models.PackingCategory
import com.example.travelpractice.models.PackingItem
import com.google.android.material.button.MaterialButton

class CategoryAdapter(
    private val categories: MutableList<PackingCategory>,
    private val itemsByCategory: MutableMap<String, MutableList<PackingItem>>,
    private val onAddItem: (PackingCategory) -> Unit,
    private val onDeleteCategory: (PackingCategory) -> Unit,
    private val onToggleItem: (PackingItem, Boolean) -> Unit,
    private val onDeleteItem: (PackingItem) -> Unit,
    private val onToggleExpand: (PackingCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    // (Optional) shared pool to make nested RVs smoother
    private val sharedPool = RecyclerView.RecycledViewPool()

    inner class VH(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
    ) {
        val title: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val tvProgress: TextView = itemView.findViewById(R.id.tvCategoryProgress) // <- add this id in XML
        val btnAddItem: MaterialButton = itemView.findViewById(R.id.btnAddItem)
        val btnDeleteCategory: ImageButton = itemView.findViewById(R.id.btnDeleteCategory)
        val btnExpand: ImageButton = itemView.findViewById(R.id.btnExpand)
        val rvItems: RecyclerView = itemView.findViewById(R.id.recyclerItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(parent).also { vh ->
        vh.rvItems.apply {
            layoutManager = LinearLayoutManager(parent.context)
            setRecycledViewPool(sharedPool)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = categories[position]
        holder.title.text = cat.title

        // Items for this category
        val items = (itemsByCategory[cat.id] ?: mutableListOf()).toMutableList()

        // Progress: checked/total
        val total = items.size
        val checked = items.count { it.checked }
        holder.tvProgress.text = "$checked/$total"

        // Child adapter
        val childAdapter = ItemAdapter(items, onToggleItem, onDeleteItem)
        holder.rvItems.adapter = childAdapter
        holder.rvItems.visibility = if (cat.expanded) View.VISIBLE else View.GONE

        // Optional: change expand icon if you have collapse icon
        // holder.btnExpand.setImageResource(if (cat.expanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24)

        // Clicks
        holder.btnExpand.setOnClickListener { onToggleExpand(cat) }
        holder.btnAddItem.setOnClickListener { onAddItem(cat) }
        holder.btnDeleteCategory.setOnClickListener { onDeleteCategory(cat) }
    }

    override fun getItemCount(): Int = categories.size

    fun replace(newCats: List<PackingCategory>, newMap: Map<String, List<PackingItem>>) {
        categories.clear()
        categories.addAll(newCats)
        itemsByCategory.clear()
        newMap.forEach { (k, v) -> itemsByCategory[k] = v.toMutableList() }
        notifyDataSetChanged()
    }
}



