package com.example.travelpractice.reviews

import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.travelpractice.data.Review
import com.example.travelpractice.databinding.ActivityTownReviewsBinding
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

class TownReviewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTownReviewsBinding

    private val vm: ReviewsViewModel by viewModels {
        ReviewsViewModelFactory(ReviewsProvider.repo())
    }

    private lateinit var adapter: ReviewAdapter
    private val auth by lazy { FirebaseAuth.getInstance() }

    companion object {
        const val EXTRA_TOWN_NAME = "extra_town_name"
        const val EXTRA_TOWN_SUBTITLE = "extra_town_subtitle" // optional (country / region)
    }

    private var lockedLocation: String? = null  // this is the town name we filter by

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTownReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get town name from Intent
        val townName = intent.getStringExtra(EXTRA_TOWN_NAME) ?: "Unknown location"
        val townSubtitle = intent.getStringExtra(EXTRA_TOWN_SUBTITLE)

        lockedLocation = townName

        // Toolbar
        setSupportActionBar(binding.topAppBarTown)
        supportActionBar?.title = "Reviews"
        binding.topAppBarTown.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Header text
        binding.tvTownHeader.text = townName
        binding.tvTownSubHeader.text = townSubtitle ?: "All reviews for this location"

        // Recycler + adapter
        adapter = ReviewAdapter(
            currentUserId = auth.currentUser?.uid,
            onReport = { review -> showReportDialog(review) },
            onDelete = { review -> vm.delete(review.id) }
        )
        binding.townReviewsRecycler.adapter = adapter

        // spacing between cards
        val space = (6 * resources.displayMetrics.density).toInt()
        binding.townReviewsRecycler.addItemDecoration(SpacesItemDecoration(space))

        // Observe only this town’s reviews
        vm.observe(locationFilter = null)

        // Collect state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    val target = lockedLocation

                    val filtered = if (target.isNullOrBlank()) {
                        state.reviews
                    } else {
                        state.reviews.filter { review ->
                            review.locationName.equals(target, ignoreCase = true)
                        }
                    }

                    adapter.submitList(filtered)
                    binding.townReviewsRecycler.isVisible = filtered.isNotEmpty()

                    state.error?.let { msg ->
                        Snackbar.make(binding.townReviewsRoot, msg, Snackbar.LENGTH_LONG).show()
                    }
                }

            }
        }

        // Add review for this town
        binding.fabAddTownReview.setOnClickListener {
            showAddDialog(defaultLocation = lockedLocation)
        }
    }

    // --- Add Review dialog (same logic as your existing screen) -------------------

    private fun showAddDialog(defaultLocation: String?) {
        val dlgBinding = DialogAddReviewBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dlgBinding.root)
            .create()

        // Prefill & (optionally) lock location
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
            Snackbar.make(binding.townReviewsRoot
                , "Posting review…", Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatDate(ts: Timestamp): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(ts.toDate())
    }

    // --- Report Review dialog (copied from your ReviewsActivity) --------------------

    private fun showReportDialog(review: Review) {
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

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            reasons
        )
        dlgBinding.spinnerReason.setAdapter(adapter)
        dlgBinding.spinnerReason.setText(reasons[0], false)
        dlgBinding.spinnerReason.threshold = 1

        dlgBinding.spinnerReason.setOnClickListener {
            dlgBinding.spinnerReason.showDropDown()
        }

        dlgBinding.spinnerReason.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) dlgBinding.spinnerReason.showDropDown()
        }

        dlgBinding.spinnerReason.setOnItemClickListener { _, _, position, _ ->
            dlgBinding.spinnerReason.setText(adapter.getItem(position), false)
            dlgBinding.spinnerReason.dismissDropDown()
        }

        // Description word-count check
        dlgBinding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString() ?: ""
                val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val wordCount = if (text.isBlank()) 0 else words.size

                if (wordCount > 500) {
                    dlgBinding.textInputDescription.error =
                        "Maximum 500 words allowed ($wordCount words)"
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
                Snackbar.make(binding.townReviewsRoot
                    , "Please select a reason", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (words.size > 500) {
                Snackbar.make(
                    binding.townReviewsRoot
                    ,
                    "Description must be 500 words or less",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Submit report (this still goes through your ViewModel → admin logic)
            lifecycleScope.launch {
                val result = vm.reportWithDetailsAsync(
                    review.id,
                    reason,
                    description.takeIf { it.isNotBlank() }
                )
                if (result.isSuccess) {
                    Snackbar.make(
                        binding.townReviewsRoot
                        ,
                        "Report submitted successfully",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    android.util.Log.d(
                        "TownReviewsActivity",
                        "Report submitted for review ${review.id}"
                    )
                } else {
                    Snackbar.make(
                        binding.townReviewsRoot
                        ,
                        "Failed to submit report: ${result.exceptionOrNull()?.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                    android.util.Log.e(
                        "TownReviewsActivity",
                        "Failed to submit report",
                        result.exceptionOrNull()
                    )
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}
