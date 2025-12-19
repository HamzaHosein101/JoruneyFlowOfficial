package com.example.travelpractice
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.travelpractice.model.Trip
import com.example.travelpractice.ui.checklist.BagChecklistActivity
import com.example.travelpractice.weather.HttpClients
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.async



class TripDetailActivity : AppCompatActivity() {


    private fun owWeatherEmoji(id: Int?): String = when (id) {
        in 200..232 -> "⛈️" // Thunderstorm
        in 300..321 -> "🌦️" // Drizzle
        in 500..531 -> "🌧️" // Rain
        in 600..622 -> "🌨️" // Snow
        in 701..781 -> "🌫️" // Atmosphere (fog, mist, etc.)
        800 -> "☀️" // Clear
        801 -> "🌤️" // Few clouds
        802 -> "⛅" // Scattered clouds
        803, 804 -> "☁️" // Broken/overcast clouds
        else -> "🌡️"
    }

    private fun groupForecastByDay(items: List<OWForecastItem>): Map<String, List<OWForecastItem>> {
        return items.groupBy { item ->
            item.dt_txt?.substring(0, 10) ?: ""
        }.filterKeys { it.isNotBlank() }
    }

    private fun getDailyEmoji(items: List<OWForecastItem>): String {
        var hasThunder = false
        var hasSnow = false
        var hasRain = false
        var maxPop = 0.0
        var cloudSum = 0
        var cloudCount = 0

        for (item in items) {
            val weatherId = item.weather?.firstOrNull()?.id
            val pop = ((item.pop ?: 0.0) * 100).toInt()
            val rain = item.rain?.`3h` ?: 0.0
            val snow = item.snow?.`3h` ?: 0.0
            val cloud = item.clouds?.all

            if (pop > maxPop) maxPop = pop.toDouble()
            if (cloud != null) { cloudSum += cloud; cloudCount++ }

            when (weatherId) {
                in 200..232 -> hasThunder = true
                in 600..622 -> hasSnow = true
                in 300..531 -> hasRain = true
            }

            if (snow > 0.2) hasSnow = true
            if (rain > 0.2) hasRain = true
        }

        if (hasThunder) return "⛈️"
        if (hasSnow) return "🌨️"
        if (hasRain) return "🌧️"
        if (maxPop in 30.0..49.0) return "🌦️"

        val avgCloud = if (cloudCount > 0) cloudSum / cloudCount else 50
        return when {
            avgCloud < 25 -> "☀️"
            avgCloud < 60 -> "⛅"
            else -> "☁️"
        }
    }



    private lateinit var openExpenses: ActivityResultLauncher<Intent>
    private var lastTripId: String? = null
    private var lastRemaining: Double? = null




