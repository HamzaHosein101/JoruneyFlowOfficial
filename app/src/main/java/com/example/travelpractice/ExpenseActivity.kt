package com.example.travelpractice

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Data class for expenses
data class Expense(
    var id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val category: String = "Other",
    val timestamp: Long = System.currentTimeMillis(),
    val tripId: String = "",
    val userId: String = ""
)

class ExpenseTrackerActivity : AppCompatActivity() {

    // Firebase
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    // Class variables - made public for fragments
    var totalSpent = 0.0
    var budgetLimit = 900.0
    var tripName = "Trip"
    var tripId = ""

    // Currency conversion
    var currentDisplayCurrency = "USD"
    var conversionRates = mutableMapOf<String, Double>()

    // Expense list
    val expenseList = mutableListOf<Expense>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get trip data from intent
        tripName = intent.getStringExtra("TRIP_NAME") ?: "Trip"
        budgetLimit = intent.getDoubleExtra("TRIP_BUDGET", 900.0)
        tripId = intent.getStringExtra("TRIP_ID") ?: ""

        try {
            setContentView(R.layout.activity_expense_tracker)

            // Setup toolbar
            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Expense Tracker"

            // Setup bottom navigation
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_expenses -> {
                        loadFragment(ExpensesFragment())
                        true
                    }
                    R.id.nav_converter -> {
                        loadFragment(ConverterFragment())
                        true
                    }
                    else -> false
                }
            }

            // Load exchange rates
            loadExchangeRates()

            // Load initial fragment
            if (savedInstanceState == null) {
                loadFragment(ExpensesFragment())
            }

        } catch (e: Exception) {
            Log.e("ExpenseTrackerActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error loading expense tracker", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun loadExchangeRates() {
        CurrencyApiManager.fetchExchangeRates(object : CurrencyApiManager.ExchangeRateCallback {
            override fun onSuccess(rates: Map<String, Double>) {
                conversionRates.clear()
                conversionRates.putAll(rates)
                Log.d("ExpenseTracker", "Exchange rates loaded: ${rates.size} currencies")
            }

            override fun onFailure(error: String) {
                Log.e("ExpenseTracker", "Failed to load exchange rates: $error")
                conversionRates = mutableMapOf(
                    "USD" to 1.0, "EUR" to 0.85, "GBP" to 0.73, "JPY" to 110.0,
                    "CAD" to 1.25, "AUD" to 1.35, "CHF" to 0.92, "CNY" to 6.45,
                    "INR" to 75.0, "KRW" to 1180.0
                )
            }
        })
    }

    fun syncTripRemaining() {
        if (tripId.isBlank()) return
        val remaining = (budgetLimit - totalSpent).coerceAtLeast(0.0)
        val update = hashMapOf<String, Any>(
            "remaining" to remaining,
            "spent" to totalSpent
        )
        db.collection("trips").document(tripId)
            .update(update)
            .addOnFailureListener { e -> Log.w("ExpenseTracker", "Failed to update remaining", e) }
    }

    fun convertCurrency(amountUSD: Double, toCurrency: String): Double {
        if (conversionRates.isEmpty()) {
            return amountUSD
        }
        val rate = conversionRates[toCurrency] ?: 1.0
        return amountUSD * rate
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}