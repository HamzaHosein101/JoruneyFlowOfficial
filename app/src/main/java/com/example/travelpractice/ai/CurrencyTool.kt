package com.example.travelpractice.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class CurrencyTool {
    private val client = OkHttpClient()

    suspend fun convertCurrency(message: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val parts = message.split(" ")
                val amountIndex = parts.indexOfFirst { it.toDoubleOrNull() != null }

                if (amountIndex == -1) {
                    return@withContext "💱 To convert currency, please use format: 'Convert 100 USD to EUR'"
                }

                val amount = parts[amountIndex].toDouble()
                val fromCurrency = parts.getOrNull(amountIndex + 1)?.uppercase() ?: "USD"
                val toCurrency = parts.lastOrNull()?.uppercase() ?: "EUR"

                val url = "https://api.exchangerate-api.com/v4/latest/$fromCurrency"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val rates = json.getJSONObject("rates")
                    val rate = rates.getDouble(toCurrency)
                    val converted = amount * rate

                    "💱 Currency Conversion:\n\n" +
                            "$amount $fromCurrency = ${String.format("%.2f", converted)} $toCurrency\n" +
                            "Exchange rate: 1 $fromCurrency = ${String.format("%.4f", rate)} $toCurrency"
                } else {
                    "Sorry, I couldn't convert that currency. Supported: USD, EUR, GBP, JPY, AUD, CAD, etc."
                }
            } catch (e: Exception) {
                "💱 Currency format: 'Convert 100 USD to EUR'\n\nError: ${e.message}"
            }
        }
    }
}