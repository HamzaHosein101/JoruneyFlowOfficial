package com.example.travelpractice

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelpractice.adapters.MessagesAdapter
import com.example.travelpractice.databinding.ActivityChatBinding
import com.example.travelpractice.viewmodel.ChatViewModel
import com.example.travelpractice.viewmodel.ChatViewModelFactory

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var messagesAdapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val geminiApiKey = "AIzaSyAA8p-9H-qw8P5ZdFAr_DgM95j-kQZq0LA"




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
    }

    private fun setupViewModel(apiKey: String) {
        val factory = ChatViewModelFactory(apiKey)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter()
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
}