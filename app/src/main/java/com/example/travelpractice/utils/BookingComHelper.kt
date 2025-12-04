package com.example.travelpractice.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.text.SimpleDateFormat
import java.util.*

class BookingComHelper(
    private val rapidApiKey: String
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://booking-com.p.rapidapi.com"
        private const val TAG = "BookingComHelper"
    }

    /**
     * Search for hotels in a city
     */
    suspend fun searchHotels(
        cityName: String,
        checkInDate: String,
        checkOutDate: String,
        adults: Int = 2,
        rooms: Int = 1,
        currency: String = "USD",
        locale: String = "en-us"
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching hotels in $cityName from $checkInDate to $checkOutDate")

            // Step 1: Get destination ID
            val destId = searchDestination(cityName)
            if (destId == null) {
                return@withContext "❌ Could not find destination: $cityName. Please try another city."
            }

            Log.d(TAG, "Found destination ID: $destId")

            // Step 2: Search hotels
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("booking-com.p.rapidapi.com")
                .addPathSegment("v1")
                .addPathSegment("hotels")
                .addPathSegment("search")
                .addQueryParameter("dest_id", destId)
                .addQueryParameter("dest_type", "city")
                .addQueryParameter("checkin_date", checkInDate)
                .addQueryParameter("checkout_date", checkOutDate)
                .addQueryParameter("adults_number", adults.toString())
                .addQueryParameter("room_number", rooms.toString())
                .addQueryParameter("units", "metric")
                .addQueryParameter("page_number", "0")
                .addQueryParameter("filter_by_currency", currency)
                .addQueryParameter("locale", locale)
                .addQueryParameter("order_by", "popularity")
                .addQueryParameter("categories_filter_ids", "class::2,class::4,free_cancellation::1")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-RapidAPI-Key", rapidApiKey)
                .addHeader("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Log.d(TAG, "Search successful, parsing results...")
                val searchResponse = gson.fromJson(responseBody, HotelSearchResponse::class.java)
                return@withContext formatHotelResults(searchResponse, cityName, checkInDate, checkOutDate)
            } else {
                Log.e(TAG, "Hotel search failed: ${response.code} - $responseBody")
                return@withContext "❌ Could not find hotels in $cityName. Error: ${response.code}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching hotels", e)
            return@withContext "❌ Error searching for hotels: ${e.message}"
        }
    }

    /**
     * Search for destination ID by city name
     */
    private suspend fun searchDestination(cityName: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("booking-com.p.rapidapi.com")
                .addPathSegment("v1")
                .addPathSegment("hotels")
                .addPathSegment("locations")
                .addQueryParameter("name", cityName)
                .addQueryParameter("locale", "en-us")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-RapidAPI-Key", rapidApiKey)
                .addHeader("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val locations = gson.fromJson(responseBody, Array<DestinationLocation>::class.java)
                // Return the first city destination
                return@withContext locations.firstOrNull { it.dest_type == "city" }?.dest_id
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching destination", e)
            return@withContext null
        }
    }

    /**
     * Get detailed hotel information
     */
    suspend fun getHotelDetails(hotelId: String): String = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("booking-com.p.rapidapi.com")
                .addPathSegment("v1")
                .addPathSegment("hotels")
                .addPathSegment("data")
                .addQueryParameter("hotel_id", hotelId)
                .addQueryParameter("locale", "en-us")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-RapidAPI-Key", rapidApiKey)
                .addHeader("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val details = gson.fromJson(responseBody, HotelDetails::class.java)
                return@withContext formatHotelDetails(details)
            } else {
                return@withContext "Could not get hotel details"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting hotel details", e)
            return@withContext "Error: ${e.message}"
        }
    }

    /**
     * Format hotel search results
     */
    private fun formatHotelResults(
        response: HotelSearchResponse,
        cityName: String,
        checkInDate: String,
        checkOutDate: String
    ): String {
        val hotels = response.result ?: return "No hotels found in $cityName"

        if (hotels.isEmpty()) {
            return "No hotels found in $cityName"
        }

        val nights = calculateNights(checkInDate, checkOutDate)

        return buildString {
            append("🏨 Found ${hotels.size} hotels in $cityName:\n\n")

            hotels.take(5).forEachIndexed { index, hotel ->
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("${index + 1}. ${hotel.hotel_name}\n")

                // Star rating
                hotel.class_?.let { stars ->
                    append("   ⭐ ${stars}-star hotel\n")
                }

                // Location/District
                hotel.district?.let {
                    append("   📍 $it")
                }
                hotel.city?.let {
                    if (hotel.district == null) append("   📍 ")
                    append(", $it")
                }
                hotel.country_trans?.let {
                    append(", $it")
                }
                append("\n")

                // Review score
                hotel.review_score?.let { score ->
                    val rating = score.toDoubleOrNull()
                    if (rating != null && rating > 0) {
                        val ratingText = when {
                            rating >= 9.0 -> "Superb"
                            rating >= 8.0 -> "Very Good"
                            rating >= 7.0 -> "Good"
                            rating >= 6.0 -> "Pleasant"
                            else -> "Fair"
                        }
                        append("   ⭐ $rating/10 - $ratingText")
                        hotel.review_nr?.let { reviews ->
                            append(" ($reviews reviews)")
                        }
                        append("\n")
                    }
                }

                // Price
                hotel.min_total_price?.let { price ->
                    val currency = hotel.currency_code ?: "USD"
                    append("   💰 From $currency ${String.format("%.2f", price)}")
                    append(" ($nights night${if (nights > 1) "s" else ""})\n")
                }

                // Amenities/Facilities
                if (!hotel.hotel_facilities.isNullOrEmpty()) {
                    val topFacilities = hotel.hotel_facilities.take(3).joinToString(", ")
                    append("   ✨ $topFacilities\n")
                }

                // Free cancellation
                if (hotel.is_free_cancellable == 1) {
                    append("   ✅ Free cancellation\n")
                }

                // Distance from center
                hotel.distance?.let { dist ->
                    val distKm = dist.toDoubleOrNull()
                    if (distKm != null && distKm < 10) {
                        append("   🚶 ${String.format("%.1f", distKm)} km from center\n")
                    }
                }

                append("\n")

                // Booking link
                append("   🔗 Book now:\n")
                val bookingUrl = "https://www.booking.com/hotel/${hotel.country_trans?.lowercase()?.replace(" ", "-")}/${hotel.hotel_name?.lowercase()?.replace(" ", "-")}.html?checkin=$checkInDate&checkout=$checkOutDate"
                append("   $bookingUrl\n\n")
            }

            append("━━━━━━━━━━━━━━━━━━━━\n\n")

            // Find cheapest hotel
            val cheapest = hotels.minByOrNull { it.min_total_price ?: Double.MAX_VALUE }
            cheapest?.let {
                it.min_total_price?.let { price ->
                    val currency = it.currency_code ?: "USD"
                    append("💡 Best deal: $currency ${String.format("%.2f", price)} for $nights night${if (nights > 1) "s" else ""}\n")
                }
            }

            // Find highest rated
            val highestRated = hotels.filter { it.review_score != null }
                .maxByOrNull { it.review_score?.toDoubleOrNull() ?: 0.0 }
            highestRated?.let {
                it.review_score?.let { score ->
                    append("🌟 Highest rated: ${it.hotel_name} ($score/10)\n")
                }
            }

            append("\n📌 Tip: Free cancellation available on selected properties!")
        }
    }

    /**
     * Format hotel details
     */
    private fun formatHotelDetails(details: HotelDetails): String {
        return buildString {
            append("🏨 ${details.hotel_name}\n\n")

            details.hotel_description?.let {
                append("📝 $it\n\n")
            }

            details.address?.let {
                append("📍 $it\n")
            }

            details.hotel_facilities?.let { facilities ->
                append("\n✨ Facilities:\n")
                facilities.take(10).forEach { facility ->
                    append("  • ${facility.name}\n")
                }
            }
        }
    }

    /**
     * Calculate number of nights
     */
    private fun calculateNights(checkIn: String, checkOut: String): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val checkInDate = format.parse(checkIn)
            val checkOutDate = format.parse(checkOut)
            val diff = checkOutDate!!.time - checkInDate!!.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            1
        }
    }

    // ==================== DATA CLASSES ====================

    data class DestinationLocation(
        val dest_id: String,
        val dest_type: String,
        val label: String,
        val city_name: String?
    )

    data class HotelSearchResponse(
        val result: List<Hotel>?
    )

    data class Hotel(
        val hotel_id: String?,
        val hotel_name: String?,
        @SerializedName("class") val class_: Int?,
        val review_score: String?,
        val review_nr: Int?,
        val min_total_price: Double?,
        val currency_code: String?,
        val distance: String?,
        val district: String?,
        val city: String?,
        val country_trans: String?,
        val hotel_facilities: List<String>?,
        val is_free_cancellable: Int?,
        val address: String?,
        val url: String?
    )

    data class HotelDetails(
        val hotel_name: String?,
        val hotel_description: String?,
        val address: String?,
        val hotel_facilities: List<Facility>?
    )

    data class Facility(
        val name: String,
        val facility_type_id: Int
    )
}