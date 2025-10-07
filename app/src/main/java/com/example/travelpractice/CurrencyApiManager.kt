package com.example.travelpractice

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Data model for API response
data class CurrencyApiResponse(
    val result: String,
    val base_code: String,
    val conversion_rates: Map<String, Double>
)

// Retrofit interface for the API
interface CurrencyApiService {
    @GET("latest/{apiKey}")
    fun getExchangeRates(
        @Path("apiKey") apiKey: String,
        @Query("base_code") baseCurrency: String = "USD"
    ): Call<CurrencyApiResponse>
}

// Currency API manager
class CurrencyApiManager {

    // This interface is OUTSIDE the companion object so it’s accessible from anywhere
    interface ExchangeRateCallback {
        fun onSuccess(rates: Map<String, Double>)
        fun onFailure(error: String)
    }

    companion object {
        private const val BASE_URL = "https://v6.exchangerate-api.com/v6/"
        private const val API_KEY = "3bbd484662f45996d3da60b1b607bb02" // Replace with your API key

        private val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        private val apiService = retrofit.create(CurrencyApiService::class.java)

        // Fetch exchange rates from the API
        fun fetchExchangeRates(callback: ExchangeRateCallback) {
            val call = apiService.getExchangeRates(API_KEY)

            call.enqueue(object : Callback<CurrencyApiResponse> {
                override fun onResponse(
                    call: Call<CurrencyApiResponse>,
                    response: Response<CurrencyApiResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.result == "success" && body.conversion_rates.isNotEmpty()) {
                            Log.d("CurrencyApiManager", "Fetched ${body.conversion_rates.size} rates")
                            callback.onSuccess(body.conversion_rates)
                        } else {
                            Log.e("CurrencyApiManager", "API returned error: ${body.result}")
                            callback.onFailure("API error: ${body.result}")
                        }
                    } else {
                        Log.e("CurrencyApiManager", "Response not successful: ${response.code()}")
                        callback.onFailure("Response not successful: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<CurrencyApiResponse>, t: Throwable) {
                    Log.e("CurrencyApiManager", "API request failed: ${t.message}")
                    callback.onFailure(t.message ?: "Unknown error")
                }
            })
        }
    }
}
