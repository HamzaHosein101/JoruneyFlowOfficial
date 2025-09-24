package com.example.travelpractice.ui.checklist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.travelpractice.R
import com.google.android.material.appbar.MaterialToolbar

class BagChecklistActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bag_checklist)

        // ✅ Toolbar setup
        val bar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(bar)

        // ✅ Get the trip title passed from TripDetailActivity
        val titleFromTrip = intent.getStringExtra("extra_trip_title")

        // ✅ Set the toolbar title
        supportActionBar?.title = titleFromTrip?.let { "$it • Checklist" } ?: "Bag Checklist"

        // ✅ Add back navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
}




