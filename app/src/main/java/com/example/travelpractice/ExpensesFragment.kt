package com.example.travelpractice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ProgressBar
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.Currency
import android.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat

class ExpensesFragment : Fragment() {

    private lateinit var activity: ExpenseTrackerActivity
    private lateinit var expenseAdapter: ExpenseAdapter
    private val filteredExpenseList = mutableListOf<Expense>()

    private var currentSortOption = "Date (Newest)"
    private var currentSearchQuery = ""
    private var snapshotListener: ListenerRegistration? = null

    // UI Components
    private lateinit var txtSpentAmount: TextView
    private lateinit var txtBudgetAmount: TextView
    private lateinit var txtBudgetStatus: TextView
    private lateinit var txtTotalExpenses: TextView
    private lateinit var txtEmptyState: TextView
    private lateinit var progressBudget: ProgressBar
    private var etSearchExpenses: TextInputEditText? = null
    private var spinnerSortExpenses: Spinner? = null

    // Currency list - MATCHING CONVERTER (15 currencies)
    private val currencies = arrayOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF",
        "CNY", "INR", "KRW", "MXN", "BRL", "ZAR", "SGD", "HKD")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expenses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity = requireActivity() as ExpenseTrackerActivity

        initializeViews(view)
        setupExpenseList(view)
        setupDisplayCurrencySpinner(view)
        setupFAB()

        // Setup search/sort if views exist
        etSearchExpenses = view.findViewById(R.id.etSearchExpenses)
        spinnerSortExpenses = view.findViewById(R.id.spinnerSortExpenses)

        if (etSearchExpenses != null && spinnerSortExpenses != null) {
            setupSortSpinner()
            setupSearchBar()
        }

        // Delay loading to avoid FragmentManager transaction conflict
        view.post {
            loadExpensesFromFirebase()
        }
    }

    private fun initializeViews(view: View) {
        txtSpentAmount = view.findViewById(R.id.txtSpentAmount)
        txtBudgetAmount = view.findViewById(R.id.txtBudgetAmount)
        txtBudgetStatus = view.findViewById(R.id.txtBudgetStatus)
        txtTotalExpenses = view.findViewById(R.id.txtTotalExpenses)
        txtEmptyState = view.findViewById(R.id.txtEmptyState)
        progressBudget = view.findViewById(R.id.progressBudget)

        val currencyFmt = NumberFormat.getCurrencyInstance()
        txtBudgetAmount.text = "/ ${currencyFmt.format(activity.budgetLimit)}"
        txtSpentAmount.text = currencyFmt.format(activity.totalSpent)
        txtBudgetStatus.text = "Remaining: ${currencyFmt.format(activity.budgetLimit)}"
        txtEmptyState.text = "No expenses added yet"
    }

    private fun setupFAB() {
        val fab = activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fabAddExpense
        )
        fab?.setOnClickListener {
            showAddExpenseDialog()
        }
    }

    private fun showAddExpenseDialog() {
        val context = requireContext()

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        // Description
        val tilDescription = TextInputLayout(context).apply { hint = "Description" }
        val etDescription = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        tilDescription.addView(etDescription)
        dialogLayout.addView(tilDescription)

        // Amount
        val tilAmount = TextInputLayout(context).apply { hint = "Amount" }
        val etAmount = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilAmount.addView(etAmount)
        dialogLayout.addView(tilAmount)

        // Category label
        val tvCategoryLabel = TextView(context).apply {
            text = "Category"
            setPadding(0, 40, 0, 20)
            setTextColor(android.graphics.Color.parseColor("#666666"))
        }
        dialogLayout.addView(tvCategoryLabel)

        // Category spinner
        val categories = arrayOf("Food", "Transportation", "Accommodation", "Entertainment", "Shopping", "Other")
        val spinnerCategory = Spinner(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_spinner_grey)
        }
        val categoryAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerCategory.adapter = categoryAdapter
        dialogLayout.addView(spinnerCategory)

        // Currency label
        val tvCurrencyLabel = TextView(context).apply {
            text = "Currency"
            setPadding(0, 40, 0, 20)
            setTextColor(android.graphics.Color.parseColor("#666666"))
        }
        dialogLayout.addView(tvCurrencyLabel)

        // Currency spinner
        val spinnerCurrency = Spinner(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_spinner_grey)
        }
        val currencyAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, currencies).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerCurrency.adapter = currencyAdapter
        dialogLayout.addView(spinnerCurrency)

        // Build dialog (IMPORTANT: positive button = null so we can validate without auto-dismiss)
        val dialog = MaterialAlertDialogBuilder(
            ContextThemeWrapper(context, R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor)
        )
            .setTitle("Add Expense")
            .setView(dialogLayout)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnAdd = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            btnAdd.setOnClickListener {
                val description = etDescription.text?.toString()?.trim().orEmpty()
                val amountText = etAmount.text?.toString()?.trim().orEmpty()
                val category = categories[spinnerCategory.selectedItemPosition]
                val selectedCurrency = currencies[spinnerCurrency.selectedItemPosition]

                if (description.isEmpty()) {
                    showMissingFieldDialog("Missing Description", "Please enter a description for your expense.")
                    return@setOnClickListener
                }

                if (amountText.isEmpty()) {
                    showMissingFieldDialog("Missing Amount", "Please enter the cost of your expense.")
                    return@setOnClickListener
                }

                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    showMissingFieldDialog("Invalid Amount", "Amount must be a valid number greater than 0.")
                    return@setOnClickListener
                }

                val amountInUSD =
                    if (selectedCurrency != "USD" && activity.conversionRates.isNotEmpty()) {
                        val rate = activity.conversionRates[selectedCurrency] ?: 1.0
                        amount / rate
                    } else {
                        amount
                    }

                // show confirm dialog (your existing function)
                showConfirmationDialog(description, amount, selectedCurrency, amountInUSD, category)

                dialog.dismiss()
            }
        }

        dialog.show()
    }


    private fun showMissingFieldDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showConfirmationDialog(
        description: String,
        displayAmount: Double,
        currency: String,
        amountInUSD: Double,
        category: String
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Expense")
            .setMessage(
                "Add expense?\n\n" +
                        "Description: $description\n" +
                        "Amount: ${String.format("%.2f", displayAmount)} $currency\n" +
                        "Category: $category"
            )
            .setPositiveButton("OK") { _, _ ->
                addExpenseToFirebase(description, displayAmount, currency, amountInUSD, category)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSortSpinner() {
        val spinner = spinnerSortExpenses ?: return
        val sortOptions = arrayOf(
            "Date (Newest)", "Date (Oldest)", "Amount (Highest)",
            "Amount (Lowest)", "Name (A-Z)", "Name (Z-A)", "Category"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortOption = sortOptions[position]
                applySortAndFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearchBar() {
        val searchBar = etSearchExpenses ?: return
        searchBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearchQuery = s.toString().trim()
                applySortAndFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applySortAndFilter() {
        filteredExpenseList.clear()

        if (currentSearchQuery.isEmpty()) {
            filteredExpenseList.addAll(activity.expenseList)
        } else {
            val query = currentSearchQuery.lowercase()
            for (expense in activity.expenseList) {
                if (expense.description.lowercase().contains(query) ||
                    expense.amount.toString().contains(query) ||
                    expense.category.lowercase().contains(query)) {
                    filteredExpenseList.add(expense)
                }
            }
        }

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

    private fun setupDisplayCurrencySpinner(view: View) {
        val spinnerDisplayCurrency = view.findViewById<Spinner>(R.id.spinnerDisplayCurrency)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDisplayCurrency.adapter = adapter

        spinnerDisplayCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                activity.currentDisplayCurrency = currencies[position]
                updateBudgetDisplay()
                expenseAdapter.notifyDataSetChanged() // Refresh expense list display
                activity.syncTripRemaining()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadExpensesFromFirebase() {
        val userId = activity.auth.currentUser?.uid ?: return
        if (activity.tripId.isBlank()) return

        val baseQuery = activity.db.collection("expenses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("tripId", activity.tripId)

        snapshotListener = baseQuery.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    val code = (error as? FirebaseFirestoreException)?.code
                    if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        Log.w("ExpensesFragment", "Index required. Falling back to unordered query.")
                        loadExpensesWithoutIndex()
                        return@addSnapshotListener
                    }
                    Log.e("ExpensesFragment", "Error loading expenses", error)
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                activity.expenseList.clear()
                activity.totalSpent = 0.0
                for (doc in snapshots.documents) {
                    doc.toObject(Expense::class.java)?.let { exp ->
                        exp.id = doc.id
                        activity.expenseList.add(exp)
                        activity.totalSpent += exp.amount
                    }
                }

                applySortAndFilter()
                updateBudgetDisplay()
            }
    }

    private fun loadExpensesWithoutIndex() {
        snapshotListener?.remove()

        val userId = activity.auth.currentUser?.uid ?: return
        if (activity.tripId.isBlank()) return

        snapshotListener = activity.db.collection("expenses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("tripId", activity.tripId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("ExpensesFragment", "Error loading expenses", error)
                    Toast.makeText(context, "Error loading expenses", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                activity.expenseList.clear()
                activity.totalSpent = 0.0
                for (doc in snapshots.documents) {
                    doc.toObject(Expense::class.java)?.let { exp ->
                        exp.id = doc.id
                        activity.expenseList.add(exp)
                        activity.totalSpent += exp.amount
                    }
                }

                applySortAndFilter()
                updateBudgetDisplay()
            }
    }

    private fun addExpenseToFirebase(
        description: String,
        displayAmount: Double,
        currency: String,
        amount: Double,
        category: String
    ) {
        val userId = activity.auth.currentUser?.uid ?: return

        val newExpense = Expense(
            description = description,
            amount = amount,
            category = category,
            timestamp = System.currentTimeMillis(),
            tripId = activity.tripId,
            userId = userId
        )

        activity.db.collection("expenses").add(
            hashMapOf(
                "description" to newExpense.description,
                "amount" to newExpense.amount,
                "category" to newExpense.category,
                "timestamp" to newExpense.timestamp,
                "tripId" to newExpense.tripId,
                "userId" to newExpense.userId
            )
        ).addOnSuccessListener { docRef ->
            newExpense.id = docRef.id

            // Show undo snackbar
            showUndoSnackbar(newExpense, description, displayAmount, currency)

            if (activity.totalSpent > activity.budgetLimit) {
                Toast.makeText(requireContext(), "Warning: You're over budget!", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to add expense", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUndoSnackbar(expense: Expense, description: String, amount: Double, currency: String) {
        val snackbar = Snackbar.make(
            requireView(),
            "Expense added: $description - ${String.format("%.2f", amount)} $currency",
            Snackbar.LENGTH_LONG
        )

        snackbar.setAction("UNDO") {
            undoExpense(expense)
        }

        snackbar.setActionTextColor(android.graphics.Color.parseColor("#FFD700"))
        snackbar.show()
    }

    private fun undoExpense(expense: Expense) {
        if (expense.id.isNotBlank()) {
            activity.db.collection("expenses").document(expense.id).delete()
                .addOnSuccessListener {
                    Snackbar.make(
                        requireView(),
                        "Expense has been removed",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener {
                    Snackbar.make(
                        requireView(),
                        "Failed to undo",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun setupExpenseList(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewExpenses)
        expenseAdapter = ExpenseAdapter(filteredExpenseList)
        recyclerView.adapter = expenseAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val recyclerView = view?.findViewById<RecyclerView>(R.id.recyclerViewExpenses)

        if (filteredExpenseList.isEmpty()) {
            txtEmptyState.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
            txtEmptyState.text = if (activity.expenseList.isEmpty()) {
                "No expenses added yet"
            } else {
                "No expenses match your search"
            }
        } else {
            txtEmptyState.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }

    private fun updateBudgetDisplay() {
        val displayTotal = activity.convertCurrency(activity.totalSpent, activity.currentDisplayCurrency)
        val displayBudget = activity.convertCurrency(activity.budgetLimit, activity.currentDisplayCurrency)
        val displayRemaining = displayBudget - displayTotal

        val locale = when(activity.currentDisplayCurrency) {
            "EUR" -> Locale.GERMANY
            "GBP" -> Locale.UK
            "JPY" -> Locale.JAPAN
            "MXN" -> Locale("es", "MX")
            "BRL" -> Locale("pt", "BR")
            "ZAR" -> Locale("en", "ZA")
            "SGD" -> Locale("en", "SG")
            "HKD" -> Locale("zh", "HK")
            else -> Locale.US
        }
        val fmt = NumberFormat.getCurrencyInstance(locale)
        if (activity.currentDisplayCurrency != "USD") {
            try {
                val currency = Currency.getInstance(activity.currentDisplayCurrency)
                fmt.currency = currency
            } catch (e: Exception) {
                Log.w("ExpensesFragment", "Currency not supported: ${activity.currentDisplayCurrency}")
            }
        }

        txtSpentAmount.text = fmt.format(displayTotal)
        txtBudgetAmount.text = "/ ${fmt.format(displayBudget)}"
        txtTotalExpenses.text = "Total: ${fmt.format(displayTotal)}"

        if (displayRemaining >= 0) {
            txtBudgetStatus.text = "Remaining: ${fmt.format(displayRemaining)}"
        } else {
            txtBudgetStatus.text = "Over by ${fmt.format(-displayRemaining)}"
        }

        val usedPct = if (activity.budgetLimit > 0) {
            ((activity.totalSpent / activity.budgetLimit) * 100).coerceIn(0.0, 100.0)
        } else 0.0
        progressBudget.progress = usedPct.toInt()

        val color = when {
            displayRemaining < 0 -> android.graphics.Color.parseColor("#FF6B6B")
            displayRemaining <= displayBudget * 0.20 -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#2E5BFF")
        }
        txtBudgetStatus.setTextColor(color)
    }

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

            // Convert amount to display currency
            val displayAmount = activity.convertCurrency(expense.amount, activity.currentDisplayCurrency)
            val locale = when(activity.currentDisplayCurrency) {
                "EUR" -> Locale.GERMANY
                "GBP" -> Locale.UK
                "JPY" -> Locale.JAPAN
                "MXN" -> Locale("es", "MX")
                "BRL" -> Locale("pt", "BR")
                "ZAR" -> Locale("en", "ZA")
                "SGD" -> Locale("en", "SG")
                "HKD" -> Locale("zh", "HK")
                else -> Locale.US
            }
            val fmt = NumberFormat.getCurrencyInstance(locale)
            if (activity.currentDisplayCurrency != "USD") {
                try {
                    val currency = Currency.getInstance(activity.currentDisplayCurrency)
                    fmt.currency = currency
                } catch (e: Exception) {
                    Log.w("ExpensesFragment", "Currency not supported: ${activity.currentDisplayCurrency}")
                }
            }

            holder.txtMeta.text = "${fmt.format(displayAmount)} • ${df.format(Date(expense.timestamp))}"

            holder.btnDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    confirmDeleteExpense(expenses[pos], pos)
                }
            }
        }

        override fun getItemCount() = expenses.size
    }

    private fun confirmDeleteExpense(expense: Expense, position: Int) {
        val displayAmount = activity.convertCurrency(expense.amount, activity.currentDisplayCurrency)

        val locale = when (activity.currentDisplayCurrency) {
            "EUR" -> Locale.GERMANY
            "GBP" -> Locale.UK
            "JPY" -> Locale.JAPAN
            "MXN" -> Locale("es", "MX")
            "BRL" -> Locale("pt", "BR")
            "ZAR" -> Locale("en", "ZA")
            "SGD" -> Locale("en", "SG")
            "HKD" -> Locale("zh", "HK")
            else -> Locale.US
        }

        val fmt = NumberFormat.getCurrencyInstance(locale).apply {
            if (activity.currentDisplayCurrency != "USD") {
                try { currency = Currency.getInstance(activity.currentDisplayCurrency) } catch (_: Exception) {}
            }
        }

        val themedContext = ContextThemeWrapper(
            requireContext(),
            R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor
        )

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setTitle("Delete expense?")
            .setMessage("${expense.description} — ${fmt.format(displayAmount)}")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteExpense(expense, position)

                // optional confirmation like your reviews
                Snackbar.make(requireView(), "Expense deleted", Snackbar.LENGTH_SHORT).show()
            }
            .show()

        // Make Delete red (destructive action) AFTER show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
    }


    private fun deleteExpense(expense: Expense, position: Int) {
        activity.expenseList.remove(expense)
        filteredExpenseList.removeAt(position)
        activity.totalSpent -= expense.amount
        expenseAdapter.notifyItemRemoved(position)
        updateBudgetDisplay()
        updateEmptyState()
        activity.syncTripRemaining()

        if (expense.id.isNotBlank()) {
            activity.db.collection("expenses").document(expense.id).delete()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
    }
}