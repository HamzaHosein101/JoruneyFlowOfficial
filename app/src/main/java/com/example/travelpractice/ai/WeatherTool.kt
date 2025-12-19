package com.example.travelpractice.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherTool {

    private val client = OkHttpClient()


    private val apiKey = "49f0accd729d8e2f257423945e5d15c3"

    suspend fun getWeather(city: String): String = withContext(Dispatchers.IO) {
        try {

            val url =
                "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric"


            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()


            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)


                val main = json.getJSONObject("main")
                val weather = json.getJSONArray("weather").getJSONObject(0)
                val temp = main.getDouble("temp")
                val feelsLike = main.getDouble("feels_like")
                val description = weather.getString("description")
                val humidity = main.getInt("humidity")


                "🌤️ Weather in ${city.replaceFirstChar { it.uppercase() }}:\n\n" +
                        "Temperature: ${temp.toInt()}°C (feels like ${feelsLike.toInt()}°C)\n" +
                        "Conditions: ${description.replaceFirstChar { it.uppercase() }}\n" +
                        "Humidity: ${humidity}%"
            } else {

                when (response.code) {
                    401 -> "❌ Invalid API key. Please check your OpenWeather key."
                    404 -> "⚠️ City '$city' not found. Try again with a valid city name."
                    else -> "⚠️ Unexpected error: ${response.code}. ${response.message}"
                }
            }
        } catch (e: Exception) {
            "❌ Error fetching weather for '$city': ${e.message ?: "Unknown error"}"
        }
    }
}
