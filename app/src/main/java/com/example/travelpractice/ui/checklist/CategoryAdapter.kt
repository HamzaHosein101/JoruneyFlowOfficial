package com.example.travelpractice.ui.checklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.model.PackingCategory
import com.example.travelpractice.model.PackingItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(
    private val categories: MutableList<PackingCategory>,
    private val itemsByCategory: MutableMap<String, MutableList<PackingItem>>,
    private val onAddItem: (PackingCategory) -> Unit,
    private val onDeleteCategory: (PackingCategory) -> Unit,
    private val onToggleItem: (PackingItem, Boolean) -> Unit,
    private val onDeleteItem: (PackingItem) -> Unit,
    private val onToggleExpand: (PackingCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    var showUncheckedOnly: Boolean = false

    private val sharedPool = RecyclerView.RecycledViewPool()

    inner class VH(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
    ) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val title: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val tvProgress: TextView = itemView.findViewById(R.id.tvCategoryProgress)
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

        val allItems = (itemsByCategory[cat.id] ?: mutableListOf()).toMutableList()

        val visibleItems = if (showUncheckedOnly) allItems.filter { !it.checked } else allItems

        val total = allItems.size
        val checked = allItems.count { it.checked }
        holder.tvProgress.text = "$checked/$total"

        val isAllCompleted = total > 0 && checked == total

        android.util.Log.d("CategoryAdapter", "Category: ${cat.title}, Total: $total, Checked: $checked, IsCompleted: $isAllCompleted")
        allItems.forEachIndexed { index, item ->
            android.util.Log.d("CategoryAdapter", "  Item $index: ${item.name}, Checked: ${item.checked}")
        }

        applyCompletedStyle(holder, isAllCompleted)

        val childAdapter = ItemAdapter(visibleItems.toMutableList(), onToggleItem, onDeleteItem)
        holder.rvItems.adapter = childAdapter
        holder.rvItems.visibility = if (cat.expanded) View.VISIBLE else View.GONE

        holder.btnExpand.setOnClickListener { onToggleExpand(cat) }
        holder.btnAddItem.setOnClickListener { onAddItem(cat) }
        holder.btnDeleteCategory.setOnClickListener { onDeleteCategory(cat) }
    }

    override fun getItemCount(): Int = categories.size

    private fun applyCompletedStyle(holder: VH, isCompleted: Boolean) {
        android.util.Log.d("CategoryAdapter", "Applying style - isCompleted: $isCompleted")

        if (isCompleted) {
            val greenColor = try {
                ContextCompat.getColor(holder.itemView.context, R.color.completed_green_tint)
            } catch (e: Exception) {
                android.util.Log.w("CategoryAdapter", "completed_green_tint color not found, using fallback")
                android.graphics.Color.parseColor("#CCF8CD")
            }

            holder.cardView.backgroundTintList = android.content.res.ColorStateList.valueOf(greenColor)
            android.util.Log.d("CategoryAdapter", "Card background tint set to green: $greenColor")

            holder.title.alpha = 0.8f
            holder.tvProgress.alpha = 0.8f
        } else {
            val whiteColor = ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            holder.cardView.backgroundTintList = android.content.res.ColorStateList.valueOf(whiteColor)
            android.util.Log.d("CategoryAdapter", "Card background tint reset to white")

            holder.title.alpha = 1.0f
            holder.tvProgress.alpha = 1.0f
        }
    }

    fun replace(newCats: List<PackingCategory>, newMap: Map<String, List<PackingItem>>) {
        categories.clear()
        categories.addAll(newCats)
        itemsByCategory.clear()
        newMap.forEach { (k, v) -> itemsByCategory[k] = v.toMutableList() }
        notifyDataSetChanged()
    }
}