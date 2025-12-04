package com.example.travelpractice.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.handlers.ActionOption
import com.example.travelpractice.model.Message
import com.example.travelpractice.model.MessageSender
import java.text.SimpleDateFormat
import java.util.Locale

class MessagesAdapter(
    private val onActionClick: (ActionOption) -> Unit = {}
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val VIEW_TYPE_USER = 1
    private val VIEW_TYPE_BOT = 2

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).sender) {
            MessageSender.USER -> VIEW_TYPE_USER
            MessageSender.BOT -> VIEW_TYPE_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_user, parent, false)
                UserMessageViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_bot, parent, false)
                BotMessageViewHolder(view, onActionClick)
            }
        } as RecyclerView.ViewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is BotMessageViewHolder -> holder.bind(message)
        }
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(message: Message) {
            messageText.text = message.text
            timestamp.text = timeFormat.format(message.timestamp)
        }
    }

    class BotMessageViewHolder(
        itemView: View,
        private val onActionClick: (ActionOption) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val actionButtonsContainer: LinearLayout? = itemView.findViewById(R.id.actionButtonsContainer)
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(message: Message) {
            messageText.text = message.text

            // ✅ MAKE LINKS CLICKABLE
            messageText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            messageText.autoLinkMask = android.text.util.Linkify.WEB_URLS

            timestamp.text = timeFormat.format(message.timestamp)

            actionButtonsContainer?.let { container ->
                if (message.hasActions()) {
                    container.visibility = View.VISIBLE
                    container.removeAllViews()

                    message.actionOptions?.forEach { option ->
                        val button = createActionButton(option)
                        container.addView(button)
                    }
                } else {
                    container.visibility = View.GONE
                }
            }
        }

        private fun createActionButton(option: ActionOption): Button {
            return Button(itemView.context).apply {
                text = option.label
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(8)
                }

                try {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                } catch (e: Exception) {
                    setBackgroundColor(0xFF4CAF50.toInt())
                }

                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                isAllCaps = false
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))

                setOnClickListener {
                    onActionClick(option)
                }
            }
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * itemView.context.resources.displayMetrics.density).toInt()
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}