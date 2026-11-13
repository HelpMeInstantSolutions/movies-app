package `in`.mybhiwani.courseapp

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
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvToggle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        // If already logged in, navigate straight to courses
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
        etName = findViewById(R.id.etName)
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

            tvTitle.text = "Create Student Account ✨"
            tvSubtitle.text = "Register to access your online video batches"
            containerName.visibility = View.VISIBLE
            btnSubmit.text = "Create Account & Start Learning"
            tvToggle.text = "Already registered? Sign In here"
        } else {
            tabLogin.setBackgroundResource(R.drawable.bg_tab_active)
            tabLogin.setTextColor(Color.WHITE)

            tabRegister.setBackgroundResource(0)
            tabRegister.setTextColor(Color.parseColor("#94A3B8"))

            tvTitle.text = "Welcome Back 👋"
            tvSubtitle.text = "Enter your credentials to access your enrolled courses"
            containerName.visibility = View.GONE
            btnSubmit.text = "Sign In to Account"
            tvToggle.text = "New to Insight? Create an account"
        }
    }

    private fun handleAuth() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val name = etName.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showSnackbar("Please enter both email and password")
            return
        }

        if (isRegisterMode && name.isEmpty()) {
            showSnackbar("Please enter your full name")
            return
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = "Authenticating..."

        val endpoint = if (isRegisterMode) {
            "https://cinemas.mybhiwani.in/api/auth.php?action=register"
        } else {
            "https://cinemas.mybhiwani.in/api/auth.php?action=login"
        }

        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", password)
            if (isRegisterMode) put("name", name)
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
                    btnSubmit.text = if (isRegisterMode) "Create Account & Start Learning" else "Sign In to Account"

                    if (isSuccess) {
                        val userObj = resJson.getJSONObject("data")
                        sessionManager.saveUserSession(
                            id = userObj.getInt("id"),
                            name = userObj.getString("name"),
                            email = userObj.getString("email")
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
                    btnSubmit.text = if (isRegisterMode) "Create Account & Start Learning" else "Sign In to Account"
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

