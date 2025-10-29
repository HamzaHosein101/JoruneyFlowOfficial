package com.example.travelpractice.di

import com.example.travelpractice.data.ReviewsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object ReviewsProvider {
    fun repo(): ReviewsRepository =
        ReviewsRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
}
