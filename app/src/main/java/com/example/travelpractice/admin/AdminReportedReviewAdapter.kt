package com.example.travelpractice.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.data.Review
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Locale

// Data class to hold review with report information
data class ReviewWithReports(
    val review: Review,
    val reportReason: String? = null,
    val reportDescription: String? = null
)

class AdminReportedReviewAdapter(
    private val onKeep: (Review) -> Unit,
    private val onDelete: (Review) -> Unit
) : ListAdapter<ReviewWithReports, AdminReportedReviewAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<ReviewWithReports>() {
        override fun areItemsTheSame(oldItem: ReviewWithReports, newItem: ReviewWithReports) = oldItem.review.id == newItem.review.id
        override fun areContentsTheSame(oldItem: ReviewWithReports, newItem: ReviewWithReports) = oldItem == newItem
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtUserName: TextView = itemView.findViewById(R.id.txtUserName)
        val txtPlaceAndDate: TextView = itemView.findViewById(R.id.txtPlaceAndDate)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        val chipReason: Chip = itemView.findViewById(R.id.chipReason)
        val txtReview: TextView = itemView.findViewById(R.id.txtReview)
        val btnKeep: MaterialButton = itemView.findViewById(R.id.btnKeep)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        val reportDetailsContainer: LinearLayout = itemView.findViewById(R.id.reportDetailsContainer)
        val txtReportReason: TextView = itemView.findViewById(R.id.txtReportReason)
        val txtReportDescription: TextView = itemView.findViewById(R.id.txtReportDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reported_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reviewWithReports = getItem(position)
        val review = reviewWithReports.review
        
        holder.txtUserName.text = review.username
        holder.txtReview.text = review.comment
        
        // Format date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        holder.txtPlaceAndDate.text = "${review.locationName} • ${dateFormat.format(review.tripDate.toDate())}"
        
        holder.ratingBar.rating = review.rating.toFloat()
        
        // Show report count if > 0
        val reportText = if (review.reportCount > 0) {
            "Reported (${review.reportCount})"
        } else {
            "Reported"
        }
        holder.chipReason.text = reportText
        
        // Show report details if available
        if (reviewWithReports.reportReason != null) {
            holder.reportDetailsContainer.visibility = View.VISIBLE
            holder.txtReportReason.text = reviewWithReports.reportReason
            
            // Show description if it exists and is not empty
            val descriptionLabel = holder.itemView.findViewById<TextView>(R.id.txtReportDescriptionLabel)
            if (!reviewWithReports.reportDescription.isNullOrBlank()) {
                descriptionLabel?.visibility = View.VISIBLE
                holder.txtReportDescription.visibility = View.VISIBLE
                holder.txtReportDescription.text = reviewWithReports.reportDescription
            } else {
                descriptionLabel?.visibility = View.GONE
                holder.txtReportDescription.visibility = View.GONE
            }
        } else {
            holder.reportDetailsContainer.visibility = View.GONE
        }
        
        holder.btnKeep.setOnClickListener { onKeep(review) }
        holder.btnDelete.setOnClickListener { onDelete(review) }
    }
}


