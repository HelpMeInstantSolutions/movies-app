package `in`.mybhiwani.cinemas

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import `in`.mybhiwani.cinemas.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Anti-Piracy Flag
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_splash)

        val ivLogo = findViewById<ImageView>(R.id.ivSplashLogo)
        val tvTitle = findViewById<TextView>(R.id.tvSplashTitle)

        // Initial animation state
        ivLogo.scaleX = 0.4f
        ivLogo.scaleY = 0.4f
        ivLogo.alpha = 0f

        tvTitle.alpha = 0f
        tvTitle.translationY = 40f

        // Smooth modern entrance animation
        ivLogo.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(900)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        tvTitle.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setDuration(700)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Transition after 1.6s
        Handler(Looper.getMainLooper()).postDelayed({
            val sessionManager = SessionManager(this)
            val nextIntent = if (sessionManager.isLoggedIn()) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, AuthActivity::class.java)
            }
            startActivity(nextIntent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1600)
    }
}
