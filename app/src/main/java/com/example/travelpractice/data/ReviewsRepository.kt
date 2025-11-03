package com.example.travelpractice.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val col get() = db.collection("reviews")

    /** Live stream of reviews (optionally filter by location). */
    fun streamReviews(locationFilter: String? = null): Flow<List<Review>> = callbackFlow {
        var query: Query = col.orderBy("createdAt", Query.Direction.DESCENDING)
        if (!locationFilter.isNullOrBlank()) {
            // If you use this filter, be sure to create the composite index:
            // locationName ASC, createdAt DESC
            query = col.whereEqualTo("locationName", locationFilter)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.e("ReviewsRepo", "Listener error", err)
                trySend(emptyList())
                return@addSnapshotListener
            }

            val items = snap?.documents?.map { d ->
                Review(
                    id = d.id,
                    userId = d.getString("userId") ?: "",
                    username = d.getString("username") ?: "",
                    locationName = d.getString("locationName") ?: "",
                    tripDate = d.get("tripDate")?.let { toTimestamp(it) } ?: Timestamp.now(),
                    rating = (d.getLong("rating") ?: 0L).toInt(),
                    comment = d.getString("comment") ?: "",
                    photoUrl = d.getString("photoUrl"),
                    createdAt = d.get("createdAt")?.let { toTimestamp(it) } ?: Timestamp.now(),
                    updatedAt = d.get("updatedAt")?.let { toTimestamp(it) } ?: Timestamp.now(),
                    likeCount = (d.getLong("likeCount") ?: 0L).toInt(),
                    reportCount = (d.getLong("reportCount") ?: 0L).toInt()
                )
            } ?: emptyList()

            trySend(items)
        }

        awaitClose { reg.remove() }
    }

    fun streamReviewsByLocationPrefix(queryText: String?): Flow<List<Review>> = callbackFlow {
        val q = queryText?.trim()?.lowercase(java.util.Locale.ROOT)
        val query = if (q.isNullOrEmpty()) {
            col.orderBy("createdAt", Query.Direction.DESCENDING)
        } else {
            // Prefix search on folded field: startAt..endAt(\uf8ff)
            col.orderBy("locationNameFold")
                .startAt(q)
                .endAt(q + "\uf8ff")
            // (no secondary order here -> simplest index requirements)
        }

        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.e("ReviewsRepo", "Search listener error", err)
                trySend(emptyList()); return@addSnapshotListener
            }
            val items = snap?.documents?.map { d ->
                Review(
                    id = d.id,
                    userId = d.getString("userId") ?: "",
                    username = d.getString("username") ?: "",
                    locationName = d.getString("locationName") ?: "",
                    tripDate = d.get("tripDate")?.let { toTimestamp(it) } ?: com.google.firebase.Timestamp.now(),
                    rating = (d.getLong("rating") ?: 0L).toInt(),
                    comment = d.getString("comment") ?: "",
                    photoUrl = d.getString("photoUrl"),
                    createdAt = d.get("createdAt")?.let { toTimestamp(it) } ?: com.google.firebase.Timestamp.now(),
                    updatedAt = d.get("updatedAt")?.let { toTimestamp(it) } ?: com.google.firebase.Timestamp.now(),
                    likeCount = (d.getLong("likeCount") ?: 0L).toInt(),
                    reportCount = (d.getLong("reportCount") ?: 0L).toInt()
                )
            } ?: emptyList()
            trySend(items)
        }
        awaitClose { reg.remove() }
    }


    /** Add a new review as the signed-in user. */
    suspend fun addReview(
        locationName: String,
        tripDate: Timestamp,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val data = hashMapOf(
            "userId" to user.uid,
            "username" to (user.displayName ?: user.email ?: "Anonymous"),
            "locationName" to locationName.trim(),
            "locationNameFold" to locationName.trim().lowercase(java.util.Locale.ROOT),
            "tripDate" to tripDate,
            "rating" to rating.coerceIn(1, 5),
            "comment" to comment.trim(),
            "photoUrl" to (user.photoUrl?.toString()),
            "createdAt" to Timestamp.now(),   // write proper Timestamp
            "updatedAt" to Timestamp.now(),   // write proper Timestamp
            "likeCount" to 0,
            "reportCount" to 0
        )
        return try {
            col.add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Allow users to delete only their own reviews. */
    suspend fun deleteOwnReview(reviewId: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val doc = col.document(reviewId).get().await()
            if (doc.getString("userId") == user.uid) {
                col.document(reviewId).delete().await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalAccessException("Not your review"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Simple report mechanism (increments a counter). */
    suspend fun reportReview(reviewId: String): Result<Unit> = try {
        db.runTransaction { tr ->
            val ref = col.document(reviewId)
            val snap = tr.get(ref)
            val cur = (snap.getLong("reportCount") ?: 0L) + 1
            tr.update(ref, mapOf("reportCount" to cur, "updatedAt" to Timestamp.now()))
        }.await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Report review with reason and optional description. */
    suspend fun reportReviewWithDetails(reviewId: String, reason: String, description: String?): Result<Unit> = try {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        
        android.util.Log.d("ReviewsRepo", "Reporting review $reviewId with reason: $reason")
        
        // Increment report count
        val newReportCount = db.runTransaction { tr ->
            val ref = col.document(reviewId)
            val snap = tr.get(ref)
            val currentCount = (snap.getLong("reportCount") ?: 0L)
            val newCount = currentCount + 1
            tr.update(ref, mapOf("reportCount" to newCount, "updatedAt" to Timestamp.now()))
            android.util.Log.d("ReviewsRepo", "Updated reportCount from $currentCount to $newCount for review $reviewId")
            newCount
        }.await()
        
        android.util.Log.d("ReviewsRepo", "Report count updated to $newReportCount for review $reviewId")
        
        // Store report details in a subcollection
        val reportData = hashMapOf(
            "reviewId" to reviewId,
            "userId" to user.uid,
            "username" to (user.displayName ?: user.email ?: "Anonymous"),
            "reason" to reason,
            "description" to (description ?: ""),
            "createdAt" to Timestamp.now()
        )
        
        db.collection("reviews").document(reviewId)
            .collection("reports")
            .add(reportData)
            .await()
        
        android.util.Log.d("ReviewsRepo", "Report details saved successfully for review $reviewId")
        
        Result.success(Unit)
    } catch (e: Exception) {
        android.util.Log.e("ReviewsRepo", "Error reporting review $reviewId", e)
        e.printStackTrace()
        Result.failure(e)
    }
}

/** Defensive conversion of various types to Firestore Timestamp. */
private fun toTimestamp(v: Any): Timestamp = when (v) {
    is Timestamp -> v
    is Date      -> Timestamp(v)
    is Long      -> {
        // Assume epoch millis; if your old data was seconds, switch to (v * 1000)
        Timestamp(Date(v))
    }
    is Double    -> Timestamp(Date(v.toLong()))
    is String    -> {
        // Try ISO-8601 like "2025-10-29T13:31:00Z"
        val iso = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).parse(v)
        }.getOrNull()
        when {
            iso != null -> Timestamp(iso)
            v.toLongOrNull() != null -> Timestamp(Date(v.toLong())) // fallback: epoch millis as string
            else -> Timestamp.now()
        }
    }
    else -> Timestamp.now()
}
