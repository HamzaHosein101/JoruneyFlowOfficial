package com.example.travelpractice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpractice.data.Review
import com.example.travelpractice.data.ReviewsRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReviewsViewModel(private val repo: ReviewsRepository) : ViewModel() {

    data class UiState(
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val locationFilter: String? = null,
        val reviews: List<Review> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun observe(locationFilter: String? = null) {
        _state.update { it.copy(locationFilter = locationFilter) }
        viewModelScope.launch {
            repo.streamReviews(locationFilter).collect { list ->
                _state.update { it.copy(reviews = list, error = null) }
            }
        }
    }

    fun searchByLocation(query: String?) {
        _state.update { it.copy(locationFilter = query) }
        viewModelScope.launch {
            repo.streamReviewsByLocationPrefix(query).collect { list ->
                _state.update { it.copy(reviews = list, error = null) }
            }
        }
    }


    fun submit(location: String, date: Timestamp, rating: Int, comment: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val res = repo.addReview(location, date, rating, comment)
            _state.update { it.copy(isSubmitting = false, error = res.exceptionOrNull()?.message) }
        }
    }

    fun delete(reviewId: String) {
        viewModelScope.launch { repo.deleteOwnReview(reviewId) }
    }

    fun report(reviewId: String) {
        viewModelScope.launch { repo.reportReview(reviewId) }
    }

    fun reportWithDetails(reviewId: String, reason: String, description: String?) {
        viewModelScope.launch { 
            val result = repo.reportReviewWithDetails(reviewId, reason, description)
            result.onFailure { error ->
                android.util.Log.e("ReviewsViewModel", "Failed to report review $reviewId", error)
            }.onSuccess {
                android.util.Log.d("ReviewsViewModel", "Successfully reported review $reviewId")
            }
        }
    }
    
    suspend fun reportWithDetailsAsync(reviewId: String, reason: String, description: String?): Result<Unit> {
        return repo.reportReviewWithDetails(reviewId, reason, description)
    }
}
