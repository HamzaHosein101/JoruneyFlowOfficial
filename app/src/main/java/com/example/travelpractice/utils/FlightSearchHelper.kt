package com.example.travelpractice.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FlightSearchHelper(
    private val clientId: String,
    private val clientSecret: String
) {
    // ✅ UPDATED: OkHttpClient with increased timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    companion object {
        private const val BASE_URL = "https://test.api.amadeus.com"
        private const val TAG = "FlightSearchHelper"
    }


    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return@withContext accessToken
        }

        try {
            Log.d(TAG, "🔑 Requesting new access token...")

            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/v1/security/oauth2/token")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val tokenResponse = gson.fromJson(responseBody, TokenResponse::class.java)
                accessToken = tokenResponse.accessToken
                tokenExpiry = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000) - 60000
                Log.d(TAG, "✅ Access token obtained successfully")
                return@withContext accessToken
            } else {
                Log.e(TAG, "❌ Token request failed: ${response.code} - $responseBody")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting access token: ${e.message}", e)
            return@withContext null
        }
    }


    suspend fun searchCityCode(cityName: String): String? = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            if (token == null) {
                Log.e(TAG, "Could not get access token for city search")
                return@withContext null
            }

            Log.d(TAG, "🔍 Searching for city code: $cityName")

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("test.api.amadeus.com")
                .addPathSegment("v1")
                .addPathSegment("reference-data")
                .addPathSegment("locations")
                .addPathSegment("cities")
                .addQueryParameter("keyword", cityName)
                .addQueryParameter("max", "5")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Log.d(TAG, "City search response: ${responseBody.take(200)}")

                val cityResponse = gson.fromJson(responseBody, CitySearchResponse::class.java)

                if (cityResponse.data.isNullOrEmpty()) {
                    Log.d(TAG, "No cities found for: $cityName")
                    return@withContext null
                }

                val firstCity = cityResponse.data.firstOrNull()
                if (firstCity != null) {
                    Log.d(TAG, "✅ Found city: ${firstCity.name} -> ${firstCity.iataCode}")
                    return@withContext firstCity.iataCode
                } else {
                    Log.d(TAG, "No cities found for: $cityName")
                    return@withContext null
                }
            } else {
                Log.e(TAG, "❌ City search failed: ${response.code} - $responseBody")
                return@withContext null
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "⏱️ City search timeout for: $cityName", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching for city code: ${e.message}", e)
            return@withContext null
        }
    }


    suspend fun searchAirportCode(locationName: String): String? = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            if (token == null) {
                Log.e(TAG, "Could not get access token for airport search")
                return@withContext null
            }

            Log.d(TAG, "🔍 Searching for airport code: $locationName")

            val cleanLocation = locationName.trim()

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("test.api.amadeus.com")
                .addPathSegment("v1")
                .addPathSegment("reference-data")
                .addPathSegment("locations")
                .addQueryParameter("keyword", cleanLocation)
                .addQueryParameter("subType", "AIRPORT,CITY")
                .addQueryParameter("page[limit]", "10")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Log.d(TAG, "Airport search response: ${responseBody.take(300)}")

                val airportResponse = gson.fromJson(responseBody, AirportSearchResponse::class.java)

                if (airportResponse.data.isNullOrEmpty()) {
                    Log.d(TAG, "No locations found for: $locationName")
                    return@withContext null
                }

                // Try to find best match
                val exactMatch = airportResponse.data.firstOrNull {
                    it.iataCode.equals(cleanLocation, ignoreCase = true)
                }
                if (exactMatch != null) {
                    Log.d(TAG, "✅ Found exact code match: ${exactMatch.name} -> ${exactMatch.iataCode}")
                    return@withContext exactMatch.iataCode
                }

                val airportMatch = airportResponse.data.firstOrNull {
                    it.subType == "AIRPORT" &&
                            it.address?.cityName?.contains(cleanLocation, ignoreCase = true) == true
                }
                if (airportMatch != null) {
                    Log.d(TAG, "✅ Found airport match: ${airportMatch.name} -> ${airportMatch.iataCode}")
                    return@withContext airportMatch.iataCode
                }

                val cityMatch = airportResponse.data.firstOrNull {
                    it.name.contains(cleanLocation, ignoreCase = true) ||
                            it.address?.cityName?.contains(cleanLocation, ignoreCase = true) == true
                }
                if (cityMatch != null) {
                    Log.d(TAG, "✅ Found city match: ${cityMatch.name} -> ${cityMatch.iataCode}")
                    return@withContext cityMatch.iataCode
                }

                val firstResult = airportResponse.data.firstOrNull()
                if (firstResult != null) {
                    Log.d(TAG, "✅ Using first result: ${firstResult.name} -> ${firstResult.iataCode}")
                    return@withContext firstResult.iataCode
                }

                return@withContext null
            } else {
                Log.e(TAG, "❌ Airport search failed: ${response.code} - $responseBody")
                return@withContext null
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "⏱️ Airport search timeout for: $locationName", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching for airport code: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun searchFlights(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String? = null,
        adults: Int = 1,
        maxResults: Int = 5,
        travelClass: String? = null,
        preferredAirline: String? = null,
        maxPrice: Int? = null,
        nonStop: Boolean? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "✈️ Searching flights: $origin -> $destination on $departureDate")

            val token = getAccessToken()
            if (token == null) {
                return@withContext "❌ Could not authenticate with flight service. Please try again."
            }

            val urlBuilder = HttpUrl.Builder()
                .scheme("https")
                .host("test.api.amadeus.com")
                .addPathSegment("v2")
                .addPathSegment("shopping")
                .addPathSegment("flight-offers")
                .addQueryParameter("originLocationCode", origin.uppercase())
                .addQueryParameter("destinationLocationCode", destination.uppercase())
                .addQueryParameter("departureDate", departureDate)
                .addQueryParameter("adults", adults.toString())
                .addQueryParameter("max", maxResults.toString())
                .addQueryParameter("currencyCode", "USD")

            returnDate?.let { urlBuilder.addQueryParameter("returnDate", it) }
            travelClass?.let { urlBuilder.addQueryParameter("travelClass", it.uppercase()) }
            nonStop?.let { urlBuilder.addQueryParameter("nonStop", it.toString()) }
            maxPrice?.let { urlBuilder.addQueryParameter("maxPrice", it.toString()) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            Log.d(TAG, "🔄 Making API request...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Log.d(TAG, "✅ Flight search successful!")
                val flightResponse = gson.fromJson(responseBody, FlightSearchResponse::class.java)

                if (flightResponse.data.isNullOrEmpty()) {
                    return@withContext "❌ No flights found from $origin to $destination on $departureDate"
                }

                val filteredFlights = if (preferredAirline != null) {
                    flightResponse.data.filter { offer ->
                        offer.itineraries.any { itinerary ->
                            itinerary.segments.any { segment ->
                                segment.carrierCode.equals(preferredAirline, ignoreCase = true)
                            }
                        }
                    }
                } else {
                    flightResponse.data
                }

                return@withContext formatFlightResults(
                    flightResponse.copy(data = filteredFlights),
                    origin,
                    destination,
                    returnDate != null,
                    travelClass,
                    departureDate,
                    returnDate
                )
            } else {
                Log.e(TAG, "❌ Flight search failed: ${response.code} - $responseBody")
                return@withContext "❌ Could not find flights. Please check your locations and dates."
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "⏱️ Flight search timeout", e)
            return@withContext "⏱️ Search timed out. The flight service is taking too long to respond.\n\nPlease try again in a moment."
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching flights: ${e.message}", e)
            return@withContext "❌ Error searching for flights: ${e.message}"
        }
    }


    private fun formatFlightResults(
        response: FlightSearchResponse,
        origin: String,
        destination: String,
        isRoundTrip: Boolean,
        travelClass: String?,
        departureDate: String,
        returnDate: String?
    ): String {
        if (response.data.isNullOrEmpty()) {
            return "No flights found from $origin to $destination"
        }

        return buildString {
            append("✈️ Found ${response.data.size} flight${if (response.data.size > 1) "s" else ""}")
            append(" from $origin to $destination")

            if (isRoundTrip) append(" (Round-trip)")
            travelClass?.let { append(" - ${it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }}") }
            append(":\n\n")

            response.data.take(5).forEachIndexed { index, offer ->
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("${index + 1}. ")

                offer.itineraries.forEachIndexed { itinIndex, itinerary ->
                    if (itinIndex > 0) append("\n   ")

                    val segments = itinerary.segments
                    val firstSegment = segments.first()
                    val lastSegment = segments.last()

                    if (isRoundTrip) {
                        append(if (itinIndex == 0) "➡️ Outbound: " else "⬅️ Return: ")
                    }

                    val airlineName = getAirlineName(firstSegment.carrierCode)
                    append("$airlineName (${firstSegment.carrierCode} ${firstSegment.number})")

                    val stops = segments.size - 1
                    if (stops > 0) {
                        append(" • $stops stop${if (stops > 1) "s" else ""}")
                    } else {
                        append(" • Direct ✨")
                    }
                    append("\n")

                    append("   🛫 ${formatDateTime(firstSegment.departure.at)}")
                    append(" → 🛬 ${formatDateTime(lastSegment.arrival.at)}\n")
                    append("   ⏱️ ${formatDuration(itinerary.duration)}")

                    offer.numberOfBookableSeats?.let {
                        append(" • 🪑 $it seat${if (it > 1) "s" else ""} left")
                    }
                    append("\n")
                }

                append("\n")

                val googleFlightsUrl = buildGoogleFlightsUrl(origin, destination, departureDate, returnDate)
                val kayakUrl = buildKayakUrl(origin, destination, departureDate, returnDate)

                append("🔗 Compare prices:\n")
                append("$googleFlightsUrl\n\n")
                append("$kayakUrl\n\n")
            }

            append("━━━━━━━━━━━━━━━━━━━━\n\n")

            val fastest = response.data.minByOrNull { offer ->
                offer.itineraries.sumOf { itinerary -> parseDuration(itinerary.duration) }
            }
            fastest?.let {
                val totalDuration = it.itineraries.sumOf { itinerary -> parseDuration(itinerary.duration) }
                append("⚡ Fastest: ${formatMinutes(totalDuration)}\n")
            }

            append("\n📌 Tap any link above to book your flight!")
        }
    }


    suspend fun searchHotels(
        cityCode: String,
        checkInDate: String,
        checkOutDate: String,
        adults: Int = 1,
        radius: Int = 20,
        radiusUnit: String = "KM",
        maxResults: Int = 5
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🏨 Searching hotels in $cityCode from $checkInDate to $checkOutDate")

            val token = getAccessToken()
            if (token == null) {
                return@withContext "❌ Could not authenticate with hotel service. Please try again."
            }

            // First, get hotel IDs by city
            val searchUrl = HttpUrl.Builder()
                .scheme("https")
                .host("test.api.amadeus.com")
                .addPathSegment("v1")
                .addPathSegment("reference-data")
                .addPathSegment("locations")
                .addPathSegment("hotels")
                .addPathSegment("by-city")
                .addQueryParameter("cityCode", cityCode.uppercase())
                .addQueryParameter("radius", radius.toString())
                .addQueryParameter("radiusUnit", radiusUnit)
                .build()

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            Log.d(TAG, "🔄 Fetching hotel list...")
            val searchResponse = client.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string()

            if (!searchResponse.isSuccessful || searchBody == null) {
                Log.e(TAG, "❌ Hotel search failed: ${searchResponse.code}")
                return@withContext """
                    ❌ Could not find hotels in $cityCode
                    
                    Try searching for:
                    • Major cities (Paris, London, New York)
                    • Tourist destinations (Barcelona, Amsterdam)
                """.trimIndent()
            }

            val hotelListResponse = gson.fromJson(searchBody, HotelListResponse::class.java)

            if (hotelListResponse.data.isNullOrEmpty()) {
                Log.d(TAG, "No hotels found in $cityCode")
                return@withContext """
                    ❌ No hotels found in $cityCode
                    
                    This could be because:
                    • The city code might not be recognized
                    • Try a major city nearby
                    
                    Popular destinations:
                    • Paris, France
                    • London, England
                    • New York, USA
                    • Barcelona, Spain
                """.trimIndent()
            }

            Log.d(TAG, "✅ Found ${hotelListResponse.data.size} hotels")

            // Get hotel IDs (limit to first 10)
            val hotelIds = hotelListResponse.data.take(10).map { it.hotelId }.joinToString(",")

            // Now get hotel offers with prices
            val offersUrl = HttpUrl.Builder()
                .scheme("https")
                .host("test.api.amadeus.com")
                .addPathSegment("v3")
                .addPathSegment("shopping")
                .addPathSegment("hotel-offers")
                .addQueryParameter("hotelIds", hotelIds)
                .addQueryParameter("checkInDate", checkInDate)
                .addQueryParameter("checkOutDate", checkOutDate)
                .addQueryParameter("adults", adults.toString())
                .addQueryParameter("roomQuantity", "1")
                .addQueryParameter("currency", "USD")
                .build()

            val offersRequest = Request.Builder()
                .url(offersUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            Log.d(TAG, "🔄 Fetching hotel offers...")
            val offersResponse = client.newCall(offersRequest).execute()
            val offersBody = offersResponse.body?.string()

            if (offersResponse.isSuccessful && offersBody != null) {
                Log.d(TAG, "✅ Got hotel offers successfully")
                val hotelOffersResponse = gson.fromJson(offersBody, HotelOffersResponse::class.java)

                if (hotelOffersResponse.data.isNullOrEmpty()) {
                    return@withContext "❌ No hotel offers available for $cityCode on these dates"
                }

                return@withContext formatHotelResults(hotelOffersResponse, cityCode, checkInDate, checkOutDate)
            } else {
                Log.e(TAG, "❌ Hotel offers failed: ${offersResponse.code}")
                return@withContext "❌ Could not get hotel prices. Please try again."
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "⏱️ Hotel search timeout", e)
            return@withContext "⏱️ Search timed out. The hotel service is taking too long to respond.\n\nPlease try again in a moment."
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching hotels: ${e.message}", e)
            return@withContext "❌ Error searching for hotels: ${e.message}\n\nPlease check your internet connection and try again."
        }
    }


    private fun formatHotelResults(
        response: HotelOffersResponse,
        cityCode: String,
        checkInDate: String,
        checkOutDate: String
    ): String {
        if (response.data.isNullOrEmpty()) {
            return "No hotels found in $cityCode for the selected dates"
        }

        val nights = calculateNights(checkInDate, checkOutDate)

        return buildString {
            append("🏨 Found ${response.data.size} hotel${if (response.data.size > 1) "s" else ""} in $cityCode:\n\n")

            response.data.take(5).forEachIndexed { index, hotel ->
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("${index + 1}. ${hotel.hotel.name}\n")

                hotel.hotel.rating?.let {
                    append("   ⭐ $it-star hotel\n")
                }

                hotel.hotel.cityCode?.let {
                    append("   📍 $it")
                }
                hotel.hotel.address?.countryCode?.let {
                    append(", $it")
                }
                append("\n")

                if (hotel.offers.isNotEmpty()) {
                    val bestOffer = hotel.offers.minByOrNull {
                        it.price.total.toDoubleOrNull() ?: Double.MAX_VALUE
                    }

                    bestOffer?.let { offer ->
                        append("   🗓️ $nights night${if (nights > 1) "s" else ""}\n")
                        append("   🛏️ ${offer.room.typeEstimated?.category ?: "Standard Room"}\n")

                        offer.room.typeEstimated?.beds?.let { beds ->
                            append("   🛌 $beds bed${if (beds > 1) "s" else ""}\n")
                        }

                        if (offer.room.description?.text != null) {
                            val description = offer.room.description.text
                            if (description.length > 80) {
                                append("   ℹ️ ${description.take(77)}...\n")
                            } else {
                                append("   ℹ️ $description\n")
                            }
                        }
                    }
                }

                val hotelName = hotel.hotel.name.replace(" ", "+")
                val bookingUrl = "https://www.booking.com/search.html?ss=$hotelName&checkin=$checkInDate&checkout=$checkOutDate"
                val hotelsUrl = "https://www.hotels.com/search.do?q-destination=$hotelName&q-check-in=$checkInDate&q-check-out=$checkOutDate"

                append("\n🔗 Book this hotel:\n")
                append("$bookingUrl\n\n")
                append("$hotelsUrl\n\n")
            }

            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            append("📌 Tap any link above to book your hotel!")
        }
    }

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


    private fun buildGoogleFlightsUrl(origin: String, destination: String, departureDate: String, returnDate: String?): String {
        val baseUrl = "https://www.google.com/travel/flights"
        return if (returnDate != null) {
            "$baseUrl?q=flights+from+$origin+to+$destination+on+$departureDate+returning+$returnDate"
        } else {
            "$baseUrl?q=flights+from+$origin+to+$destination+on+$departureDate"
        }
    }

    private fun buildKayakUrl(origin: String, destination: String, departureDate: String, returnDate: String?): String {
        val formattedDeparture = departureDate.replace("-", "")
        return if (returnDate != null) {
            val formattedReturn = returnDate.replace("-", "")
            "https://www.kayak.com/flights/$origin-$destination/$formattedDeparture/$formattedReturn"
        } else {
            "https://www.kayak.com/flights/$origin-$destination/$formattedDeparture"
        }
    }



    private fun getAirlineName(code: String): String {
        return when (code.uppercase()) {
            "AA" -> "American Airlines"
            "DL" -> "Delta Air Lines"
            "UA" -> "United Airlines"
            "WN" -> "Southwest Airlines"
            "B6" -> "JetBlue Airways"
            "AS" -> "Alaska Airlines"
            "NK" -> "Spirit Airlines"
            "F9" -> "Frontier Airlines"
            "G4" -> "Allegiant Air"
            "LH" -> "Lufthansa"
            "BA" -> "British Airways"
            "AF" -> "Air France"
            "KL" -> "KLM"
            "EK" -> "Emirates"
            "QR" -> "Qatar Airways"
            "SQ" -> "Singapore Airlines"
            "CX" -> "Cathay Pacific"
            "NH" -> "ANA"
            "JL" -> "Japan Airlines"
            "AC" -> "Air Canada"
            "QF" -> "Qantas"
            "EY" -> "Etihad Airways"
            "TK" -> "Turkish Airlines"
            "VS" -> "Virgin Atlantic"
            else -> code
        }
    }

    private fun parseDuration(duration: String): Int {
        val hours = Regex("(\\d+)H").find(duration)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)M").find(duration)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (hours * 60) + minutes
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (mins > 0) append("${mins}m")
        }.trim()
    }

    private fun formatDuration(duration: String): String {
        val hours = Regex("(\\d+)H").find(duration)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)M").find(duration)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m")
        }.trim()
    }

    private fun formatDateTime(isoDateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val date = inputFormat.parse(isoDateTime)
            date?.let { outputFormat.format(it) } ?: isoDateTime
        } catch (e: Exception) {
            isoDateTime
        }
    }



    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresIn: Int
    )

    data class CitySearchResponse(val data: List<CityData>?)
    data class CityData(
        val name: String,
        val iataCode: String,
        val address: CityAddress?
    )
    data class CityAddress(
        val cityName: String?,
        val countryName: String?
    )

    data class AirportSearchResponse(val data: List<AirportData>?)
    data class AirportData(
        val name: String,
        val iataCode: String,
        val subType: String?,
        val address: AirportAddress?
    )
    data class AirportAddress(
        val cityName: String?,
        val countryName: String?
    )

    data class FlightSearchResponse(val data: List<FlightOffer>?)
    data class FlightOffer(
        val price: Price,
        val itineraries: List<Itinerary>,
        val numberOfBookableSeats: Int?
    )
    data class Price(val total: String, val currency: String)
    data class Itinerary(val duration: String, val segments: List<Segment>)
    data class Segment(
        val departure: FlightPoint,
        val arrival: FlightPoint,
        val carrierCode: String,
        val number: String,
        val duration: String
    )
    data class FlightPoint(val iataCode: String, val at: String)

    data class HotelListResponse(val data: List<HotelData>?)
    data class HotelData(val hotelId: String, val name: String)

    data class HotelOffersResponse(val data: List<HotelOffer>?)
    data class HotelOffer(val hotel: Hotel, val offers: List<Offer>)

    data class Hotel(
        val name: String,
        val hotelId: String,
        val rating: Int?,
        val cityCode: String?,
        val address: HotelAddress?
    )

    data class HotelAddress(val countryCode: String?)

    data class Offer(
        val id: String,
        val price: Price,
        val room: Room
    )

    data class Room(
        val type: String?,
        val typeEstimated: RoomTypeEstimated?,
        val description: RoomDescription?
    )

    data class RoomTypeEstimated(
        val category: String?,
        val beds: Int?
    )

    data class RoomDescription(val text: String?)
}