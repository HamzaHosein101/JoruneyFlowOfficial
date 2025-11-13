package com.example.travelpractice.reviews

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.travelpractice.databinding.ActivityMapReviewsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.Locale

class MapReviewsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapReviewsBinding
    private lateinit var map: GoogleMap

    private var selectedTownName: String? = null
    private var selectedTownSubtitle: String? = null
    private var selectedLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.topAppBarMap)
        binding.topAppBarMap.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Disable button until a town is selected
        binding.btnAddReviewDirect.isEnabled = false

        // Setup map fragment
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager
            .beginTransaction()
            .replace(binding.mapContainer.id, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        // Single button: open TownReviewsActivity (user can view + add reviews there)
        binding.btnAddReviewDirect.setOnClickListener {
            val town = selectedTownName ?: return@setOnClickListener
            val subtitle = selectedTownSubtitle

            val intent = Intent(this, TownReviewsActivity::class.java).apply {
                putExtra(TownReviewsActivity.EXTRA_TOWN_NAME, town)
                putExtra(TownReviewsActivity.EXTRA_TOWN_SUBTITLE, subtitle)
            }
            startActivity(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Basic UI options
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isZoomGesturesEnabled = true

        // Move map UI (zoom controls, etc.) slightly up so they don't hide behind the bottom card
        val bottomPaddingPx = (80 * resources.displayMetrics.density).toInt()
        map.setPadding(0, 0, 0, bottomPaddingPx)

        // Start centered somewhere reasonable (NY as default)
        val ny = LatLng(40.7128, -74.0060)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(ny, 3.5f))

        // When user taps anywhere on the map
        map.setOnMapClickListener { latLng ->
            handleMapTap(latLng)
        }
    }

    private fun handleMapTap(latLng: LatLng) {
        selectedLatLng = latLng

        // Clear previous markers, add new one
        map.clear()
        map.addMarker(MarkerOptions().position(latLng))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 6f))

        // Reverse-geocode to get town/city + country
        val geocoder = Geocoder(this, Locale.getDefault())
        var townName = "Selected location"
        var subtitle: String? = null

        try {
            val results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                val city = addr.locality ?: addr.subAdminArea
                val country = addr.countryName
                val region = addr.adminArea

                townName = when {
                    city != null && country != null -> "$city, $country"
                    country != null -> country
                    else -> "Selected location"
                }

                subtitle = region ?: country
            }
        } catch (e: IOException) {
            Snackbar.make(binding.mapReviewsRoot, "Could not get location name", Snackbar.LENGTH_SHORT).show()
        }

        selectedTownName = townName
        selectedTownSubtitle = subtitle

        // Update bottom card UI
        binding.tvTownName.text = townName
        binding.tvTownSubtitle.text = subtitle ?: ""

        // Enable the single action button once we have something
        binding.btnAddReviewDirect.isEnabled = true
    }
}
