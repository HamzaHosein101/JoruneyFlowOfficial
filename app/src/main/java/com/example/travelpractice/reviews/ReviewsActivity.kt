package com.example.travelpractice.reviews

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.travelpractice.R
import com.example.travelpractice.databinding.ActivityReviewsBinding
import com.example.travelpractice.databinding.DialogAddReviewBinding
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
            onReport = { review -> vm.report(review.id) },
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
}
