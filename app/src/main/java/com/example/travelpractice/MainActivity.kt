package com.example.travelpractice  // keep your package

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)

        findViewById<android.widget.Button>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }


        val user = FirebaseAuth.getInstance().currentUser
        // Prefer displayName (username), fall back to email if displayName is null/blank
        val name = user?.displayName?.takeIf { !it.isNullOrBlank() } ?: user?.email ?: "User"
        tvWelcome.text = "Welcome, $name!"
    }

    override fun onStart() {
        super.onStart()
        // Protect this screen: if not logged in, go to Login
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

