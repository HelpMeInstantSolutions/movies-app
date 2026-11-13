package `in`.mybhiwani.courseapp

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CourseAppUserSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }

    fun saveUserSession(id: Int, name: String, email: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putInt(KEY_USER_ID, id)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Student") ?: "Student"

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    fun logout() {
        prefs.edit().clear().apply()
    }
}

