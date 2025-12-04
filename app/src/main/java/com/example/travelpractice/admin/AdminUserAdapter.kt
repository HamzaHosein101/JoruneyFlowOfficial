package com.example.travelpractice.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.data.UserProfile
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class AdminUserAdapter(
    private val onResetPassword: (UserProfile) -> Unit
) : ListAdapter<UserProfile, AdminUserAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<UserProfile>() {
        override fun areItemsTheSame(oldItem: UserProfile, newItem: UserProfile) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: UserProfile, newItem: UserProfile) = oldItem == newItem
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtUserName: TextView = itemView.findViewById(R.id.txtUserName)
        val txtUserEmail: TextView = itemView.findViewById(R.id.txtUserEmail)
        val txtEmailVerified: TextView = itemView.findViewById(R.id.txtEmailVerified)
        val txtUserProviders: TextView = itemView.findViewById(R.id.txtUserProviders)
        val txtCreatedAt: TextView = itemView.findViewById(R.id.txtCreatedAt)
        val txtLastLoginAt: TextView = itemView.findViewById(R.id.txtLastLoginAt)
        val btnResetPassword: MaterialButton = itemView.findViewById(R.id.btnResetPassword)
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        val context = holder.itemView.context

        val name = user.displayName?.takeIf { it.isNotBlank() } ?: holder.itemView.context.getString(R.string.admin_user_unknown_value)
        holder.txtUserName.text = name

        val fallbackEmail = when {
            user.email.isNotBlank() -> user.email
            user.uid.contains("@") -> user.uid
            else -> holder.itemView.context.getString(R.string.admin_user_no_email)
        }
        holder.txtUserEmail.text = fallbackEmail

        val verifiedText = if (user.emailVerified) {
            context.getString(R.string.admin_user_email_verified)
        } else {
            context.getString(R.string.admin_user_email_unverified)
        }
        holder.txtEmailVerified.text = verifiedText
        val verifiedColorRes = if (user.emailVerified) {
            R.color.approve_green
        } else {
            R.color.delete_red
        }
        holder.txtEmailVerified.setTextColor(ContextCompat.getColor(context, verifiedColorRes))

        val providerDisplay = if (user.providers.isNotEmpty()) {
            user.providers.joinToString()
        } else {
            context.getString(R.string.admin_user_providers_unknown)
        }
        val providerText = context.getString(R.string.admin_user_providers, providerDisplay)
        holder.txtUserProviders.text = providerText

        val createdAtText = user.createdAt?.toDate()?.let { date ->
            context.getString(R.string.admin_user_created_at, dateFormat.format(date))
        } ?: context.getString(R.string.admin_user_created_at, context.getString(R.string.admin_user_unknown_value))
        holder.txtCreatedAt.text = createdAtText

        val lastLoginText = user.lastLoginAt?.toDate()?.let { date ->
            context.getString(R.string.admin_user_last_login, dateFormat.format(date))
        } ?: context.getString(R.string.admin_user_last_login, context.getString(R.string.admin_user_unknown_value))
        holder.txtLastLoginAt.text = lastLoginText

        holder.btnResetPassword.setOnClickListener { onResetPassword(user) }
    }
}

