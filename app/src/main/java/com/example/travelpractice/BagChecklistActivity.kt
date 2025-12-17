package com.example.travelpractice.ui.checklist

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.model.PackingCategory
import com.example.travelpractice.model.PackingItem
import com.example.travelpractice.model.TodoTask
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.android.material.snackbar.Snackbar


class BagChecklistActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TRIP_ID = "extra_trip_id"
        private const val EXTRA_TRIP_TITLE = "extra_trip_title"
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    private val listId: String by lazy { intent.getStringExtra(EXTRA_TRIP_ID) ?: "default" }
    private val uid get() = auth.currentUser?.uid ?: ""

    private lateinit var topBar: MaterialToolbar
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var fabAddCategory: FloatingActionButton

    private lateinit var categoryAdapter: CategoryAdapter
    private val categories = mutableListOf<PackingCategory>()
    private val itemsByCategory = mutableMapOf<String, MutableList<PackingItem>>()

    private var showUncheckedOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bag_checklist)

        listenToCategories()
        backfillCreatedAtForExistingCategories()

        if (uid.isEmpty()) {
            finish()
            return
        }

        topBar = findViewById(R.id.topAppBar)
        tvEmpty = findViewById(R.id.emptyState)
        recycler = findViewById(R.id.recyclerCategories)
        fabAddCategory = findViewById(R.id.fabAddCategory)

        val tripTitle = intent.getStringExtra(EXTRA_TRIP_TITLE) ?: "Bag Checklist"
        topBar.title = tripTitle

        topBar.inflateMenu(R.menu.menu_checklist)

        val menu = topBar.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.icon?.setTint(Color.WHITE)
        }

        topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_filter_unchecked -> {
                    showUncheckedOnly = !showUncheckedOnly
                    item.isChecked = showUncheckedOnly

                    // Change icon based on state
                    item.setIcon(
                        if (showUncheckedOnly)
                            R.drawable.show_unchecked
                        else
                            R.drawable.show_everyting
                    )

                    categoryAdapter.showUncheckedOnly = showUncheckedOnly
                    categoryAdapter.notifyDataSetChanged()
                    true
                }

                else -> false
            }
        }

        categoryAdapter = CategoryAdapter(
            categories,
            itemsByCategory,
            onAddItem = { showAddItemDialog(it) },
            onDeleteCategory = { deleteCategory(it) },
            onToggleItem = { item, checked -> setItemChecked(item, checked) },
            onDeleteItem = { deleteItem(it) },
            onToggleExpand = { toggleExpand(it) }
        ).apply {
            showUncheckedOnly = this@BagChecklistActivity.showUncheckedOnly
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = categoryAdapter

        fabAddCategory.setOnClickListener { showAddCategoryDialog() }

        listenToCategories()
    }

    /** users/{uid}/checklists/{listId} */
    private fun userListRef() =
        db.collection("users").document(uid)
            .collection("checklists").document(listId)

    private fun listenToCategories() {
        userListRef().collection("categories")
            .orderBy("createdAt")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    e.printStackTrace()
                    tvEmpty.text = "Error loading categories"
                    tvEmpty.visibility = TextView.VISIBLE
                    return@addSnapshotListener
                }

                val newCats = snap?.documents?.mapNotNull { d ->
                    d.toObject(PackingCategory::class.java)?.copy(id = d.id)
                }.orEmpty()

                categories.clear()
                categories.addAll(newCats)
                tvEmpty.visibility = if (categories.isEmpty()) TextView.VISIBLE else TextView.GONE
                categoryAdapter.notifyDataSetChanged()

                categories.forEach { listenToItems(it.id) }
            }
    }

    private fun showSnack(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content), // app root view
            message,
            Snackbar.LENGTH_SHORT
        )
            .setAnchorView(fabAddCategory) // keeps it above the FAB (optional but nice)
            .show()
    }


    private fun backfillCreatedAtForExistingCategories() {
        userListRef().collection("categories").get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                var i = 0
                snap.documents.forEach { doc ->
                    if (!doc.contains("createdAt")) {
                        // Keep existing relative order by assigning increasing times
                        val ts = System.currentTimeMillis() + (i++)
                        batch.update(doc.reference, "createdAt", ts)
                    }
                }
                batch.commit()
            }
    }

    private fun listenToItems(categoryId: String) {
        userListRef().collection("categories").document(categoryId)
            .collection("items")
            .orderBy("name")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(PackingItem::class.java)?.copy(id = d.id)
                }.orEmpty()
                itemsByCategory[categoryId] = list.toMutableList()
                categoryAdapter.notifyDataSetChanged()
            }
    }

    private fun showAddCategoryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null, false)
        val input = view.findViewById<EditText>(R.id.inputCategoryName)

        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedAlertDialog)
            .setView(view)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        view.findViewById<View>(R.id.btnCancelCategory).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnAddCategory).setOnClickListener {
            val title = input.text?.toString()?.trim().orEmpty()

            if (title.isBlank()) {
                input.error = "Required"
                return@setOnClickListener
            }

            val catRef = userListRef().collection("categories").document()
            val cat = PackingCategory(
                id = catRef.id,
                title = title,
                uid = uid,
                listId = listId,
                expanded = true,
                createdAt = System.currentTimeMillis()
            )

            catRef.set(cat)
                .addOnSuccessListener {
                    showSnack("You added \"$title\" to your list")
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    showSnack("Couldn’t add category. Try again.")
                }

        }

        dialog.show()
    }

    private fun showAddItemDialog(category: PackingCategory) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_item, null, false)
        val input = view.findViewById<EditText>(R.id.inputItemName)

        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedAlertDialog)
            .setView(view)
            .create()

        dialog.show()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<View>(R.id.btnCancelItem).setOnClickListener { dialog.dismiss() }

        view.findViewById<View>(R.id.btnAddItem).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                input.error = "Required"
                return@setOnClickListener
            }

            val itemRef = userListRef()
                .collection("categories").document(category.id)
                .collection("items").document()

            itemRef.set(
                PackingItem(
                    id = itemRef.id,
                    name = name,
                    checked = false,
                    uid = uid,
                    listId = listId,
                    categoryId = category.id
                )
            )
                .addOnSuccessListener {
                    showSnack("You added \"$name\" to your list")
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    showSnack("Couldn’t add item. Try again.")
                }

        }
    }

    private fun deleteCategory(category: PackingCategory) {
        val dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor
        )
            .setTitle("Delete Category")
            .setMessage("Delete \"${category.title}\" and all items in it?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { d, _ ->

                val catRef = userListRef().collection("categories").document(category.id)

                catRef.collection("items").get().addOnSuccessListener { snap ->
                    val batch = db.batch()
                    snap.documents.forEach { batch.delete(it.reference) }
                    batch.delete(catRef)

                    batch.commit()
                        .addOnSuccessListener {
                            showSnack("Removed \"${category.title}\" from your list")
                        }
                        .addOnFailureListener {
                            showSnack("Failed to remove category. Try again.")
                        }
                }

                d.dismiss()
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.red))
    }


    private fun deleteItem(item: PackingItem) {
        val dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_JourneyFlow_AlertDialogAnchor
        )
            .setTitle("Delete Item")
            .setMessage("Remove \"${item.name}\" from the checklist?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { d, _ ->

                userListRef()
                    .collection("categories").document(item.categoryId)
                    .collection("items").document(item.id)
                    .delete()
                    .addOnSuccessListener {
                        showSnack("Removed \"${item.name}\" from your list")
                    }
                    .addOnFailureListener {
                        showSnack("Failed to remove item. Try again.")
                    }

                d.dismiss()
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.red))
    }


    private fun setItemChecked(item: PackingItem, checked: Boolean) {
        userListRef().collection("categories").document(item.categoryId)
            .collection("items").document(item.id)
            .update("checked", checked)
    }

    private fun toggleExpand(category: PackingCategory) {
        userListRef().collection("categories").document(category.id)
            .update("expanded", !category.expanded)
    }

    private fun showAddTaskDialog() {
        val input = EditText(this).apply { hint = "Task name (e.g., Call car rental)" }
        MaterialAlertDialogBuilder(this, R.style.RoundedAlertDialog)
            .setTitle("Add Task")
            .setView(input)
            .setPositiveButton("Add") { d, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val ref = userListRef().collection("tasks").document()
                    ref.set(
                        TodoTask(
                            id = ref.id,
                            name = name,
                            checked = false,
                            uid = uid,
                            listId = listId
                        )
                    )
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemainingDialog() {
        val itemsQuery = db.collectionGroup("items")
            .whereEqualTo("uid", uid)
            .whereEqualTo("listId", listId)
            .whereEqualTo("checked", false)
            .get()

        val tasksQuery = db.collectionGroup("tasks")
            .whereEqualTo("uid", uid)
            .whereEqualTo("listId", listId)
            .whereEqualTo("checked", false)
            .get()

        itemsQuery.addOnSuccessListener { itemsSnap ->
            val items = itemsSnap.documents.mapNotNull { it.getString("name") }
            tasksQuery.addOnSuccessListener { tasksSnap ->
                val tasks = tasksSnap.documents.mapNotNull { it.getString("name") }

                val msg = buildString {
                    append("Items remaining:\n")
                    if (items.isEmpty()) append("• None ✅\n") else items.forEach { append("• $it\n") }
                    append("\nTasks remaining:\n")
                    if (tasks.isEmpty()) append("• None ✅\n") else tasks.forEach { append("• $it\n") }
                }

                MaterialAlertDialogBuilder(this, R.style.RoundedAlertDialog)
                    .setTitle("Still Unchecked")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
