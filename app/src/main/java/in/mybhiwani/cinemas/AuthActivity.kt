package `in`.mybhiwani.cinemas

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AuthActivity : AppCompatActivity() {

    private var isRegisterMode = false
    private lateinit var sessionManager: SessionManager

    private lateinit var tabLogin: TextView
    private lateinit var tabRegister: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var containerName: View
    private lateinit var containerPhone: View
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvToggle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        // If already logged in, navigate straight to main catalog
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_auth)

        tabLogin = findViewById(R.id.tabLogin)
        tabRegister = findViewById(R.id.tabRegister)
        tvTitle = findViewById(R.id.tvAuthTitle)
        tvSubtitle = findViewById(R.id.tvAuthSubtitle)
        containerName = findViewById(R.id.containerName)
        containerPhone = findViewById(R.id.containerPhone)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSubmit = findViewById(R.id.btnSubmitAuth)
        tvToggle = findViewById(R.id.tvToggleAuth)

        tabLogin.setOnClickListener {
            if (isRegisterMode) {
                isRegisterMode = false
                updateUiState()
            }
        }

        tabRegister.setOnClickListener {
            if (!isRegisterMode) {
                isRegisterMode = true
                updateUiState()
            }
        }

        tvToggle.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUiState()
        }

        btnSubmit.setOnClickListener {
            handleAuth()
        }
    }

    private fun updateUiState() {
        if (isRegisterMode) {
            tabLogin.setBackgroundResource(0)
            tabLogin.setTextColor(Color.parseColor("#94A3B8"))

            tabRegister.setBackgroundResource(R.drawable.bg_tab_active)
            tabRegister.setTextColor(Color.WHITE)

            tvTitle.text = "Create Cinemas Account 🍿"
            tvSubtitle.text = "Register to watch private high-definition movies & web series"
            containerName.visibility = View.VISIBLE
            containerPhone.visibility = View.VISIBLE
            etEmail.hint = "Email Address"
            btnSubmit.text = "Create Account & Start Watching"
            tvToggle.text = "Already registered? Sign In here"
        } else {
            tabLogin.setBackgroundResource(R.drawable.bg_tab_active)
            tabLogin.setTextColor(Color.WHITE)

            tabRegister.setBackgroundResource(0)
            tabRegister.setTextColor(Color.parseColor("#94A3B8"))

            tvTitle.text = "Welcome Back 🎬"
            tvSubtitle.text = "Sign in to access your personal cinema watch list"
            containerName.visibility = View.GONE
            containerPhone.visibility = View.GONE
            etEmail.hint = "Email or 10-Digit Mobile"
            btnSubmit.text = "Sign In to Watch"
            tvToggle.text = "New to Cinemas? Create an account"
        }
    }

    private fun handleAuth() {
        val emailOrPhone = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (emailOrPhone.isEmpty() || password.isEmpty()) {
            showSnackbar("Please enter your credentials")
            return
        }

        if (isRegisterMode) {
            if (name.isEmpty()) {
                showSnackbar("Please enter your full name")
                return
            }
            if (phone.length != 10 || !phone.all { it.isDigit() }) {
                showSnackbar("Please enter a valid 10-digit mobile number")
                return
            }
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = "Authenticating..."

        val endpoint = if (isRegisterMode) {
            "https://cinemas.mybhiwani.in/api/auth.php?action=register"
        } else {
            "https://cinemas.mybhiwani.in/api/auth.php?action=login"
        }

        val jsonBody = JSONObject().apply {
            put("email", emailOrPhone)
            put("password", password)
            if (isRegisterMode) {
                put("name", name)
                put("phone", phone)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val os = OutputStreamWriter(conn.outputStream)
                os.write(jsonBody.toString())
                os.flush()
                os.close()

                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val reader = BufferedReader(InputStreamReader(stream))
                val resp = reader.readText()
                reader.close()

                val resJson = JSONObject(resp)
                val isSuccess = resJson.optBoolean("status", false)
                val message = resJson.optString("message", "Something went wrong")

                withContext(Dispatchers.Main) {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = if (isRegisterMode) "Create Account & Start Watching" else "Sign In to Watch"

                    if (isSuccess) {
                        val userObj = resJson.getJSONObject("data")
                        sessionManager.saveUserSession(
                            id = userObj.getInt("id"),
                            name = userObj.getString("name"),
                            email = userObj.getString("email"),
                            phone = userObj.optString("phone", "")
                        )

                        showSnackbar("Welcome, ${userObj.getString("name")}! 🎉")
                        startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                        finish()
                    } else {
                        showSnackbar(message)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = if (isRegisterMode) "Create Account & Start Watching" else "Sign In to Watch"
                    showSnackbar("Network error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun showSnackbar(msg: String) {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
            .setBackgroundTint(Color.parseColor("#1A2438"))
            .setTextColor(Color.WHITE)
            .show()
    }
}




