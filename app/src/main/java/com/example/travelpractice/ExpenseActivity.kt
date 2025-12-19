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
import com.google.firebase.firestore.ListenerRegistration

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


    var currentDisplayCurrency = "USD"
    var conversionRates = mutableMapOf<String, Double>()


    val expenseList = mutableListOf<Expense>()


    private var expenseListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        tripName = intent.getStringExtra("TRIP_NAME") ?: "Trip"
        budgetLimit = intent.getDoubleExtra("TRIP_BUDGET", 900.0)
        tripId = intent.getStringExtra("TRIP_ID") ?: ""

        try {
            setContentView(R.layout.activity_expense_tracker)


            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Expense Tracker"


            val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
                R.id.fabAddExpense
            )
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

            fab.setOnClickListener {

                bottomNav.selectedItemId = R.id.nav_expenses


                supportFragmentManager.setFragmentResult(
                    "open_add_expense",
                    Bundle.EMPTY
                )
            }

            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_expenses -> {
                        fab.show()
                        loadFragment(ExpensesFragment())
                        true
                    }
                    R.id.nav_converter -> {
                        fab.hide()
                        loadFragment(ConverterFragment())
                        true
                    }
                    else -> false
                }
            }


            loadExchangeRates()


            if (savedInstanceState == null) {
                bottomNav.selectedItemId = R.id.nav_expenses
            }


            startExpenseListener()

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


    private fun startExpenseListener() {
        val userId = auth.currentUser?.uid ?: return
        if (tripId.isBlank()) return

        expenseListener = db.collection("expenses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("tripId", tripId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("ExpenseTracker", "Error listening to expenses", error)
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                // Calculate total spent from Firestore
                var newTotalSpent = 0.0
                expenseList.clear()

                for (doc in snapshots.documents) {
                    doc.toObject(Expense::class.java)?.let { expense ->
                        expense.id = doc.id
                        expenseList.add(expense)
                        newTotalSpent += expense.amount
                    }
                }

                totalSpent = newTotalSpent

                // Update the trip's remaining field in Firestore
                syncTripRemaining()

                Log.d("ExpenseTracker", "Real-time update: $totalSpent spent, ${expenseList.size} expenses")
            }
    }


    fun syncTripRemaining() {
        if (tripId.isBlank()) return

        val newRemaining = (budgetLimit - totalSpent).coerceAtLeast(0.0)
        val newSpent = totalSpent

        db.collection("trips").document(tripId)
            .update(
                mapOf(
                    "remaining" to newRemaining,
                    "spent" to newSpent
                )
            )
            .addOnSuccessListener {
                Log.d("ExpenseTracker", "Updated trip: spent=$newSpent, remaining=$newRemaining")
            }
            .addOnFailureListener { e ->
                Log.e("ExpenseTracker", "Failed to update trip", e)
            }
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
                // Return result to TripDetailActivity
                val resultIntent = android.content.Intent().apply {
                    putExtra("RESULT_TRIP_ID", tripId)
                    putExtra("RESULT_REMAINING", (budgetLimit - totalSpent).coerceAtLeast(0.0))
                }
                setResult(RESULT_OK, resultIntent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        expenseListener?.remove()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Return result to TripDetailActivity
        val resultIntent = android.content.Intent().apply {
            putExtra("RESULT_TRIP_ID", tripId)
            putExtra("RESULT_REMAINING", (budgetLimit - totalSpent).coerceAtLeast(0.0))
        }
        setResult(RESULT_OK, resultIntent)
        super.onBackPressed()
    }
}