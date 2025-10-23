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

    // Currency conversion
    private var currentDisplayCurrency = "USD"
    private var conversionRates = mutableMapOf<String, Double>()

    // Add these for the expense list
    private lateinit var expenseAdapter: ExpenseAdapter
    private val expenseList = mutableListOf<Expense>()
    private val filteredExpenseList = mutableListOf<Expense>()

    // Sort and filter state
    private var currentSortOption = "Date (Newest)"
    private var currentSearchQuery = ""

    // UI Components
    private lateinit var txtSpentAmount: TextView
    private lateinit var txtBudgetAmount: TextView
    private lateinit var txtBudgetStatus: TextView
    private lateinit var txtTotalExpenses: TextView
    private lateinit var txtEmptyState: TextView
    private lateinit var progressBudget: ProgressBar
    private lateinit var etSearchExpenses: TextInputEditText
    private lateinit var spinnerSortExpenses: Spinner

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
                .inflate(R.layout.item_expense, parent, false)
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
            setupSortSpinner()
            setupSearchBar()
            setupDisplayCurrencySpinner()
            setupExpenseCurrencySpinner()
            setupCurrencyConverter()
            setupListeners()
            setupRefreshButton()

            // Load exchange rates and expenses
            loadExchangeRates()
            loadExpensesFromFirebase()

        } catch (e: Exception) {
            android.util.Log.e("ExpenseTrackerActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error loading expense tracker", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun syncTripRemaining() {
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

    private fun initializeViews() {
        txtSpentAmount = findViewById(R.id.txtSpentAmount)
        txtBudgetAmount = findViewById(R.id.txtBudgetAmount)
        txtBudgetStatus = findViewById(R.id.txtBudgetStatus)
        txtTotalExpenses = findViewById(R.id.txtTotalExpenses)
        txtEmptyState = findViewById(R.id.txtEmptyState)
        progressBudget = findViewById(R.id.progressBudget)
        etSearchExpenses = findViewById(R.id.etSearchExpenses)
        spinnerSortExpenses = findViewById(R.id.spinnerSortExpenses)

        // Set initial values
        txtBudgetAmount.text = "/ ${currencyFmt.format(budgetLimit)}"
        txtSpentAmount.text = currencyFmt.format(totalSpent)
        txtBudgetStatus.text = "Remaining: ${currencyFmt.format(budgetLimit)}"
        txtEmptyState.text = "No expenses match your search"
    }

    private fun setupSortSpinner() {
        val sortOptions = arrayOf(
            "Date (Newest)",
            "Date (Oldest)",
            "Amount (Highest)",
            "Amount (Lowest)",
            "Name (A-Z)",
            "Name (Z-A)",
            "Category"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSortExpenses.adapter = adapter

        spinnerSortExpenses.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortOption = sortOptions[position]
                applySortAndFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearchBar() {
        etSearchExpenses.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearchQuery = s.toString().trim()
                applySortAndFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applySortAndFilter() {
        // First, filter based on search query
        filteredExpenseList.clear()

        if (currentSearchQuery.isEmpty()) {
            filteredExpenseList.addAll(expenseList)
        } else {
            val query = currentSearchQuery.lowercase()
            for (expense in expenseList) {
                val matchesDescription = expense.description.lowercase().contains(query)
                val matchesAmount = expense.amount.toString().contains(query)
                val matchesCategory = expense.category.lowercase().contains(query)

                if (matchesDescription || matchesAmount || matchesCategory) {
                    filteredExpenseList.add(expense)
                }
            }
        }

        // Then sort the filtered list
        when (currentSortOption) {
            "Date (Newest)" -> filteredExpenseList.sortByDescending { it.timestamp }
            "Date (Oldest)" -> filteredExpenseList.sortBy { it.timestamp }
            "Amount (Highest)" -> filteredExpenseList.sortByDescending { it.amount }
            "Amount (Lowest)" -> filteredExpenseList.sortBy { it.amount }
            "Name (A-Z)" -> filteredExpenseList.sortBy { it.description.lowercase() }
            "Name (Z-A)" -> filteredExpenseList.sortByDescending { it.description.lowercase() }
            "Category" -> filteredExpenseList.sortBy { it.category }
        }

        expenseAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun setupDisplayCurrencySpinner() {
        val spinnerDisplayCurrency = findViewById<Spinner>(R.id.spinnerDisplayCurrency)

        val currencies = arrayOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "KRW")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDisplayCurrency.adapter = adapter

        spinnerDisplayCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentDisplayCurrency = currencies[position]
                updateBudgetDisplay()
                syncTripRemaining()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupExpenseCurrencySpinner() {
        val spinnerExpenseCurrency = findViewById<Spinner>(R.id.spinnerExpenseCurrency)

        val currencies = arrayOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "KRW")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExpenseCurrency.adapter = adapter
    }

    private fun loadExchangeRates() {
        CurrencyApiManager.fetchExchangeRates(object : CurrencyApiManager.ExchangeRateCallback {
            override fun onSuccess(rates: Map<String, Double>) {
                conversionRates.clear()
                conversionRates.putAll(rates)
                Log.d("ExpenseTracker", "Exchange rates loaded: ${rates.size} currencies")
                updateBudgetDisplay()
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

    private fun setupRefreshButton() {
        val btnRefreshRates = findViewById<MaterialButton>(R.id.btnRefreshRates)
        btnRefreshRates.setOnClickListener {
            Toast.makeText(this, "Refreshing exchange rates...", Toast.LENGTH_SHORT).show()
            loadExchangeRates()
        }
    }

    private fun convertCurrency(amountUSD: Double, toCurrency: String): Double {
        if (conversionRates.isEmpty()) {
            return amountUSD
        }
        val rate = conversionRates[toCurrency] ?: 1.0
        return amountUSD * rate
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
                                applySortAndFilter()
                                updateBudgetDisplay()
                                syncTripRemaining()
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

                applySortAndFilter()
                updateBudgetDisplay()
            }
    }

    private fun setupListeners() {
        val etExpenseDescription = findViewById<TextInputEditText>(R.id.etExpenseDescription)
        val etExpenseAmount = findViewById<TextInputEditText>(R.id.etExpenseAmount)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerExpenseCurrency = findViewById<Spinner>(R.id.spinnerExpenseCurrency)
        val btnAddExpense = findViewById<MaterialButton>(R.id.btnAddExpense)

        val categories = arrayOf("Food", "Transportation", "Accommodation", "Entertainment", "Shopping", "Other")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        btnAddExpense.setOnClickListener {
            val description = etExpenseDescription.text.toString().trim()
            val amountText = etExpenseAmount.text.toString().trim()
            val category = categories[spinnerCategory.selectedItemPosition]
            val selectedCurrency = spinnerExpenseCurrency.selectedItem.toString()

            when {
                description.isEmpty() -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Missing Description")
                        .setMessage("Please enter a description for your expense.")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                            etExpenseDescription.requestFocus()
                        }
                        .show()
                }
                amountText.isEmpty() -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Missing Amount")
                        .setMessage("Please enter the cost of your expense.")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                            etExpenseAmount.requestFocus()
                        }
                        .show()
                }
                else -> {
                    try {
                        val amount = amountText.toDouble()
                        if (amount <= 0) {
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Invalid Amount")
                                .setMessage("Please enter a valid amount greater than 0.")
                                .setPositiveButton("OK") { dialog, _ ->
                                    dialog.dismiss()
                                    etExpenseAmount.requestFocus()
                                }
                                .show()
                            return@setOnClickListener
                        }

                        val amountInUSD = if (selectedCurrency != "USD" && conversionRates.isNotEmpty()) {
                            val rate = conversionRates[selectedCurrency] ?: 1.0
                            amount / rate
                        } else {
                            amount
                        }

                        MaterialAlertDialogBuilder(this)
                            .setTitle("Confirm Expense")
                            .setMessage("Add expense?\n\n" +
                                    "Description: $description\n" +
                                    "Amount: $amount $selectedCurrency\n" +
                                    "Category: $category")
                            .setPositiveButton("OK") { dialog, _ ->
                                addExpenseToFirebase(description, amountInUSD, category)
                                etExpenseDescription.setText("")
                                etExpenseAmount.setText("")
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()

                    } catch (e: NumberFormatException) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Invalid Amount")
                            .setMessage("Please enter a valid number for the amount.")
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                                etExpenseAmount.requestFocus()
                            }
                            .show()
                    }
                }
            }
        }
    }

    private fun addExpenseToFirebase(description: String, amount: Double, category: String) {
        val userId = auth.currentUser?.uid ?: return

        val newExpense = Expense(
            id = "",
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
                    "timestamp" to newExpense.timestamp,
                    "tripId" to newExpense.tripId,
                    "userId" to newExpense.userId
                )
            )
            .addOnSuccessListener { ref ->
                newExpense.id = ref.id
                expenseList.add(newExpense)
                totalSpent += newExpense.amount
                applySortAndFilter()
                updateBudgetDisplay()

                val snackbar = com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "Expense added: $description - ${String.format("%.2f", amount)}",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                )

                snackbar.setAction("UNDO") {
                    val position = filteredExpenseList.indexOf(newExpense)
                    if (position != -1) {
                        deleteExpense(newExpense, position)
                    }
                }

                snackbar.show()

                if (totalSpent > budgetLimit) {
                    findViewById<View>(android.R.id.content).postDelayed({
                        Toast.makeText(this, "Warning: You're over budget!", Toast.LENGTH_LONG).show()
                    }, 500)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ExpenseTracker", "Error adding expense", e)
                Toast.makeText(this, "Failed to add expense", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupExpenseList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewExpenses)
        expenseAdapter = ExpenseAdapter(filteredExpenseList)
        recyclerView.adapter = expenseAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewExpenses)

        if (filteredExpenseList.isEmpty()) {
            txtEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE

            txtEmptyState.text = if (expenseList.isEmpty()) {
                "No expenses added yet"
            } else {
                "No expenses match your search"
            }
        } else {
            txtEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private val currencyFmt = NumberFormat.getCurrencyInstance()

    private fun updateBudgetDisplay() {
        val displayTotal = convertCurrency(totalSpent, currentDisplayCurrency)
        val displayBudget = convertCurrency(budgetLimit, currentDisplayCurrency)
        val displayRemaining = displayBudget - displayTotal

        val locale = when(currentDisplayCurrency) {
            "EUR" -> Locale.GERMANY
            "GBP" -> Locale.UK
            "JPY" -> Locale.JAPAN
            else -> Locale.US
        }
        val fmt = NumberFormat.getCurrencyInstance(locale)
        if (currentDisplayCurrency != "USD") {
            val currency = Currency.getInstance(currentDisplayCurrency)
            fmt.currency = currency
        }

        txtSpentAmount.text = fmt.format(displayTotal)
        txtBudgetAmount.text = "/ ${fmt.format(displayBudget)}"
        txtTotalExpenses.text = "Total: ${fmt.format(displayTotal)}"

        if (displayRemaining >= 0) {
            txtBudgetStatus.text = "Remaining: ${fmt.format(displayRemaining)}"
        } else {
            txtBudgetStatus.text = "Over by ${fmt.format(-displayRemaining)}"
        }

        val usedPct = if (budgetLimit > 0) ((totalSpent / budgetLimit) * 100).coerceIn(0.0, 100.0) else 0.0
        progressBudget.progress = usedPct.toInt()

        val color = when {
            displayRemaining < 0 -> android.graphics.Color.parseColor("#FF6B6B")
            displayRemaining <= displayBudget * 0.20 -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#2E5BFF")
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

        fun convertCurrencyInConverter() {
            val amountText = etAmount.text.toString()
            if (amountText.isNotEmpty()) {
                try {
                    val amount = amountText.toDouble()
                    val fromCurrency = currencies[spinnerFromCurrency.selectedItemPosition].substring(0, 3)
                    val toCurrency = currencies[spinnerToCurrency.selectedItemPosition].substring(0, 3)

                    if (conversionRates.isNotEmpty()) {
                        val fromRate = conversionRates[fromCurrency] ?: 1.0
                        val toRate = conversionRates[toCurrency] ?: 1.0
                        val usdAmount = amount / fromRate
                        val convertedAmount = usdAmount * toRate
                        txtConvertedAmount.text = "Converted: ${String.format("%.2f", convertedAmount)} $toCurrency"
                    } else {
                        txtConvertedAmount.text = "Converted: Loading rates..."
                    }
                } catch (e: NumberFormatException) {
                    txtConvertedAmount.text = "Converted: Invalid amount"
                }
            } else {
                txtConvertedAmount.text = "Converted: $0.00"
            }
        }

        etAmount.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { convertCurrencyInConverter() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        spinnerFromCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                convertCurrencyInConverter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerToCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                convertCurrencyInConverter()
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
        expenseList.remove(expense)
        filteredExpenseList.removeAt(position)
        totalSpent -= expense.amount
        expenseAdapter.notifyItemRemoved(position)
        updateBudgetDisplay()
        updateEmptyState()
        syncTripRemaining()

        if (expense.id.isNotBlank()) {
            db.collection("expenses").document(expense.id)
                .delete()
                .addOnSuccessListener {}
                .addOnFailureListener { e ->
                    expenseList.add(expense)
                    totalSpent += expense.amount
                    applySortAndFilter()
                    updateBudgetDisplay()
                    syncTripRemaining()
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            loadExpensesFromFirebase()
        }
    }
}