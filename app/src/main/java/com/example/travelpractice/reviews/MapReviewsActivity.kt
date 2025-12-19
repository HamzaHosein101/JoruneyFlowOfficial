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
import android.view.inputmethod.EditorInfo
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout



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

        val etSearch = binding.etSearchTown
        val tilSearch = binding.tilSearchTown

        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) searchTown(query)
                true
            } else false
        }


        tilSearch.setEndIconOnClickListener {
            val query = etSearch.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) searchTown(query)
        }



        setSupportActionBar(binding.topAppBarMap)
        binding.topAppBarMap.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        binding.btnAddReviewDirect.isEnabled = false


        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager
            .beginTransaction()
            .replace(binding.mapContainer.id, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)


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


        val bottomPaddingPx = (80 * resources.displayMetrics.density).toInt()
        map.setPadding(0, 0, 0, bottomPaddingPx)


        val ny = LatLng(40.7128, -74.0060)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(ny, 3.5f))


        map.setOnMapClickListener { latLng ->
            handleMapTap(latLng)
        }
    }

    private fun searchTown(query: String) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val results = geocoder.getFromLocationName(query, 1)

            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                val latLng = com.google.android.gms.maps.model.LatLng(
                    addr.latitude,
                    addr.longitude
                )


                handleMapTap(latLng)

            } else {
                Snackbar.make(binding.mapReviewsRoot,
                    "No results found for \"$query\"",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.mapReviewsRoot,
                "Could not find that town. Try adding country/state.",
                Snackbar.LENGTH_SHORT
            ).show()
            e.printStackTrace()
        }
    }


    private fun handleMapTap(latLng: LatLng) {
        selectedLatLng = latLng


        map.clear()
        map.addMarker(MarkerOptions().position(latLng))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 6f))


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


        binding.tvTownName.text = townName
        binding.tvTownSubtitle.text = subtitle ?: ""


        binding.btnAddReviewDirect.isEnabled = true
    }
}
