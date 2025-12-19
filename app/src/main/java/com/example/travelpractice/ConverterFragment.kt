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
import android.widget.ListPopupWindow
import android.graphics.drawable.ColorDrawable
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class ConverterFragment : Fragment() {

    private lateinit var activity: ExpenseTrackerActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_converter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity = requireActivity() as ExpenseTrackerActivity

        setupCurrencyConverter(view)
        setupRefreshButton(view)
    }

    private fun setSpinnerPopupBackground(spinner: Spinner) {
        try {
            val popup = Spinner::class.java.getDeclaredField("mPopup")
            popup.isAccessible = true
            val popupWindow = popup.get(spinner) as? ListPopupWindow
            popupWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
        } catch (e: Exception) {
            Log.w("ConverterFragment", "Could not set spinner popup background", e)
        }
    }

    private fun setupCurrencyConverter(view: View) {
        val etAmount = view.findViewById<TextInputEditText>(R.id.etAmount)
        val spinnerFromCurrency = view.findViewById<Spinner>(R.id.spinnerFromCurrency)
        val spinnerToCurrency = view.findViewById<Spinner>(R.id.spinnerToCurrency)
        val txtConvertedAmount = view.findViewById<TextView>(R.id.txtConvertedAmount)

        val currencies = arrayOf(
            "USD - US Dollar", "EUR - Euro", "GBP - British Pound", "JPY - Japanese Yen",
            "CAD - Canadian Dollar", "AUD - Australian Dollar", "CHF - Swiss Franc",
            "CNY - Chinese Yuan", "INR - Indian Rupee", "KRW - South Korean Won",
            "MXN - Mexican Peso", "BRL - Brazilian Real", "ZAR - South African Rand",
            "SGD - Singapore Dollar", "HKD - Hong Kong Dollar"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

        spinnerFromCurrency.adapter = adapter
        spinnerToCurrency.adapter = adapter


        setSpinnerPopupBackground(spinnerFromCurrency)
        setSpinnerPopupBackground(spinnerToCurrency)

        spinnerFromCurrency.setSelection(0) // USD
        spinnerToCurrency.setSelection(1) // EUR

        fun convertCurrencyInConverter() {
            val amountText = etAmount.text.toString()
            if (amountText.isNotEmpty()) {
                try {
                    val amount = amountText.toDouble()
                    val fromCurrency = currencies[spinnerFromCurrency.selectedItemPosition].substring(0, 3)
                    val toCurrency = currencies[spinnerToCurrency.selectedItemPosition].substring(0, 3)

                    if (activity.conversionRates.isNotEmpty()) {
                        val fromRate = activity.conversionRates[fromCurrency] ?: 1.0
                        val toRate = activity.conversionRates[toCurrency] ?: 1.0
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

    private fun setupRefreshButton(view: View) {
        val btnRefreshRates = view.findViewById<MaterialButton>(R.id.btnRefreshRates)
        btnRefreshRates.setOnClickListener {
            Snackbar.make(
                requireView(),
                "Exchange rates refreshed",
                Snackbar.LENGTH_SHORT
            ).show()

            activity.loadExchangeRates()
        }
    }
}