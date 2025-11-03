package com.example.travelpractice.admin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.travelpractice.R

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREF_NAME = "admin_prefs"
        private const val KEY_IS_LOGGED_IN = "is_admin_logged_in"
        private const val ADMIN_USERNAME = "Admin"
        private const val ADMIN_PASSWORD = "admin123"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        etUsername = findViewById(R.id.etAdminUsername)
        etPassword = findViewById(R.id.etAdminPassword)
        btnLogin = findViewById(R.id.btnAdminLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (username.isEmpty()) {
                etUsername.error = "Username is required"
                etUsername.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Password is required"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            if (username == ADMIN_USERNAME && password == ADMIN_PASSWORD) {
                // Save login state
                prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                
                // Navigate to admin panel
                val intent = Intent(this, AdminPanelActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

