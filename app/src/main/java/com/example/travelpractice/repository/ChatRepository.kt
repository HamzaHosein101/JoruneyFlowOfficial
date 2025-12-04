package com.example.travelpractice.repository

import com.example.travelpractice.model.Message
import com.example.travelpractice.model.MessageSender
import com.example.travelpractice.model.MessageType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class ChatRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Get current user's chat history collection reference
     */
    private fun getUserChatCollection() = firestore
        .collection("chatHistory")
        .document(auth.currentUser?.uid ?: "anonymous")
        .collection("messages")

    /**
     * Save a message to Firestore
     */
    suspend fun saveMessage(message: Message): Result<Unit> {
        return try {
            val data = hashMapOf(
                "id" to message.id,
                "text" to message.text,
                "sender" to message.sender.name,
                "timestamp" to message.timestamp,
                "type" to message.type.name
            )

            getUserChatCollection()
                .document(message.id.toString())
                .set(data)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load all messages for current user
     */
    suspend fun loadMessages(): Result<List<Message>> {
        return try {
            val snapshot = getUserChatCollection()
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()

            val messages = snapshot.documents.mapNotNull { doc ->
                try {
                    Message(
                        id = doc.getLong("id") ?: 0L,
                        text = doc.getString("text") ?: "",
                        sender = MessageSender.valueOf(doc.getString("sender") ?: "BOT"),
                        timestamp = doc.getDate("timestamp") ?: Date(),
                        type = MessageType.valueOf(doc.getString("type") ?: "GENERAL")
                    )
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all chat history for current user
     */
    suspend fun clearHistory(): Result<Unit> {
        return try {
            val snapshot = getUserChatCollection().get().await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a specific message
     */
    suspend fun deleteMessage(messageId: Long): Result<Unit> {
        return try {
            getUserChatCollection()
                .document(messageId.toString())
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}