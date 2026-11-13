package `in`.mybhiwani.cinemas

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CinemasUserSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"

        fun getUserName(context: Context): String {
            val sp = context.getSharedPreferences("CinemasUserSession", Context.MODE_PRIVATE)
            return sp.getString(KEY_USER_NAME, "Movie Buff") ?: "Movie Buff"
        }

        fun clearSession(context: Context) {
            val sp = context.getSharedPreferences("CinemasUserSession", Context.MODE_PRIVATE)
            sp.edit().clear().apply()
        }
    }

    fun saveUserSession(id: Int, name: String, email: String, phone: String = "") {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putInt(KEY_USER_ID, id)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_PHONE, phone)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Movie Buff") ?: "Movie Buff"

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "") ?: ""

    fun logout() {
        prefs.edit().clear().apply()
    }
}
