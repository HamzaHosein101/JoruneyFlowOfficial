package com.example.travelpractice

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelpractice.adapters.MessagesAdapter
import com.example.travelpractice.databinding.ActivityChatBinding
import com.example.travelpractice.handlers.ActionOption
import com.example.travelpractice.handlers.ChatActionHandler
import com.example.travelpractice.viewmodel.ChatViewModel
import com.example.travelpractice.viewmodel.ChatViewModelFactory

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var chatActionHandler: ChatActionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val geminiApiKey = "AIzaSyDglHD1fXogQvKkdaqcTsTU6HSQQSFqVVQ"

        // Initialize chat action handler
        chatActionHandler = ChatActionHandler(this)

        setupToolbar()
        setupViewModel(geminiApiKey)
        setupRecyclerView()
        setupInputListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Handle menu item clicks for MaterialToolbar
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_clear_history -> {
                    showClearHistoryDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupViewModel(apiKey: String) {
        val factory = ChatViewModelFactory(apiKey, applicationContext)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter { actionOption ->
            handleActionClick(actionOption)
        }
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messagesAdapter
            itemAnimator = null
        }
    }

    private fun setupInputListeners() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun sendMessage() {
        val message = binding.messageInput.text.toString()
        if (message.isNotBlank()) {
            viewModel.sendMessage(message)
            binding.messageInput.text?.clear()
        }
    }

    private fun handleActionClick(actionOption: ActionOption) {
        Log.d("ChatActivity", "Action button clicked: ${actionOption.label}")

        // Execute the action
        chatActionHandler.executeAction(actionOption.actionType)

        // Show feedback
        Toast.makeText(this, "Opening ${actionOption.label}...", Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            messagesAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isTyping.observe(this) { isTyping ->
            binding.typingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
            if (isTyping) {
                binding.messagesRecyclerView.smoothScrollToPosition(
                    messagesAdapter.itemCount
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                showClearHistoryDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Chat History")
            .setMessage("Are you sure you want to delete all chat messages? This cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearChat()
                Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}