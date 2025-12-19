package com.example.travelpractice

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.model.Trip
import com.example.travelpractice.ui.home.AddTripBottomSheetDialogFragment
import com.example.travelpractice.ui.home.TripAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: TripAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var reg: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        recycler = findViewById(R.id.recyclerTrips)
        empty    = findViewById(R.id.emptyState)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = TripAdapter(
            onDelete = { trip ->
                db.collection("trips").document(trip.id).delete()
                    .addOnSuccessListener {
                        Snackbar.make(findViewById(android.R.id.content), "Trip Deleted", Snackbar.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Snackbar.make(findViewById(android.R.id.content), "Delete failed", Snackbar.LENGTH_SHORT).show()
                    }
            },
            onEdit = { trip ->
                AddTripBottomSheetDialogFragment.forEdit(trip)
                    .show(supportFragmentManager, "edit_trip")
            },
            onOpen = { trip ->
                val i = Intent(this, TripDetailActivity::class.java)
                i.putExtra("extra_trip", trip) // Trip implements Serializable
                startActivity(i)
            }
        )
        recycler.adapter = adapter


        findViewById<View>(R.id.fabAddTrip)?.setOnClickListener {
            AddTripBottomSheetDialogFragment.newInstance()
                .show(supportFragmentManager, "add_trip")
        }


        findViewById<MaterialToolbar?>(R.id.topAppBar)?.apply {
            inflateMenu(R.menu.menu_home)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_logout -> {
                        doLogout()
                        true
                    }
                    else -> false
                }
            }
        }


        findViewById<View?>(R.id.btnLogout)?.setOnClickListener { doLogout() }
    }

    fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }
    private fun doLogout() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        reg?.remove()

        val q = db.collection("trips")
            .whereEqualTo("userId", uid)


        reg = q.addSnapshotListener { snap, err ->
            if (err != null) {
                Snackbar.make(findViewById(android.R.id.content), "Failed to load trips", Snackbar.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            val items = snap?.documents?.mapNotNull { d ->
                d.toObject(Trip::class.java)?.apply { id = d.id }
            }.orEmpty()

            adapter.submitList(items)
            val hasTrips = items.isNotEmpty()
            recycler.visibility = if (hasTrips) View.VISIBLE else View.GONE
            empty.visibility    = if (hasTrips) View.GONE else View.VISIBLE
        }
    }

    override fun onStop() {
        super.onStop()
        reg?.remove()
        reg = null
    }
}

