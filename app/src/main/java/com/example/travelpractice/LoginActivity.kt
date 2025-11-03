package com.example.travelpractice

import android.util.Log
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.travelpractice.admin.AdminLoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().currentUser?.let { user ->
            if (user.isEmailVerified) {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                // optional: show a gentle reminder
                Toast.makeText(this, "Please verify your email to continue.", Toast.LENGTH_SHORT).show()
                FirebaseAuth.getInstance().signOut()
            }
        }
    }


    // Register for Activity Result (Google sign-in)
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: run {
                Toast.makeText(this, "No ID token from Google", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("GFirebase", "signInWithCredential failed", e)
                    Toast.makeText(this, "Firebase auth failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: ApiException) {
            Log.e("GSignIn", "Google sign-in failed, code=${e.statusCode}", e)
            Toast.makeText(this, "Google sign-in failed (code ${e.statusCode})", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // --- Google Sign-In config ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // from google-services.json
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)
        // -----------------------------

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val btnGoogle = findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogle)
        val btnAdminLogin = findViewById<Button>(R.id.btnAdminLogin)

        // Email/password login (your existing code)
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email"
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        // 🔑 check email verification before entering HomeActivity
                        if (user != null && user.isEmailVerified) {
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(
                                this,
                                "Please verify your email before logging in.",
                                Toast.LENGTH_LONG
                            ).show()
                            auth.signOut() // keep them logged out until verified
                        }
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage ?: "Login failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        val tvResendVerification = findViewById<TextView>(R.id.tvResendVerification)

        tvResendVerification.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Enter a valid email"; etEmail.requestFocus(); return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Enter your password"; etPassword.requestFocus(); return@setOnClickListener
            }

            tvResendVerification.isEnabled = false

            // Temporarily sign in just to send the verification email, then sign out.
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        val user = auth.currentUser
                        user?.reload()?.addOnCompleteListener {
                            if (user != null && user.isEmailVerified) {
                                Toast.makeText(this, "This account is already verified.", Toast.LENGTH_LONG).show()
                                auth.signOut()
                                tvResendVerification.isEnabled = true
                            } else {
                                user?.sendEmailVerification()?.addOnCompleteListener { sendTask ->
                                    if (sendTask.isSuccessful) {
                                        Toast.makeText(this, "Verification email sent. Check your inbox (or spam).", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this, sendTask.exception?.localizedMessage ?: "Failed to send verification email.", Toast.LENGTH_LONG).show()
                                    }
                                    auth.signOut()
                                    tvResendVerification.isEnabled = true
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, signInTask.exception?.localizedMessage ?: "Couldn’t sign in to resend email.", Toast.LENGTH_LONG).show()
                        tvResendVerification.isEnabled = true
                    }
                }
        }

        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "Enter your email to reset"
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Enter a valid email"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            tvForgotPassword.isEnabled = false
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    tvForgotPassword.isEnabled = true
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Password reset email sent. Check your inbox (or spam).",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val msg = when ((task.exception as? FirebaseAuthException)?.errorCode) {
                            "ERROR_INVALID_EMAIL" -> "That email address is invalid."
                            "ERROR_USER_NOT_FOUND" -> "No account found for this email."
                            else -> task.exception?.localizedMessage ?: "Failed to send reset email."
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Google sign-in button
        btnGoogle.setOnClickListener {
            // Optional: sign-out any prior cached Google account to force chooser:
            // googleClient.signOut()
            googleSignInLauncher.launch(googleClient.signInIntent)
        }

        // Admin login button
        btnAdminLogin.setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }
    }
}

