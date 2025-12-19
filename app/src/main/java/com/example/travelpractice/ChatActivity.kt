package com.example.travelpractice

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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

        val geminiApiKey = "AIzaSyCA8riiN_fHO2TT9j8zlCf2hpPAjZyZwQI"

        chatActionHandler = ChatActionHandler(this)

        setupToolbar()
        setupViewModel(geminiApiKey)
        setupRecyclerView()
        setupInputListeners()
        setupQuickActions()
        observeViewModel()
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Handle back button
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
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
            itemAnimator = null
        }
    }

    private fun setupInputListeners() {
        // Send button click
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // Handle "Send" action from keyboard
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun setupQuickActions() {
        // Flight Search - Show Dialog
        binding.chipFlights.setOnClickListener {
            showFlightSearchDialog()
        }

        // Hotel Search - Show Dialog
        binding.chipHotels.setOnClickListener {
            showHotelSearchDialog()
        }

    }

    private fun showFlightSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_flight_search, null)
        val originInput = dialogView.findViewById<EditText>(R.id.originInput)
        val destinationInput = dialogView.findViewById<EditText>(R.id.destinationInput)

        val dialog = AlertDialog.Builder(this, R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor)
            .setView(dialogView)
            .setPositiveButton("Search", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(android.graphics.Color.RED)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val origin = originInput.text.toString().trim()
                val destination = destinationInput.text.toString().trim()

                if (origin.isEmpty() || destination.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Please enter both origin and destination",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    fillAndSendMessage("Find flights from $origin to $destination")
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showHotelSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hotel_search, null)
        val cityInput = dialogView.findViewById<EditText>(R.id.cityInput)

        val dialog = AlertDialog.Builder(this,R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor)
            .setView(dialogView)
            .setPositiveButton("Search", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(android.graphics.Color.RED)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val city = cityInput.text.toString().trim()

                if (city.isEmpty()) {
                    Toast.makeText(this, "Please enter a city", Toast.LENGTH_SHORT).show()
                } else {
                    fillAndSendMessage("Find hotels in $city")
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }


    /**
     * Fill message input and send automatically
     */
    private fun fillAndSendMessage(message: String) {
        binding.messageInput.setText(message)
        sendMessage()
    }

    /**
     * Send message from input field
     */
    private fun sendMessage() {
        val message = binding.messageInput.text.toString()
        if (message.isNotBlank()) {
            viewModel.sendMessage(message)
            binding.messageInput.text?.clear()

            // Scroll to bottom after sending
            binding.messagesRecyclerView.post {
                if (messagesAdapter.itemCount > 0) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messagesAdapter.itemCount - 1)
                }
            }
        }
    }

    /**
     * Handle action button clicks from messages
     */
    private fun handleActionClick(actionOption: ActionOption) {
        Log.d("ChatActivity", "Action button clicked: ${actionOption.label}")

        // Execute the action
        chatActionHandler.executeAction(actionOption.actionType)

        // Show feedback
        Toast.makeText(this, "Opening ${actionOption.label}...", Toast.LENGTH_SHORT).show()
    }

    /**
     * Observe ViewModel data
     */
    private fun observeViewModel() {
        // Observe messages
        viewModel.messages.observe(this) { messages ->
            messagesAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        // Observe typing indicator
        viewModel.isTyping.observe(this) { isTyping ->
            binding.typingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
            if (isTyping) {
                binding.messagesRecyclerView.smoothScrollToPosition(
                    messagesAdapter.itemCount
                )
            }
        }
    }

    /**
     * Inflate menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    /**
     * Handle menu item selection
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                showClearHistoryDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Show confirmation dialog for clearing chat history
     */
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

    /**
     * Clean up when activity is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        // Any cleanup if needed
    }
}