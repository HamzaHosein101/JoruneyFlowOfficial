package com.example.travelpractice.reviews

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.data.Review
import com.example.travelpractice.databinding.ItemReviewBinding
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class ReviewAdapter(
    private val currentUserId: String?,
    private val onReport: (Review) -> Unit,
    private val onDelete: (Review) -> Unit
) : ListAdapter<Review, ReviewAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }

    inner class VH(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding


        b.tvLocation.text = item.locationName
        b.tvComment.text = item.comment


        b.tvUserChip.text = item.username


        b.ratingBarStatic.rating = item.rating.toFloat()


        val date = item.tripDate.toDate()
        val month = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(date)
        val day = java.text.SimpleDateFormat("dd", java.util.Locale.getDefault()).format(date)
        b.tvMonth.text = month
        b.tvDay.text = day


        val isOwner = item.userId == currentUserId
        b.btnDelete.visibility = if (isOwner) View.VISIBLE else View.GONE
        b.btnReport.visibility = if (isOwner) View.GONE else View.VISIBLE

        b.btnReport.setOnClickListener { onReport(item) }
        b.btnDelete.setOnClickListener { onDelete(item) }
    }


    private fun formatDate(ts: Timestamp): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(ts.toDate())
    }
}
