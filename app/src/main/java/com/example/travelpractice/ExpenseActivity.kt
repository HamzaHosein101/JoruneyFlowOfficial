package com.example.travelpractice
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Class variables to track expenses
    private var totalSpent = 0.0
    private var budgetLimit = 900.0
    private var tripName = "Trip"
    private var tripId = ""

    // Add these for the expense list
    private lateinit var expenseAdapter: ExpenseAdapter
    private val expenseList = mutableListOf<Expense>()


    // UI Components
    private lateinit var txtSpentAmount: TextView
    private lateinit var txtBudgetAmount: TextView
    private lateinit var txtBudgetStatus: TextView
    private lateinit var txtTotalExpenses: TextView
    private lateinit var txtEmptyState: TextView
    private lateinit var progressBudget: ProgressBar


    // Adapter class for RecyclerView
    inner class ExpenseAdapter(private val expenses: MutableList<Expense>) :
        RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

        inner class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtDescription: TextView = view.findViewById(R.id.txtDescription)
            val txtMeta: TextView = view.findViewById(R.id.txtMeta)
            val btnDelete: View = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)   // ← use your row layout
            return ExpenseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
            val expense = expenses[position]
            val df = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())

            holder.txtDescription.text = "${expense.description} (${expense.category})"
            holder.txtMeta.text = "$${String.format("%.2f", expense.amount)} • ${df.format(java.util.Date(expense.timestamp))}"

            holder.btnDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    confirmDeleteExpense(expenses[pos], pos)     // ← use your helpers
                }
            }
        }

        override fun getItemCount() = expenses.size
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get trip data from intent
        tripName = intent.getStringExtra("TRIP_NAME") ?: "Trip"
        budgetLimit = intent.getDoubleExtra("TRIP_BUDGET", 900.0)
        tripId = intent.getStringExtra("TRIP_ID") ?: ""

        try {
            setContentView(R.layout.activity_expense_tracker)
            android.util.Log.d("ExpenseTrackerActivity", "Layout set successfully")

            // Setup toolbar
            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Expense Tracker"
            android.util.Log.d("ExpenseTrackerActivity", "Toolbar setup complete")

            // Initialize views
            initializeViews()

            // Setup functionality
            setupExpenseList()
            setupCurrencyConverter()
            setupListeners()

            // Load expenses from Firebase
            loadExpensesFromFirebase()

        } catch (e: Exception) {
            android.util.Log.e("ExpenseTrackerActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error loading expense tracker", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeViews() {
        txtSpentAmount = findViewById(R.id.txtSpentAmount)
        txtBudgetAmount = findViewById(R.id.txtBudgetAmount)
        txtBudgetStatus = findViewById(R.id.txtBudgetStatus)
        txtTotalExpenses = findViewById(R.id.txtTotalExpenses)
        txtEmptyState = findViewById(R.id.txtEmptyState)
        progressBudget = findViewById(R.id.progressBudget)

        // Set initial values
        txtBudgetAmount.text = "/ $${String.format("%.2f", budgetLimit)}"
        txtEmptyState.text = "No expenses added yet"
        txtBudgetAmount.text = "/ ${currencyFmt.format(budgetLimit)}"
        txtSpentAmount.text = currencyFmt.format(totalSpent)         // $0.00
        txtBudgetStatus.text = "Remaining: ${currencyFmt.format(budgetLimit)}"

    }

    private var indexToastShown = false  // prevent repeated toasts

    private fun loadExpensesFromFirebase() {
        val userId = auth.currentUser?.uid ?: return
        if (tripId.isBlank()) {
            Log.w("ExpenseTracker", "No tripId provided; skipping listener")
            return
        }

        val baseQuery = db.collection("expenses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("tripId", tripId)

        baseQuery
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener(this) { snapshots, error ->
                if (error != null) {
                    val code = (error as? FirebaseFirestoreException)?.code
                    Log.e("ExpenseTracker", "Snapshot error: code=$code msg=${error.message}", error)

                    // If the composite index isn’t ready, silently fallback so users see data without any popups
                    if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        baseQuery.get()
                            .addOnSuccessListener { res ->
                                expenseList.clear()
                                totalSpent = 0.0
                                for (doc in res.documents) {
                                    doc.toObject(Expense::class.java)?.let { exp ->
                                        exp.id = doc.id
                                        expenseList.add(exp)
                                        totalSpent += exp.amount
                                    }
                                }
                                // Local sort (DESC by timestamp)
                                expenseList.sortByDescending { it.timestamp }

                                expenseAdapter.notifyDataSetChanged()
                                updateBudgetDisplay()
                                updateEmptyState()
                            }
                            .addOnFailureListener { e2 ->
                                Log.e("ExpenseTracker", "Fallback query failed", e2)
                            }
                    }
                    // No Toasts shown to users for any error
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                expenseList.clear()
                totalSpent = 0.0
                for (doc in snapshots.documents) {
                    doc.toObject(Expense::class.java)?.let { exp ->
                        exp.id = doc.id
                        expenseList.add(exp)
                        totalSpent += exp.amount
                    }
                }

                expenseAdapter.notifyDataSetChanged()
                updateBudgetDisplay()
                updateEmptyState()
            }
    }

    private fun setupListeners() {
        val etExpenseDescription = findViewById<TextInputEditText>(R.id.etExpenseDescription)
        val etExpenseAmount = findViewById<TextInputEditText>(R.id.etExpenseAmount)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val btnAddExpense = findViewById<MaterialButton>(R.id.btnAddExpense)

        // Setup category spinner
        val categories = arrayOf("Food", "Transportation", "Accommodation", "Entertainment", "Shopping", "Other")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        // Add expense button click listener
        btnAddExpense.setOnClickListener {
            val description = etExpenseDescription.text.toString().trim()
            val amountText = etExpenseAmount.text.toString().trim()
            val category = categories[spinnerCategory.selectedItemPosition]

            when {
                description.isEmpty() -> {
                    Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
                    etExpenseDescription.requestFocus()
                }
                amountText.isEmpty() -> {
                    Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
                    etExpenseAmount.requestFocus()
                }
                else -> {
                    try {
                        val amount = amountText.toDouble()
                        if (amount <= 0) {
                            Toast.makeText(this, "Please enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // Add the expense to Firebase
                        addExpenseToFirebase(description, amount, category)

                        // Clear the input fields
                        etExpenseDescription.setText("")
                        etExpenseAmount.setText("")


                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter a valid number for amount", Toast.LENGTH_SHORT).show()
                        etExpenseAmount.requestFocus()
                    }
                }
            }
        }


    }

    private fun addExpenseToFirebase(description: String, amount: Double, category: String) {
        val userId = auth.currentUser?.uid ?: return

        val newExpense = Expense(
            id = "", // Firestore will give us this
            description = description,
            amount = amount,
            category = category,
            timestamp = System.currentTimeMillis(),
            tripId = tripId,
            userId = userId
        )

        db.collection("expenses")
            .add(
                hashMapOf(
                    "description" to newExpense.description,
                    "amount" to newExpense.amount,
                    "category" to newExpense.category,
                    "timestamp" to newExpense.timestamp,   // keep using client time for instant sort
                    "tripId" to newExpense.tripId,
                    "userId" to newExpense.userId
                )
            )
            .addOnSuccessListener { ref ->
                // Optimistic update so it shows instantly
                newExpense.id = ref.id
                expenseList.add(0, newExpense)              // add to top since DESC
                totalSpent += newExpense.amount
                expenseAdapter.notifyItemInserted(0)
                updateBudgetDisplay()
                updateEmptyState()

                if (totalSpent > budgetLimit) {
                    Toast.makeText(this, "Warning: You're over budget!", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ExpenseTracker", "Error adding expense", e)
                Toast.makeText(this, "Failed to add expense", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupExpenseList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewExpenses)

        // Setup RecyclerView
        expenseAdapter = ExpenseAdapter(expenseList)
        recyclerView.adapter = expenseAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Show/hide empty state
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewExpenses)

        if (expenseList.isEmpty()) {
            txtEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            txtEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private val currencyFmt = NumberFormat.getCurrencyInstance()

    private fun updateBudgetDisplay() {
        val remaining = budgetLimit - totalSpent

        txtSpentAmount.text = currencyFmt.format(totalSpent)
        txtTotalExpenses.text = "Total: ${currencyFmt.format(totalSpent)}"

        // Text: remaining or over by
        if (remaining >= 0) {
            txtBudgetStatus.text = "Remaining: ${currencyFmt.format(remaining)}"
        } else {
            txtBudgetStatus.text = "Over by ${currencyFmt.format(-remaining)}"
        }

        // Progress still shows % used (keeps a good visual)
        val usedPct = if (budgetLimit > 0) ((totalSpent / budgetLimit) * 100).coerceIn(0.0, 100.0) else 0.0
        progressBudget.progress = usedPct.toInt()

        // Color-code by remaining
        val color = when {
            remaining < 0                         -> android.graphics.Color.parseColor("#FF6B6B") // red
            remaining <= budgetLimit * 0.20       -> android.graphics.Color.parseColor("#FFC107") // yellow (<=20% left)
            else                                  -> android.graphics.Color.parseColor("#2E5BFF") // blue
        }
        txtBudgetStatus.setTextColor(color)
    }


    private fun setupCurrencyConverter() {
        val etAmount = findViewById<TextInputEditText>(R.id.etAmount)
        val spinnerFromCurrency = findViewById<Spinner>(R.id.spinnerFromCurrency)
        val spinnerToCurrency = findViewById<Spinner>(R.id.spinnerToCurrency)
        val txtConvertedAmount = findViewById<TextView>(R.id.txtConvertedAmount)

        val currencies = arrayOf(
            "USD - US Dollar", "EUR - Euro", "GBP - British Pound", "JPY - Japanese Yen",
            "CAD - Canadian Dollar", "AUD - Australian Dollar", "CHF - Swiss Franc",
            "CNY - Chinese Yuan", "INR - Indian Rupee", "KRW - South Korean Won",
            "MXN - Mexican Peso", "BRL - Brazilian Real", "ZAR - South African Rand",
            "SGD - Singapore Dollar", "HKD - Hong Kong Dollar"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerFromCurrency.adapter = adapter
        spinnerToCurrency.adapter = adapter

        spinnerFromCurrency.setSelection(0)
        spinnerToCurrency.setSelection(1)

        val conversionRates = mapOf(
            "USD" to 1.0, "EUR" to 0.85, "GBP" to 0.73, "JPY" to 110.0,
            "CAD" to 1.25, "AUD" to 1.35, "CHF" to 0.92, "CNY" to 6.45,
            "INR" to 75.0, "KRW" to 1180.0, "MXN" to 20.0, "BRL" to 5.2,
            "ZAR" to 14.5, "SGD" to 1.35, "HKD" to 7.8
        )



        fun convertCurrency() {
            val amountText = etAmount.text.toString()
            if (amountText.isNotEmpty()) {
                try {
                    val amount = amountText.toDouble()
                    val fromCurrency = currencies[spinnerFromCurrency.selectedItemPosition].substring(0, 3)
                    val toCurrency = currencies[spinnerToCurrency.selectedItemPosition].substring(0, 3)

                    val fromRate = conversionRates[fromCurrency] ?: 1.0
                    val toRate = conversionRates[toCurrency] ?: 1.0

                    val usdAmount = amount / fromRate
                    val convertedAmount = usdAmount * toRate

                    txtConvertedAmount.text = "Converted: ${String.format("%.2f", convertedAmount)} $toCurrency"
                } catch (e: NumberFormatException) {
                    txtConvertedAmount.text = "Converted: Invalid amount"
                }
            } else {
                txtConvertedAmount.text = "Converted: $0.00"
            }
        }

        etAmount.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { convertCurrency() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        spinnerFromCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                convertCurrency()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerToCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                convertCurrency()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
    private fun confirmDeleteExpense(expense: Expense, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete expense?")
            .setMessage("${expense.description} — $${String.format("%.2f", expense.amount)}")
            .setPositiveButton("Delete") { _, _ ->
                deleteExpense(expense, position)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // refresh the row so swipe/long-press visual resets if needed
                expenseAdapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteExpense(expense: Expense, position: Int) {
        // Optimistic UI: remove locally
        val removed = expenseList.removeAt(position)
        totalSpent -= removed.amount
        expenseAdapter.notifyItemRemoved(position)
        updateBudgetDisplay()
        updateEmptyState()

        // Delete from Firestore
        if (expense.id.isNotBlank()) {
            db.collection("expenses").document(expense.id)
                .delete()
                .addOnSuccessListener {
                    // Snapshot listener will reconcile if needed; nothing else to do
                }
                .addOnFailureListener { e ->
                    // Revert UI on failure
                    expenseList.add(position, removed)
                    totalSpent += removed.amount
                    expenseAdapter.notifyItemInserted(position)
                    updateBudgetDisplay()
                    updateEmptyState()
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // No id? Just refresh to resync with server
            loadExpensesFromFirebase()
        }
    }

}