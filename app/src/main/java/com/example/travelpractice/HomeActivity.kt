package com.example.travelpractice

import android.widget.Toast
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.model.Trip
import com.example.travelpractice.ui.home.AddTripBottomSheetDialogFragment
import com.example.travelpractice.ui.home.TripAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

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

        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = TripAdapter(
            onDelete = { trip ->
                db.collection("trips").document(trip.id).delete()
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

        // + FAB → Add Trip
        findViewById<View>(R.id.fabAddTrip)?.setOnClickListener {
            AddTripBottomSheetDialogFragment.newInstance()
                .show(supportFragmentManager, "add_trip")
        }

        // Toolbar logout (only if toolbar has a menu item)
        findViewById<MaterialToolbar?>(R.id.topAppBar)?.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_logout) {
                doLogout()
                true
            } else false
        }

        // Bottom "Log out" button (only if present in your layout)
        findViewById<View?>(R.id.btnLogout)?.setOnClickListener { doLogout() }
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
        //.orderBy("startDate", Query.Direction.ASCENDING) // re-enable after creating composite index (userId+startDate)

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