    private val usdFmt: java.text.NumberFormat =
        java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).apply {
            currency = java.util.Currency.getInstance("USD")
        }





    private var singleToast: Toast? = null
    private fun toast(msg: String) {
        singleToast?.cancel()
        singleToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun addDailyChip(container: android.widget.LinearLayout, title: String, sub: String, emoji: String) {
        val ctx = container.context
        val chip = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (8 * ctx.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = pad
            layoutParams = lp
            background = getDrawable(R.drawable.bg_chip)
        }
        val t1 = android.widget.TextView(ctx).apply { text = title; textSize = 12f }
        val e  = android.widget.TextView(ctx).apply { text = emoji; textSize = 20f }
        val t2 = android.widget.TextView(ctx).apply { text = sub; textSize = 12f }
        chip.addView(t1); chip.addView(e); chip.addView(t2)
        container.addView(chip)
    }

    private fun formatLocation(address: Map<String, String>?): String? {
        if (address == null) return null
        val city = address["city"] ?: address["town"] ?: address["village"] ?: address["hamlet"] ?: address["suburb"]
        val state = address["state"] ?: address["region"]
        val countryCode = address["country_code"]?.uppercase()
        return when {
            city != null && state != null && countryCode == "US" -> "$city, $state"
            city != null && state != null -> "$city, $state"
            city != null -> city
            state != null -> state
            else -> address["country"]
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_detail)
        openExpenses = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK && res.data != null) {
                lastTripId = res.data!!.getStringExtra("RESULT_TRIP_ID")
                lastRemaining = res.data!!.getDoubleExtra("RESULT_REMAINING", 0.0)

                findViewById<TextView>(R.id.txtHeaderBudget).text =
                    "Remaining ${usdFmt.format(lastRemaining)}"
            }
        }

        val stripDaily = findViewById<android.widget.LinearLayout>(R.id.stripDaily)
        val trip = intent.getSerializableExtra("extra_trip") as? Trip
        if (trip == null) { toast("Trip not found"); finish(); return }

        // Toolbar
        val bar = findViewById<MaterialToolbar>(R.id.topAppBar)
        bar.title = trip.title.ifBlank { "Trip" }
        bar.setNavigationIcon(android.R.drawable.ic_media_previous)
        bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Header values
        val dfMed = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        findViewById<TextView>(R.id.txtHeaderDestination).text =
            trip.destination.ifBlank { "Destination TBD" }
        findViewById<TextView>(R.id.txtHeaderDates).text =
            "${dfMed.format(Date(trip.startDate))} — ${dfMed.format(Date(trip.endDate))}"
        findViewById<TextView>(R.id.txtHeaderBudget).apply {
            if (trip.budget > 0.0) {
                text = java.text.NumberFormat.getCurrencyInstance().format(trip.budget)
                alpha = 1f
            } else {
                text = "No budget set"
                alpha = 0.7f
            }
        }

        val budgetView = findViewById<TextView>(R.id.txtHeaderBudget)


        val initialRemaining = when {
            trip.remaining > 0.0 && trip.remaining <= trip.budget -> trip.remaining
            trip.spent > 0.0 -> (trip.budget - trip.spent).coerceAtLeast(0.0)
            else -> trip.budget
        }
        budgetView.text = "Remaining ${usdFmt.format(initialRemaining)}"
        budgetView.alpha = 1f


        findViewById<MaterialCardView>(R.id.cardChatbot).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }


   //     findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardReviews)
     //       .setOnClickListener {
      //          val intent = Intent(this, com.example.travelpractice.reviews.ReviewsActivity::class.java)
     //           intent.putExtra(
        //            com.example.travelpractice.reviews.ReviewsActivity.EXTRA_LOCATION_FILTER,
       //             "Paris, France" // or your dynamic location
      //          )
       //         startActivity(intent)
       //    }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardReviews)
            .setOnClickListener {
                val intent = Intent(this, com.example.travelpractice.reviews.MapReviewsActivity::class.java)
                startActivity(intent)
            }





        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardExpenses)
            .setOnClickListener {
                val i = Intent(this, ExpenseTrackerActivity::class.java).apply {
                    putExtra("TRIP_ID", trip.id)
                    putExtra("TRIP_NAME", trip.title)
                    putExtra("TRIP_BUDGET", trip.budget)
                }
                openExpenses.launch(i) // <-- use the launcher, not startActivity
            }

        findViewById<MaterialCardView>(R.id.cardChecklist).setOnClickListener {
            val i = Intent(this, BagChecklistActivity::class.java).apply {
                putExtra("extra_trip_id", trip.id)
                putExtra("extra_trip_title", trip.title)
            }
            startActivity(i)
        }
        findViewById<MaterialCardView>(R.id.cardItinerary).setOnClickListener {
            val i = Intent(this, ItineraryActivity::class.java)
            i.putExtra("extra_trip", trip)
            startActivity(i)
        }

        val txtStatus = findViewById<TextView>(R.id.txtWeatherStatus)
        val txtEmoji  = findViewById<TextView>(R.id.txtCurrentEmoji)
        val txtTemp   = findViewById<TextView>(R.id.txtCurrentTemp)
        val txtTitle = findViewById<TextView>(R.id.txtWeatherTitle)

        lifecycleScope.launch {
            try {
                txtStatus.text = "Loading…"
                val startTime = System.currentTimeMillis()

                // 1) Get coordinates
                val (lat, lon) = when {
                    trip.lat != null && trip.lng != null -> {
                        android.util.Log.d("Weather", "✓ Using saved coords: 0ms")
                        txtTitle.text = "Weather • ${trip.destination}"
                        trip.lat!! to trip.lng!!
                    }
                    !trip.destination.isNullOrBlank() -> {
                        val coordStart = System.currentTimeMillis()
                        val res = HttpClients.nominatim.search(trip.destination!!)
                        android.util.Log.d("Weather", "⏱ Geocoding took: ${System.currentTimeMillis() - coordStart}ms")
                        if (res.isNotEmpty()) {
                            res.first().lat.toDouble() to res.first().lon.toDouble()
                        } else {
                            txtStatus.text = "Place not found"
                            return@launch
                        }
                    }
                    else -> {
                        txtStatus.text = "No location yet"
                        return@launch
                    }
                }



                val weatherStart = System.currentTimeMillis()
                val current = HttpClients.openWeather.current(lat, lon, HttpClients.OPENWEATHER_API_KEY)
                val forecast = HttpClients.openWeather.forecast(lat, lon, HttpClients.OPENWEATHER_API_KEY)
                android.util.Log.d("Weather", "⏱ Weather API took: ${System.currentTimeMillis() - weatherStart}ms")

                val t = current.main?.temp
                val weatherId = current.weather?.firstOrNull()?.id

                android.util.Log.d("Weather", "🌡️ OpenWeatherMap says: $t° at $lat, $lon")

                txtTemp.text = if (t != null) "${t.toInt()}°" else "--°"
                txtEmoji.text = owWeatherEmoji(weatherId)

                val timeStr = android.text.format.DateFormat
                    .getTimeFormat(this@TripDetailActivity)
                    .format(java.util.Date())
                txtStatus.text = "Updated $timeStr"
                txtStatus.visibility = android.view.View.VISIBLE

                stripDaily.removeAllViews()
                val dailyForecasts = groupForecastByDay(forecast.list.orEmpty())

                for ((day, items) in dailyForecasts.entries.take(7)) {
                    val dow = try {
                        val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        val outFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                        val date = inFmt.parse(day)
                        outFmt.format(date!!)
                    } catch (_: Exception) {
                        day.substring(5)
                    }

                    val minTemp = items.mapNotNull { it.main?.temp_min }.minOrNull()?.toInt() ?: 0
                    val maxTemp = items.mapNotNull { it.main?.temp_max }.maxOrNull()?.toInt() ?: 0
                    val sub = "$minTemp° / $maxTemp°"
                    val emj = getDailyEmoji(items)

                    addDailyChip(stripDaily, dow, sub, emj)
                }

                if (trip.destination.isNullOrBlank()) {
                    val reverseStart = System.currentTimeMillis()
                    runCatching {
                        HttpClients.nominatim.reverse(lat, lon)
                    }.onSuccess { rev ->
                        android.util.Log.d("Weather", "⏱ Reverse geocoding took: ${System.currentTimeMillis() - reverseStart}ms")
                        val loc = formatLocation(rev.address) ?: rev.display_name
                        txtTitle.text = if (!loc.isNullOrBlank()) "Weather • $loc" else "Weather"
                    }.onFailure {
                        android.util.Log.d("Weather", "Reverse geocoding failed: ${System.currentTimeMillis() - reverseStart}ms")
                        txtTitle.text = "Weather"
                    }
                }

                android.util.Log.d("Weather", " TOTAL TIME: ${System.currentTimeMillis() - startTime}ms")

            } catch (e: Exception) {
                android.util.Log.e("TripDetailActivity", "Weather error", e)
                txtStatus.text = "Unable: ${e::class.simpleName} - ${e.message}"
            }
        }
    }
}



