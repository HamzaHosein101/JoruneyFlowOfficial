package com.example.travelpractice

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // Strong password validation function
    private fun isPasswordSecure(password: String): Pair<Boolean, String> {
        return when {
            password.length < 8 -> Pair(false, "Password must be at least 8 characters")
            !password.any { it.isUpperCase() } -> Pair(false, "Must contain at least one uppercase letter (A-Z)")
            !password.any { it.isLowerCase() } -> Pair(false, "Must contain at least one lowercase letter (a-z)")
            !password.any { it.isDigit() } -> Pair(false, "Must contain at least one number (0-9)")
            !password.any { !it.isLetterOrDigit() } -> Pair(false, "Must contain at least one special character (!@#$%^&*)")
            password.contains(" ") -> Pair(false, "Password cannot contain spaces")
            else -> Pair(true, "Password is secure")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirm)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        // Password requirement TextViews
        val tvReqLength = findViewById<TextView>(R.id.tvReqLength)
        val tvReqUppercase = findViewById<TextView>(R.id.tvReqUppercase)
        val tvReqLowercase = findViewById<TextView>(R.id.tvReqLowercase)
        val tvReqNumber = findViewById<TextView>(R.id.tvReqNumber)
        val tvReqSpecial = findViewById<TextView>(R.id.tvReqSpecial)
        val tvPasswordRequirements = findViewById<TextView>(R.id.tvPasswordRequirements)

        // Add TextWatcher to validate password in real-time
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()

                // Show requirements when user starts typing
                if (password.isNotEmpty()) {
                    tvPasswordRequirements.visibility = View.VISIBLE
                    tvReqLength.visibility = View.VISIBLE
                    tvReqUppercase.visibility = View.VISIBLE
                    tvReqLowercase.visibility = View.VISIBLE
                    tvReqNumber.visibility = View.VISIBLE
                    tvReqSpecial.visibility = View.VISIBLE

                    // Update each requirement
                    updateRequirement(tvReqLength, password.length >= 8)
                    updateRequirement(tvReqUppercase, password.any { it.isUpperCase() })
                    updateRequirement(tvReqLowercase, password.any { it.isLowerCase() })
                    updateRequirement(tvReqNumber, password.any { it.isDigit() })
                    updateRequirement(tvReqSpecial, password.any { !it.isLetterOrDigit() })
                } else {
                    // Hide requirements when field is empty
                    tvPasswordRequirements.visibility = View.GONE
                    tvReqLength.visibility = View.GONE
                    tvReqUppercase.visibility = View.GONE
                    tvReqLowercase.visibility = View.GONE
                    tvReqNumber.visibility = View.GONE
                    tvReqSpecial.visibility = View.GONE
                }
            }
        })

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            // Validate username
            if (username.isEmpty()) {
                etUsername.error = "Enter a username"
                etUsername.requestFocus()
                return@setOnClickListener
            }

            // Validate email
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email address"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            // Validate password security
            val (isSecure, message) = isPasswordSecure(password)
            if (!isSecure) {
                etPassword.error = message
                etPassword.requestFocus()
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Validate password match
            if (password != confirmPassword) {
                etConfirmPassword.error = "Passwords do not match"
                etConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            // Disable button to prevent double-clicks
            btnRegister.isEnabled = false

            // Create user with Firebase
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    btnRegister.isEnabled = true
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        // Set displayName (username) on the Firebase profile
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                            // Send email verification
                            user?.sendEmailVerification()?.addOnCompleteListener { emailTask ->
                                if (emailTask.isSuccessful) {
                                    Toast.makeText(
                                        this,
                                        "Registration successful! Please check your email to verify your account.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    // Sign out user until they verify email
                                    auth.signOut()
                                    // Go back to login
                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Account created but failed to send verification email: ${emailTask.exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        val errorMessage = when {
                            task.exception?.message?.contains("email address is already in use") == true ->
                                "This email is already registered. Please login instead."
                            task.exception?.message?.contains("network error") == true ->
                                "Network error. Please check your internet connection."
                            else -> task.exception?.localizedMessage ?: "Registration failed"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // Helper function to update requirement text color
    private fun updateRequirement(textView: TextView, isMet: Boolean) {
        val baseText = when(textView.id) {
            R.id.tvReqLength -> "At least 8 characters"
            R.id.tvReqUppercase -> "One uppercase letter (A-Z)"
            R.id.tvReqLowercase -> "One lowercase letter (a-z)"
            R.id.tvReqNumber -> "One number (0-9)"
            R.id.tvReqSpecial -> "One special character"
            else -> textView.text.toString()
        }

        if (isMet) {
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            textView.text = "✓ $baseText"
        } else {
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            textView.text = "✗ $baseText"
        }
    }
}