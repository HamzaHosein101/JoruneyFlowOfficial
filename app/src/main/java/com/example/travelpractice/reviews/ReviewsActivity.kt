package com.example.travelpractice.reviews

import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.travelpractice.R
import com.example.travelpractice.databinding.ActivityReviewsBinding
import com.example.travelpractice.databinding.DialogAddReviewBinding
import com.example.travelpractice.databinding.DialogReportReviewBinding
import com.example.travelpractice.di.ReviewsProvider
import com.example.travelpractice.viewmodel.ReviewsViewModel
import com.example.travelpractice.viewmodel.ReviewsViewModelFactory
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.tasks.await


class ReviewsActivity : AppCompatActivity() {



    private lateinit var binding: ActivityReviewsBinding
    private val vm: ReviewsViewModel by viewModels {
        ReviewsViewModelFactory(ReviewsProvider.repo())
    }

    private lateinit var adapter: ReviewAdapter
    private val auth by lazy { FirebaseAuth.getInstance() }

    companion object {
        const val EXTRA_LOCATION_FILTER = "location_filter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.topAppBar)
        binding.topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Recycler
        adapter = ReviewAdapter(
            currentUserId = auth.currentUser?.uid,
            onReport = { review -> showReportDialog(review) },
            onDelete = { review -> vm.delete(review.id) }
        )
        binding.reviewsRecycler.adapter = adapter

        // Tight item spacing
        val space = (6 * resources.displayMetrics.density).toInt()
        binding.reviewsRecycler.addItemDecoration(SpacesItemDecoration(space))

        // Initial feed (no filter to start; search handles filtering live)
        vm.observe(locationFilter = null)

        // Collect state and render
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    adapter.submitList(state.reviews)
                    binding.reviewsRecycler.isVisible = state.reviews.isNotEmpty()
                    // simple empty-state snackbar (optional)
                    if (state.reviews.isEmpty()) {
                        // You could show a dedicated empty view instead
                        // Snackbar.make(binding.reviewsRoot, "No reviews yet", Snackbar.LENGTH_SHORT).show()
                    }
                    state.error?.let { msg ->
                        Snackbar.make(binding.reviewsRoot, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Add review
        binding.fabAddReview.setOnClickListener { showAddDialog(defaultLocation = null) }
    }

    // -- Search menu ----------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
// Put the menu on the toolbar itself
        binding.topAppBar.inflateMenu(R.menu.menu_reviews)

        val searchItem = binding.topAppBar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
        searchView.queryHint = "Search by location"

// listeners
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                vm.searchByLocation(query)
                searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) vm.searchByLocation(null)
                else if (newText.length >= 2) vm.searchByLocation(newText)
                return true
            }
        })

        searchItem.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                vm.searchByLocation(null)
                return true
            }
        })


        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                vm.searchByLocation(null) // reset to full feed when closing search
                return true
            }
        })



        return true
    }

    // -- Add Review dialog ----------------------------------------------------

    private fun showAddDialog(defaultLocation: String?) {
        val dlgBinding = DialogAddReviewBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dlgBinding.root)
            .create()

        // Prefill & (optionally) lock location if you pass one
        dlgBinding.etLocation.setText(defaultLocation ?: "")
        dlgBinding.etLocation.isEnabled = defaultLocation == null

        // Default rating so it's never 0
        dlgBinding.ratingBar.rating = 5f

        var selectedTs: Timestamp? = null
        dlgBinding.btnPickDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select trip date")
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                selectedTs = Timestamp(Date(millis))
                dlgBinding.tvSelectedDate.text = formatDate(selectedTs!!)
            }
            picker.show(supportFragmentManager, "trip_date_picker")
        }

        dlgBinding.btnPost.setOnClickListener {
            val loc = dlgBinding.etLocation.text?.toString()?.trim().orEmpty()
            val rating = dlgBinding.ratingBar.rating.toInt().coerceIn(1, 5)
            val comment = dlgBinding.etComment.text?.toString()?.trim().orEmpty()
            val date = selectedTs ?: Timestamp.now()

            if (loc.isEmpty()) {
                dlgBinding.etLocation.error = "Location required"
                return@setOnClickListener
            }

            vm.submit(loc, date, rating, comment)
            Snackbar.make(binding.reviewsRoot, "Posting review…", Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatDate(ts: Timestamp): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(ts.toDate())
    }

    // -- Report Review dialog ----------------------------------------------------

    private fun showReportDialog(review: com.example.travelpractice.data.Review) {
        val dlgBinding = DialogReportReviewBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dlgBinding.root)
            .create()

        // Setup reason dropdown
        val reasons = listOf(
            "Spam or misleading",
            "Inappropriate",
            "Hateful or abusive content",
            "Harassment or bullying"
        )
        
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, reasons)
        dlgBinding.spinnerReason.setAdapter(adapter)
        dlgBinding.spinnerReason.setText(reasons[0], false)
        dlgBinding.spinnerReason.threshold = 1 // Show dropdown after 1 character (always show on click)
        
        // Make it show dropdown when clicked or touched
        dlgBinding.spinnerReason.setOnClickListener {
            dlgBinding.spinnerReason.showDropDown()
        }
        
        dlgBinding.spinnerReason.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dlgBinding.spinnerReason.showDropDown()
            }
        }
        
        dlgBinding.spinnerReason.setOnItemClickListener { _, _, position, _ ->
            dlgBinding.spinnerReason.setText(adapter.getItem(position), false)
            dlgBinding.spinnerReason.dismissDropDown()
        }

        // Setup description text watcher for word count validation
        dlgBinding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString() ?: ""
                val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val wordCount = if (text.isBlank()) 0 else words.size
                
                // Show error if over 500 words
                if (wordCount > 500) {
                    dlgBinding.textInputDescription.error = "Maximum 500 words allowed ($wordCount words)"
                } else {
                    dlgBinding.textInputDescription.error = null
                }
            }
        })

        dlgBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dlgBinding.btnSubmit.setOnClickListener {
            val reason = dlgBinding.spinnerReason.text.toString().trim()
            val description = dlgBinding.etDescription.text.toString().trim()
            val words = description.split(Regex("\\s+")).filter { it.isNotBlank() }
            
            if (reason.isEmpty()) {
                Snackbar.make(binding.reviewsRoot, "Please select a reason", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (words.size > 500) {
                Snackbar.make(binding.reviewsRoot, "Description must be 500 words or less", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Submit report
            lifecycleScope.launch {
                val result = vm.reportWithDetailsAsync(review.id, reason, description.takeIf { it.isNotBlank() })
                if (result.isSuccess) {
                    Snackbar.make(binding.reviewsRoot, "Report submitted successfully", Snackbar.LENGTH_SHORT).show()
                    android.util.Log.d("ReviewsActivity", "Report submitted for review ${review.id}")
                } else {
                    Snackbar.make(binding.reviewsRoot, "Failed to submit report: ${result.exceptionOrNull()?.message}", Snackbar.LENGTH_LONG).show()
                    android.util.Log.e("ReviewsActivity", "Failed to submit report", result.exceptionOrNull())
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}
