package com.example.travelpractice
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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


// Data class for expenses - UPDATED with currency fields
data class Expense(
    var id: String = "",
    val description: String = "",
    val amount: Double = 0.0,  // stored in USD
    val category: String = "Other",
    val timestamp: Long = System.currentTimeMillis(),
    val tripId: String = "",
    val userId: String = "",
    val currency: String = "USD",  // original currency
    val originalAmount: Double = 0.0  // original amount in original currency
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
    private var displayCurrency = "USD"  // User's preferred display currency


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


    // UPDATED: Changed to mutable map for API updates
    private var conversionRates = mutableMapOf(
        "USD" to 1.0, "EUR" to 0.85, "GBP" to 0.73, "JPY" to 110.0,
        "CAD" to 1.25, "AUD" to 1.35, "CHF" to 0.92, "CNY" to 6.45,
        "INR" to 75.0, "KRW" to 1180.0, "MXN" to 20.0, "BRL" to 5.2,
        "ZAR" to 14.5, "SGD" to 1.35, "HKD" to 7.8
    )

    // NEW: Variables to track API rate updates
    private var lastRateUpdate: Long = 0
    private val RATE_CACHE_DURATION = 3600000L // 1 hour in milliseconds


    // Currency symbols map
    private val currencySymbols = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
        "CAD" to "C$", "AUD" to "A$", "CHF" to "CHF", "CNY" to "¥",
        "INR" to "₹", "KRW" to "₩", "MXN" to "$", "BRL" to "R$",
        "ZAR" to "R", "SGD" to "S$", "HKD" to "HK$"
    )


    // Adapter class for RecyclerView - UPDATED to show currency
    inner class ExpenseAdapter(private val expenses: MutableList<Expense>) :
        RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {


        inner class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtDescription: TextView = view.findViewById(R.id.txtDescription)
            val txtMeta: TextView = view.findViewById(R.id.txtMeta)
            val btnDelete: View = view.findViewById(R.id.btnDelete)
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)
            return ExpenseViewHolder(view)
        }


        override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
            val expense = expenses[position]
            val df = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())


            holder.txtDescription.text = "${expense.description} (${expense.category})"


            // Show original amount and currency, plus USD equivalent if different
            val displayText = if (expense.currency != "USD") {
                "${String.format("%.2f", expense.originalAmount)} ${expense.currency} ($${String.format("%.2f", expense.amount)} USD) • ${df.format(Date(expense.timestamp))}"
            } else {
                "$${String.format("%.2f", expense.amount)} • ${df.format(Date(expense.timestamp))}"
            }


            holder.txtMeta.text = displayText


            holder.btnDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    confirmDeleteExpense(expenses[pos], pos)
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
            Log.d("ExpenseTrackerActivity", "Layout set successfully")


            // Setup toolbar
            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Expense Tracker"
            Log.d("ExpenseTrackerActivity", "Toolbar setup complete")


            // Initialize views
            initializeViews()


            // Setup functionality
            setupDisplayCurrencySpinner()
            setupExpenseList()
            setupCurrencyConverter()
            setupListeners()


            // Load expenses from Firebase
            loadExpensesFromFirebase()

            // NEW: Fetch live exchange rates from API
            fetchLiveExchangeRates()


        } catch (e: Exception) {
            Log.e("ExpenseTrackerActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error loading expense tracker", Toast.LENGTH_SHORT).show()
            finish()
        }
    }


    // NEW: Method to fetch live exchange rates
    private fun fetchLiveExchangeRates() {
        val currentTime = System.currentTimeMillis()

        // Only fetch if rates are older than 1 hour
        if (currentTime - lastRateUpdate < RATE_CACHE_DURATION && lastRateUpdate > 0) {
            Log.d("ExpenseTracker", "Using cached rates")
            return
        }

        CurrencyApiManager.fetchExchangeRates(object : CurrencyApiManager.ExchangeRateCallback {
            override fun onSuccess(rates: Map<String, Double>) {
                runOnUiThread {
                    // Update conversion rates
                    conversionRates.clear()
                    conversionRates.putAll(rates)
                    lastRateUpdate = currentTime

                    // Refresh the display with new rates
                    updateBudgetDisplay()

                    Toast.makeText(
                        this@ExpenseTrackerActivity,
                        "Exchange rates updated",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.d("ExpenseTracker", "Updated ${rates.size} exchange rates")
                }
            }

            override fun onFailure(error: String) {
                runOnUiThread {
                    Log.e("ExpenseTracker", "Failed to fetch rates: $error")
                    Toast.makeText(
                        this@ExpenseTrackerActivity,
                        "Using offline rates",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }


    private fun initializeViews() {
        txtSpentAmount = findViewById(R.id.txtSpentAmount)
        txtBudgetAmount = findViewById(R.id.txtBudgetAmount)
        txtBudgetStatus = findViewById(R.id.txtBudgetStatus)
        txtTotalExpenses = findViewById(R.id.txtTotalExpenses)
        txtEmptyState = findViewById(R.id.txtEmptyState)
        progressBudget = findViewById(R.id.progressBudget)


        // Set initial values
        txtEmptyState.text = "No expenses added yet"
        updateBudgetDisplay()
    }


    private fun setupDisplayCurrencySpinner() {
        val spinnerDisplayCurrency = findViewById<Spinner>(R.id.spinnerDisplayCurrency)


        val currencies = arrayOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "KRW", "MXN", "BRL", "ZAR", "SGD", "HKD")
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDisplayCurrency.adapter = currencyAdapter


        // Set default to USD
        spinnerDisplayCurrency.setSelection(0)


        // Listen for currency changes
        spinnerDisplayCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                displayCurrency = currencies[position]
                Log.d("ExpenseTracker", "Display currency changed to: $displayCurrency, Total spent in USD: $totalSpent")
                updateBudgetDisplay()  // Refresh display with new currency
                Toast.makeText(this@ExpenseTrackerActivity, "Currency changed to $displayCurrency", Toast.LENGTH_SHORT).show()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }


    private var indexToastShown = false


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
                                expenseList.sortByDescending { it.timestamp }
                                expenseAdapter.notifyDataSetChanged()
                                updateBudgetDisplay()
                                updateEmptyState()
                            }
                            .addOnFailureListener { e2 ->
                                Log.e("ExpenseTracker", "Fallback query failed", e2)
                            }
                    }
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
        val spinnerExpenseCurrency = findViewById<Spinner>(R.id.spinnerExpenseCurrency)
        val btnAddExpense = findViewById<MaterialButton>(R.id.btnAddExpense)


        // Setup category spinner
        val categories = arrayOf("Food", "Transportation", "Accommodation", "Entertainment", "Shopping", "Other")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter


        // Setup currency spinner for expenses
        val currencies = arrayOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "KRW", "MXN", "BRL", "ZAR", "SGD", "HKD")
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExpenseCurrency.adapter = currencyAdapter


        // Add expense button click listener
        btnAddExpense.setOnClickListener {
            val description = etExpenseDescription.text.toString().trim()
            val amountText = etExpenseAmount.text.toString().trim()
            val category = categories[spinnerCategory.selectedItemPosition]
            val currency = currencies[spinnerExpenseCurrency.selectedItemPosition]


            // Validate inputs
            when {
                description.isEmpty() -> {
                    showValidationDialog("Missing Description", "Please enter a description for this expense.")
                    etExpenseDescription.requestFocus()
                }
                amountText.isEmpty() -> {
                    showValidationDialog("Missing Amount", "Please enter an amount for this expense.")
                    etExpenseAmount.requestFocus()
                }
                else -> {
                    try {
                        val amount = amountText.toDouble()
                        if (amount <= 0) {
                            showValidationDialog("Invalid Amount", "Please enter an amount greater than 0.")
                            return@setOnClickListener
                        }


                        // Show confirmation dialog before adding
                        showAddExpenseConfirmation(description, amount, category, currency, etExpenseDescription, etExpenseAmount)


                    } catch (e: NumberFormatException) {
                        showValidationDialog("Invalid Amount", "Please enter a valid number for the amount.")
                        etExpenseAmount.requestFocus()
                    }
                }
            }
        }
    }


    private fun showValidationDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun showAddExpenseConfirmation(
        description: String,
        amount: Double,
        category: String,
        currency: String,
        etDescription: TextInputEditText,
        etAmount: TextInputEditText
    ) {
        val symbol = currencySymbols[currency] ?: currency
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Expense?")
            .setMessage("$description\n$symbol${String.format("%.2f", amount)} ($category)")
            .setPositiveButton("Add") { dialog, _ ->
                // Add the expense to Firebase with currency
                addExpenseToFirebase(description, amount, category, currency)


                // Clear the input fields
                etDescription.setText("")
                etAmount.setText("")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun addExpenseToFirebase(description: String, amount: Double, category: String, currency: String) {
        val userId = auth.currentUser?.uid ?: return


        // Convert to USD for storage
        val rate = conversionRates[currency] ?: 1.0
        val amountInUSD = amount / rate


        val newExpense = Expense(
            id = "",
            description = description,
            amount = amountInUSD,  // Store in USD
            category = category,
            timestamp = System.currentTimeMillis(),
            tripId = tripId,
            userId = userId,
            currency = currency,  // original currency
            originalAmount = amount  // original amount
        )


        db.collection("expenses")
            .add(
                hashMapOf(
                    "description" to newExpense.description,
                    "amount" to newExpense.amount,
                    "category" to newExpense.category,
                    "timestamp" to newExpense.timestamp,
                    "tripId" to newExpense.tripId,
                    "userId" to newExpense.userId,
                    "currency" to newExpense.currency,
                    "originalAmount" to newExpense.originalAmount
                )
            )
            .addOnSuccessListener { ref ->
                newExpense.id = ref.id
                expenseList.add(0, newExpense)
                totalSpent += newExpense.amount
                expenseAdapter.notifyItemInserted(0)
                updateBudgetDisplay()
                updateEmptyState()


                // Show snackbar with undo option
                showUndoSnackbar(newExpense, 0)


                if (totalSpent > budgetLimit) {
                    Toast.makeText(this, "Warning: You're over budget!", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ExpenseTracker", "Error adding expense", e)
                Toast.makeText(this, "Failed to add expense", Toast.LENGTH_SHORT).show()
            }
    }


    private fun showUndoSnackbar(expense: Expense, position: Int) {
        val symbol = currencySymbols[expense.currency] ?: expense.currency
        val rootView = findViewById<View>(android.R.id.content)

        Snackbar.make(
            rootView,
            "Expense added: ${expense.description}",
            Snackbar.LENGTH_LONG
        )
            .setAction("UNDO") {
                // Remove from list and Firebase
                undoExpenseAddition(expense, position)
            }
            .show()
    }


    private fun undoExpenseAddition(expense: Expense, position: Int) {
        // Remove from list
        if (position < expenseList.size && expenseList[position].id == expense.id) {
            expenseList.removeAt(position)
            totalSpent -= expense.amount
            expenseAdapter.notifyItemRemoved(position)
            updateBudgetDisplay()
            updateEmptyState()


            // Delete from Firebase
            if (expense.id.isNotBlank()) {
                db.collection("expenses").document(expense.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Expense removed", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ExpenseTracker", "Error removing expense", e)
                        // Re-add if delete failed
                        expenseList.add(position, expense)
                        totalSpent += expense.amount
                        expenseAdapter.notifyItemInserted(position)
                        updateBudgetDisplay()
                        updateEmptyState()
                        Toast.makeText(this, "Failed to undo", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }


    private fun setupExpenseList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewExpenses)


        expenseAdapter = ExpenseAdapter(expenseList)
        recyclerView.adapter = expenseAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)


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


    private fun updateBudgetDisplay() {
        // Convert totalSpent from USD to display currency
        val displayRate = conversionRates[displayCurrency] ?: 1.0
        val totalSpentInDisplayCurrency = totalSpent * displayRate
        val budgetInDisplayCurrency = budgetLimit * displayRate
        val remaining = budgetInDisplayCurrency - totalSpentInDisplayCurrency


        // Get currency symbol
        val symbol = currencySymbols[displayCurrency] ?: displayCurrency


        txtSpentAmount.text = "$symbol${String.format("%.2f", totalSpentInDisplayCurrency)}"
        txtBudgetAmount.text = "/ $symbol${String.format("%.2f", budgetInDisplayCurrency)}"
        txtTotalExpenses.text = "Total: $symbol${String.format("%.2f", totalSpentInDisplayCurrency)}"


        if (remaining >= 0) {
            txtBudgetStatus.text = "Remaining: $symbol${String.format("%.2f", remaining)}"
        } else {
            txtBudgetStatus.text = "Over by $symbol${String.format("%.2f", -remaining)}"
        }


        val usedPct = if (budgetInDisplayCurrency > 0) ((totalSpentInDisplayCurrency / budgetInDisplayCurrency) * 100).coerceIn(0.0, 100.0) else 0.0
        progressBudget.progress = usedPct.toInt()


        val color = when {
            remaining < 0                                  -> android.graphics.Color.parseColor("#FF6B6B")
            remaining <= budgetInDisplayCurrency * 0.20    -> android.graphics.Color.parseColor("#FFC107")
            else                                           -> android.graphics.Color.parseColor("#2E5BFF")
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
                expenseAdapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .show()
    }


    private fun deleteExpense(expense: Expense, position: Int) {
        val removed = expenseList.removeAt(position)
        totalSpent -= removed.amount
        expenseAdapter.notifyItemRemoved(position)
        updateBudgetDisplay()
        updateEmptyState()


        if (expense.id.isNotBlank()) {
            db.collection("expenses").document(expense.id)
                .delete()
                .addOnSuccessListener {
                    // Success
                }
                .addOnFailureListener { e ->
                    expenseList.add(position, removed)
                    totalSpent += removed.amount
                    expenseAdapter.notifyItemInserted(position)
                    updateBudgetDisplay()
                    updateEmptyState()
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            loadExpensesFromFirebase()
        }
    }
}