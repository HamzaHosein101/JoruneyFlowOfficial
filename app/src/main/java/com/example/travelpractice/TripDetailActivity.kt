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
import androidx.activity.result.contract.ActivityResultContracts


class TripDetailActivity : AppCompatActivity() {


    private fun weatherEmoji(code: Int?): String = when (code) {
        0 -> "☀️"
        1, 2, 3 -> "⛅"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77 -> "🌨️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
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
        // 🔸 Register activity result launcher FIRST
        openExpenses = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK && res.data != null) {
                lastTripId = res.data!!.getStringExtra("RESULT_TRIP_ID")
                lastRemaining = res.data!!.getDoubleExtra("RESULT_REMAINING", 0.0)

                // 🔥 Instantly update the header budget
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

        // Expense Tracker - pass trip data to expense tracker
        val budgetView = findViewById<TextView>(R.id.txtHeaderBudget)



// prefer saved remaining; fall back to computed; else show budget
        val initialRemaining = when {
            trip.remaining > 0.0 && trip.remaining <= trip.budget -> trip.remaining
            trip.spent > 0.0 -> (trip.budget - trip.spent).coerceAtLeast(0.0)
            else -> trip.budget
        }
        budgetView.text = "Remaining ${usdFmt.format(initialRemaining)}"
        budgetView.alpha = 1f


        findViewById<MaterialCardView>(R.id.cardChatbot).setOnClickListener { toast("AI Chatbot") }
        findViewById<MaterialCardView>(R.id.cardReviews).setOnClickListener { toast("Reviews") }
        // Expense Tracker card → open ExpenseTrackerActivity
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

        // ---------- WEATHER (Step 2) ----------
        val txtStatus = findViewById<TextView>(R.id.txtWeatherStatus)
        val txtEmoji  = findViewById<TextView>(R.id.txtCurrentEmoji)
        val txtTemp   = findViewById<TextView>(R.id.txtCurrentTemp)
        val txtTitle = findViewById<TextView>(R.id.txtWeatherTitle)

        lifecycleScope.launch {
            try {
                txtStatus.text = "Loading…"

                // 1) Coordinates: use saved lat/lng, else geocode destination via Nominatim
                val (lat, lon) = when {
                    trip.lat != null && trip.lng != null -> trip.lat!! to trip.lng!!
                    !trip.destination.isNullOrBlank() -> {
                        val res = HttpClients.nominatim.search(trip.destination!!)
                        if (res.isNotEmpty()) {
                            val la = res.first().lat.toDouble()
                            val lo = res.first().lon.toDouble()
                            la to lo
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

                // 2) Weather call
                val wx = HttpClients.openMeteo.forecast(lat, lon, unit = "fahrenheit")

                // Bind current
                val t = wx.current?.temperature_2m
                val c = wx.current?.weather_code
                txtTemp.text = if (t != null) "${t.toInt()}°" else "--°"
                txtEmoji.text = weatherEmoji(c)

                // Show updated time
                val timeStr = android.text.format.DateFormat
                    .getTimeFormat(this@TripDetailActivity)
                    .format(java.util.Date())
                txtStatus.text = "Updated $timeStr"
                txtStatus.visibility = android.view.View.VISIBLE

                //Bind 7-day strip
                stripDaily.removeAllViews()
                val days  = wx.daily?.time.orEmpty()
                val maxes = wx.daily?.temperature_2m_max.orEmpty()
                val mins  = wx.daily?.temperature_2m_min.orEmpty()
                val codes = wx.daily?.weather_code.orEmpty()

                // 5) Reverse geocode
                runCatching {
                    HttpClients.nominatim.reverse(lat, lon)
                }.onSuccess { rev ->
                    val loc = formatLocation(rev.address) ?: rev.display_name
                    txtTitle.text = if (!loc.isNullOrBlank()) "Weather • $loc" else "Weather"
                }.onFailure {
                    txtTitle.text = "Weather"
                }

                for (i in days.indices) {
                    val dow = try {
                        val inFmt  = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        val outFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()) // Mon/Tue/...
                        val date   = inFmt.parse(days[i])
                        outFmt.format(date!!)
                    } catch (_: Exception) {
                        days[i].substring(5) // fallback "MM-DD"
                    }
                    val sub  = "${mins.getOrNull(i)?.toInt() ?: "-"}° / ${maxes.getOrNull(i)?.toInt() ?: "-"}°"
                    val emj  = weatherEmoji(codes.getOrNull(i))
                    addDailyChip(stripDaily, dow, sub, emj)
                }
            } catch (e: Exception) {
                txtStatus.text = "Unable to load"
            }
        }
    }
}



