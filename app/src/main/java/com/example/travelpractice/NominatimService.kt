package com.example.travelpractice

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

data class NominatimPlace(
    val display_name: String,
    val lat: String,
    val lon: String
)
typealias NominatimResults = List<NominatimPlace>

// ⬇️ Add this for reverse geocoding
data class NominatimReverse(
    val display_name: String?,
    val address: Map<String, String>?
)

interface NominatimService {
    @Headers("User-Agent: JourneyFlow/1.0 (contact: journeyflow100@gmail.com)")
    @GET("search")
    suspend fun search(
        @Query("q") q: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 1
    ): NominatimResults

    // ⬇️ New: lat/lon -> human-readable place
    @Headers("User-Agent: JourneyFlow/1.0 (contact:  journeyflow100@gmail.com)")
    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2"
    ): NominatimReverse
}


