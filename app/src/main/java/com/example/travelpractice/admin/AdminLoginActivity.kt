package com.example.travelpractice.admin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.travelpractice.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.textfield.TextInputLayout

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var tilAdminEmail: TextInputLayout
    private lateinit var etUsername: EditText
    private lateinit var tilAdminPassword: TextInputLayout
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREF_NAME = "admin_prefs"
        private const val KEY_IS_LOGGED_IN = "is_admin_logged_in"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        tilAdminEmail = findViewById(R.id.tilAdminEmail)
        etUsername = findViewById(R.id.etAdminUsername)
        tilAdminPassword = findViewById(R.id.tilAdminPassword)
        etPassword = findViewById(R.id.etAdminPassword)
        btnLogin = findViewById(R.id.btnAdminLogin)

        FirebaseAuth.getInstance().currentUser?.let { currentUser ->
            verifyAdminRole(currentUser.uid,
                onSuccess = {
                    prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                    navigateToPanel()
                },
                onFailure = {
                    FirebaseAuth.getInstance().signOut()
                }
            )
        }

        btnLogin.setOnClickListener {
            tilAdminEmail.error = null
            tilAdminPassword.error = null

            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilAdminEmail.error = getString(R.string.admin_login_invalid_email)
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                tilAdminPassword.error = getString(R.string.admin_login_password_required)
                return@setOnClickListener
            }

            setLoading(true)

            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        showError(getString(R.string.admin_login_generic_error))
                        setLoading(false)
                        return@addOnSuccessListener
                    }

                    verifyAdminRole(
                        uid = user.uid,
                        onSuccess = {
                            prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                            navigateToPanel()
                        },
                        onFailure = { error ->
                            FirebaseAuth.getInstance().signOut()
                            showError(error ?: getString(R.string.admin_login_not_admin))
                            setLoading(false)
                        }
                    )
                }
                .addOnFailureListener { e ->
                    showError(e.localizedMessage ?: getString(R.string.admin_login_generic_error))
                    setLoading(false)
                }
        }
    }

    private fun verifyAdminRole(
        uid: String,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.getString("role") == "admin") {
                    onSuccess()
                } else {
                    onFailure(getString(R.string.admin_login_not_admin))
                }
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage)
            }
    }

    private fun navigateToPanel() {
        setLoading(false)
        val intent = Intent(this, AdminPanelActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = if (loading) {
            getString(R.string.admin_login_progress)
        } else {
            getString(R.string.admin_login_button)
        }
        tilAdminEmail.isEnabled = !loading
        tilAdminPassword.isEnabled = !loading
        etUsername.isEnabled = !loading
        etPassword.isEnabled = !loading
    }
}

