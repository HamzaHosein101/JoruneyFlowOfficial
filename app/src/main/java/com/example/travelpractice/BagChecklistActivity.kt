package com.example.travelpractice.ui.checklist

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelpractice.R
import com.example.travelpractice.data.defaultSeed
import com.example.travelpractice.model.PackingCategory
import com.example.travelpractice.model.PackingItem
import com.example.travelpractice.model.TodoTask
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

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

    // keeps the toggle state across config changes if you want (simple var is fine too)
    private var showUncheckedOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bag_checklist)

        if (uid.isEmpty()) { finish(); return }

        topBar = findViewById(R.id.topAppBar)
        tvEmpty = findViewById(R.id.emptyState)
        recycler = findViewById(R.id.recyclerCategories)
        fabAddCategory = findViewById(R.id.fabAddCategory)

        val tripTitle = intent.getStringExtra(EXTRA_TRIP_TITLE) ?: "Bag Checklist"
        topBar.title = tripTitle

        // ===== Toolbar menu (Add Task, Show Remaining, Show Unchecked / Show All)
        topBar.inflateMenu(R.menu.menu_checklist)
        topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {

                R.id.action_filter_unchecked -> {
                    showUncheckedOnly = !showUncheckedOnly
                    item.isChecked = showUncheckedOnly
                    item.title = if (showUncheckedOnly) "Show All" else "Show Unchecked"
                    categoryAdapter.showUncheckedOnly = showUncheckedOnly
                    categoryAdapter.notifyDataSetChanged()
                    true
                }

                else -> false
            }
        }
        // ======================================

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
        maybeSeedDefaultsOnce()
    }

    /** users/{uid}/checklists/{listId} */
    private fun userListRef() =
        db.collection("users").document(uid)
            .collection("checklists").document(listId)

    private fun listenToCategories() {
        userListRef().collection("categories")
            .orderBy("title")
            .addSnapshotListener { snap, _ ->
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

    private fun maybeSeedDefaultsOnce() {
        userListRef().collection("categories").limit(1).get()
            .addOnSuccessListener { res ->
                if (!res.isEmpty) return@addOnSuccessListener

                defaultSeed.forEach { dc ->
                    val catRef = userListRef().collection("categories").document()
                    val cat = PackingCategory(
                        id = catRef.id, title = dc.title, uid = uid, listId = listId, expanded = true
                    )
                    catRef.set(cat)

                    dc.items.forEach { name ->
                        val itemRef = catRef.collection("items").document()
                        val item = PackingItem(
                            id = itemRef.id, name = name, checked = false,
                            uid = uid, listId = listId, categoryId = cat.id
                        )
                        itemRef.set(item)
                    }
                }

                val taskRef = userListRef().collection("tasks").document()
                taskRef.set(
                    TodoTask(id = taskRef.id, name = "Renew passport", checked = false, uid = uid, listId = listId)
                )
            }
    }

    private fun showAddCategoryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null, false)
        val input = view.findViewById<EditText>(R.id.inputCategoryName)
        AlertDialog.Builder(this)
            .setTitle("Add Category")
            .setView(view)
            .setPositiveButton("Add") { d, _ ->
                val title = input.text?.toString()?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    val catRef = userListRef().collection("categories").document()
                    val cat = PackingCategory(
                        id = catRef.id, title = title, uid = uid, listId = listId, expanded = true
                    )
                    catRef.set(cat)
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddItemDialog(category: PackingCategory) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_item, null, false)
        val input = view.findViewById<EditText>(R.id.inputItemName)
        AlertDialog.Builder(this)
            .setTitle("Add Item")
            .setView(view)
            .setPositiveButton("Add") { d, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val itemRef = userListRef()
                        .collection("categories").document(category.id)
                        .collection("items").document()
                    val item = PackingItem(
                        id = itemRef.id, name = name, checked = false,
                        uid = uid, listId = listId, categoryId = category.id
                    )
                    itemRef.set(item)
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCategory(category: PackingCategory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete \"${category.title}\" and all items in it?")
            .setPositiveButton("Yes") { d, _ ->
                val catRef = userListRef().collection("categories").document(category.id)
                catRef.collection("items").get().addOnSuccessListener { snap ->
                    val batch = db.batch()
                    snap.documents.forEach { batch.delete(it.reference) }
                    batch.delete(catRef)
                    batch.commit()
                }
                d.dismiss()
            }
            .setNegativeButton("No", null)
            .show()
    }


    private fun deleteItem(item: PackingItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Remove \"${item.name}\" from the checklist?")
            .setPositiveButton("Yes") { d, _ ->
                userListRef().collection("categories").document(item.categoryId)
                    .collection("items").document(item.id).delete()
                d.dismiss()
            }
            .setNegativeButton("No", null)
            .show()
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
        AlertDialog.Builder(this)
            .setTitle("Add Task")
            .setView(input)
            .setPositiveButton("Add") { d, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val ref = userListRef().collection("tasks").document()
                    ref.set(TodoTask(id = ref.id, name = name, checked = false, uid = uid, listId = listId))
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
                AlertDialog.Builder(this)
                    .setTitle("Still Unchecked")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
