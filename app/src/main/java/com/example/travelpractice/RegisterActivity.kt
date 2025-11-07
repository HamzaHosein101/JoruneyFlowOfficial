package com.example.travelpractice  // keep your package

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

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
        val btnRegister = findViewById<Button>(R.id.btnRegister)

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

                        user?.updateProfile(updates)

                        //Send email verification
                        user?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
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
                                    verifyTask.exception?.localizedMessage ?: "Failed to send verification email",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
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
}

