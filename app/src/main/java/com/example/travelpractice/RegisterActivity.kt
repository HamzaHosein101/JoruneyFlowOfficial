package com.example.travelpractice  // keep your package

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.material.button.MaterialButton

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btnRegister = findViewById<MaterialButton>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString()
            val conf  = etConfirm.text.toString()

            if (username.isEmpty()) { etUsername.error = "Enter a username"; return@setOnClickListener }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email"; return@setOnClickListener
            }
            if (pass.length < 6) {
                etPassword.error = "Min 6 characters"; return@setOnClickListener
            }
            if (pass != conf) {
                etConfirm.error = "Passwords don’t match"; return@setOnClickListener
            }

            btnRegister.isEnabled = false
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    btnRegister.isEnabled = true
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        // Set displayName on the Firebase profile
                        val updates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()

                        if (user == null) {
                            Toast.makeText(
                                this,
                                "Registration incomplete. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnCompleteListener
                        }

                        val updateTask = user.updateProfile(updates)
                        val saveTask = saveUserProfile(user, username)

                        Tasks.whenAll(updateTask, saveTask)
                            .addOnSuccessListener {
                                user.sendEmailVerification()
                                    .addOnCompleteListener { verifyTask ->
                                        if (verifyTask.isSuccessful) {
                                            Toast.makeText(
                                                this,
                                                "Account created. Verification email sent. Check your inbox.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            auth.signOut() // log them out until they verify
                                            finish() // back to LoginActivity
                                        } else {
                                            Toast.makeText(
                                                this,
                                                verifyTask.exception?.localizedMessage
                                                    ?: "Failed to send verification email",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("RegisterActivity", "Failed to finalize registration", e)
                                Toast.makeText(
                                    this,
                                    "Unable to finish registration. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage ?: "Registration failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun saveUserProfile(user: FirebaseUser, username: String): Task<Void> {
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(user.uid)
        val data = hashMapOf(
            "uid" to user.uid,
            "email" to user.email.orEmpty(),
            "displayName" to username.ifBlank { user.displayName },
            "photoUrl" to user.photoUrl?.toString(),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "providers" to user.providerData.map { it.providerId }.distinct(),
            "emailVerified" to user.isEmailVerified
        )

        return userDoc.set(data, SetOptions.merge())
    }
}

