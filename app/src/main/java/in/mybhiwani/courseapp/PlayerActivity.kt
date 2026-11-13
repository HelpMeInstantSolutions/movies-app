package `in`.mybhiwani.courseapp

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PlayerActivity : AppCompatActivity() {

    // Native ExoPlayer (Media3 - Zero Watermark / Zero Logo / Pure Stream)
    private var exoPlayer: ExoPlayer? = null
    private lateinit var exoPlayerView: PlayerView

    // Fallback WebView (Only for YouTube or legacy failover)
    private lateinit var webView: WebView

    private lateinit var progressBar: ProgressBar
    private lateinit var videoContainer: RelativeLayout
    private lateinit var playerTopBar: View
    private lateinit var playerMetaSection: View
    private lateinit var playlistHeaderSection: View
    private lateinit var rvLessons: RecyclerView

    private lateinit var tvPlayerCourseTitle: TextView
    private lateinit var tvPlayerLessonCounter: TextView
    private lateinit var tvCurrentLesson: TextView
    private lateinit var tvPlaylistCount: TextView

    private lateinit var btnSpeedToggle: TextView
    private lateinit var btnLockToggle: ImageButton
    private lateinit var btnFullscreenToggle: ImageButton
    private lateinit var layoutLockOverlay: View
    private lateinit var btnUnlockTap: View
    private lateinit var seekLeftBubble: View
    private lateinit var seekRightBubble: View

    private lateinit var lessonAdapter: LessonAdapter
    private val lessons = mutableListOf<VideoLesson>()

    private var courseId: Int = 1
    private var courseTitle: String = "Course Lectures"
    private var initialVideoId: Int = 0
    private var currentLessonIndex: Int = 0
    private var currentSpeed: Double = 1.0
    private var isFullscreen: Boolean = false
    private var isScreenLocked: Boolean = false

    private lateinit var gestureDetector: GestureDetector
    private val hideHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Anti-Screen Capture & Anti-Piracy Flag
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_player)

        courseId = intent.getIntExtra("COURSE_ID", 1)
        courseTitle = intent.getStringExtra("COURSE_TITLE") ?: "Course Lectures"
        initialVideoId = intent.getIntExtra("INITIAL_VIDEO_ID", 0)

        initViews()
        setupExoPlayer()
        setupWebView()
        setupGestures()
        setupControls()
        fetchCourseVideos(courseId)
    }

    private fun initViews() {
        exoPlayerView = findViewById(R.id.exoPlayerView)
        webView = findViewById(R.id.playerWebView)
        progressBar = findViewById(R.id.playerLoader)
        videoContainer = findViewById(R.id.videoContainer)
        playerTopBar = findViewById(R.id.playerTopBar)
        playerMetaSection = findViewById(R.id.playerMetaSection)
        playlistHeaderSection = findViewById(R.id.playlistHeaderSection)
        rvLessons = findViewById(R.id.rvLessonList)

        tvPlayerCourseTitle = findViewById(R.id.tvPlayerCourseTitle)
        tvPlayerLessonCounter = findViewById(R.id.tvPlayerLessonCounter)
        tvCurrentLesson = findViewById(R.id.tvCurrentLesson)
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount)

        btnSpeedToggle = findViewById(R.id.btnSpeedToggle)
        btnLockToggle = findViewById(R.id.btnLockToggle)
        btnFullscreenToggle = findViewById(R.id.btnFullscreenToggle)
        layoutLockOverlay = findViewById(R.id.layoutLockOverlay)
        btnUnlockTap = findViewById(R.id.btnUnlockTap)
        seekLeftBubble = findViewById(R.id.seekLeftBubble)
        seekRightBubble = findViewById(R.id.seekRightBubble)

        tvPlayerCourseTitle.text = courseTitle
        rvLessons.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnBackFromPlayer)?.setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupExoPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build()
            exoPlayerView.player = exoPlayer
            exoPlayerView.controllerShowTimeoutMs = 3000
            exoPlayerView.controllerAutoShow = true

            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                        Player.STATE_READY -> progressBar.visibility = View.GONE
                        Player.STATE_ENDED -> playNextLesson()
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    progressBar.visibility = View.GONE
                    showToaster("Playback stream error: ${error.localizedMessage}")
                }
            })
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 70) {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                val hideWatermarkJs = """
                    (function() {
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.innerHTML = '.dmp_Logo, .np_Logo, [class*="Logo"], [class*="watermark"], [class*="Brand"], [aria-label*="Dailymotion"], .dmp_Button--logo, .player_logo, .watermark, .dmp_ShareButton, [class*="share"], .dmp_Endscreen, .player_banner { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; }';
                        if (document.head) { document.head.appendChild(style); }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(hideWatermarkJs, null)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return true

                val screenWidth = videoContainer.width
                val touchX = e.x

                if (touchX > screenWidth * 0.6) {
                    seekVideo(10)
                    showSeekAnimation(seekRightBubble)
                } else if (touchX < screenWidth * 0.4) {
                    seekVideo(-10)
                    showSeekAnimation(seekLeftBubble)
                }
                return true
            }
        })

        val touchListener = View.OnTouchListener { _, event ->
            if (isScreenLocked) {
                if (event.action == MotionEvent.ACTION_UP) {
                    showUnlockPrompt()
                }
                true
            } else {
                gestureDetector.onTouchEvent(event)
                false
            }
        }

        webView.setOnTouchListener(touchListener)
        exoPlayerView.setOnTouchListener(touchListener)
    }

    private fun showSeekAnimation(bubbleView: View) {
        bubbleView.visibility = View.VISIBLE
        bubbleView.alpha = 1.0f
        bubbleView.animate()
            .alpha(0.0f)
            .setDuration(600)
            .withEndAction { bubbleView.visibility = View.GONE }
            .start()
    }

    private fun setupControls() {
        // Speed Toggle
        btnSpeedToggle.setOnClickListener {
            if (isScreenLocked) return@setOnClickListener
            currentSpeed = when (currentSpeed) {
                1.0 -> 1.25
                1.25 -> 1.5
                1.5 -> 1.75
                1.75 -> 2.0
                else -> 1.0
            }
            btnSpeedToggle.text = "${currentSpeed}x"
            setPlaybackSpeed(currentSpeed)
            showToaster("Playback Speed: ${currentSpeed}x ⚡")
        }

        // Screen Lock Toggle
        btnLockToggle.setOnClickListener {
            toggleScreenLock()
        }

        btnUnlockTap.setOnClickListener {
            toggleScreenLock()
        }

        // Fullscreen Toggle
        btnFullscreenToggle.setOnClickListener {
            toggleFullscreen()
        }

        // Next Lesson
        findViewById<View>(R.id.btnNextLesson)?.setOnClickListener {
            playNextLesson()
        }
    }

    private fun toggleScreenLock() {
        isScreenLocked = !isScreenLocked
        if (isScreenLocked) {
            btnLockToggle.setImageResource(R.drawable.ic_lock)
            showUnlockPrompt()
            showToaster("Screen Controls Locked 🔒")
        } else {
            btnLockToggle.setImageResource(R.drawable.ic_lock_open)
            layoutLockOverlay.visibility = View.GONE
            showToaster("Screen Controls Unlocked 🔓")
        }
    }

    private fun showUnlockPrompt() {
        layoutLockOverlay.visibility = View.VISIBLE
        layoutLockOverlay.alpha = 1.0f
        hideHandler.removeCallbacksAndMessages(null)
        hideHandler.postDelayed({
            layoutLockOverlay.animate()
                .alpha(0.0f)
                .setDuration(400)
                .withEndAction { layoutLockOverlay.visibility = View.GONE }
                .start()
        }, 2800)
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            applyPortraitUi()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            applyLandscapeUi()
        }
    }

    private fun applyLandscapeUi() {
        isFullscreen = true
        btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_exit)
        playerTopBar.visibility = View.GONE
        playerMetaSection.visibility = View.GONE
        playlistHeaderSection.visibility = View.GONE
        rvLessons.visibility = View.GONE

        val params = videoContainer.layoutParams as LinearLayout.LayoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        videoContainer.layoutParams = params

        // Immersive Sticky Flags
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    private fun applyPortraitUi() {
        isFullscreen = false
        btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen)
        playerTopBar.visibility = View.VISIBLE
        playerMetaSection.visibility = View.VISIBLE
        playlistHeaderSection.visibility = View.VISIBLE
        rvLessons.visibility = View.VISIBLE

        val heightDp = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 225f, resources.displayMetrics
        ).toInt()

        val params = videoContainer.layoutParams as LinearLayout.LayoutParams
        params.height = heightDp
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        videoContainer.layoutParams = params

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyLandscapeUi()
        } else {
            applyPortraitUi()
        }
    }

    private fun seekVideo(seconds: Int) {
        if (exoPlayer != null && exoPlayerView.visibility == View.VISIBLE) {
            val cur = exoPlayer!!.currentPosition
            val target = (cur + (seconds * 1000L)).coerceAtLeast(0L)
            exoPlayer!!.seekTo(target)
        } else {
            val js = """
                (function() {
                    var v = document.querySelector('video');
                    if (v) { v.currentTime = Math.max(0, v.currentTime + ($seconds)); }
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    private fun setPlaybackSpeed(speed: Double) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed.toFloat())
        val js = """
            (function() {
                var v = document.querySelector('video');
                if (v) { v.playbackRate = $speed; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun playNextLesson() {
        if (currentLessonIndex < lessons.size - 1) {
            currentLessonIndex++
            lessonAdapter.setSelectedPosition(currentLessonIndex)
            val nextLesson = lessons[currentLessonIndex]
            playVideoDirect(nextLesson.dailymotionId, nextLesson.title)
        } else {
            showToaster("You have reached the last lecture in this course! 🎓")
        }
    }

    private fun extractYouTubeId(urlOrId: String): String {
        return if (urlOrId.contains("youtu.be/")) {
            urlOrId.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        } else if (urlOrId.contains("v=")) {
            urlOrId.substringAfter("v=").substringBefore("&")
        } else if (urlOrId.contains("embed/")) {
            urlOrId.substringAfter("embed/").substringBefore("?").substringBefore("&")
        } else {
            urlOrId.trim()
        }
    }

    private fun playHlsStream(streamUrl: String) {
        setupExoPlayer()
        exoPlayerView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        webView.loadUrl("about:blank")

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://geo.dailymotion.com/",
                "Origin" to "https://geo.dailymotion.com"
            ))
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        exoPlayer?.apply {
            setMediaSource(mediaSource)
            playbackParameters = PlaybackParameters(currentSpeed.toFloat())
            prepare()
            playWhenReady = true
        }
    }

    private fun playVideoDirect(videoId: String, title: String) {
        tvCurrentLesson.text = title
        val currentLesson = if (currentLessonIndex in lessons.indices) lessons[currentLessonIndex] else null
        val chPrefix = if (currentLesson != null && currentLesson.chapterTitle.isNotEmpty()) "${currentLesson.chapterTitle} • " else ""
        tvPlayerLessonCounter.text = "${chPrefix}Lecture ${currentLessonIndex + 1} of ${lessons.size}"
        progressBar.visibility = View.VISIBLE

        val rawId = videoId.trim()

        // 1. DIRECT MP4 / HLS / STREAMING URL (100% NATIVE EXOPLAYER - ZERO WATERMARK)
        if (rawId.startsWith("http") && (rawId.contains(".mp4") || rawId.contains(".m3u8") || rawId.contains(".webm"))) {
            playHlsStream(rawId)
            return
        }

        // 2. YOUTUBE UNLISTED / PUBLIC VIDEO (CLEAN EMBED)
        if (rawId.contains("youtube.com") || rawId.contains("youtu.be")) {
            exoPlayer?.pause()
            exoPlayerView.visibility = View.GONE
            webView.visibility = View.VISIBLE

            val ytId = extractYouTubeId(rawId)
            val ytUrl = "https://www.youtube.com/embed/$ytId?autoplay=1&modestbranding=1&rel=0&iv_load_policy=3&controls=1&showinfo=0&fs=1"
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        * { margin:0; padding:0; box-sizing:border-box; }
                        body { background:#000; height:100vh; overflow:hidden; }
                        iframe { width:100%; height:100%; border:none; }
                    </style>
                </head>
                <body>
                    <iframe src="$ytUrl" allow="autoplay; fullscreen; encrypted-media" allowfullscreen></iframe>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            return
        }

        // 3. DAILYMOTION PRIVATE VIDEO -> LOAD SECURE ZERO-BRANDING PLAYER (NO STREAM ERROR / NO WATERMARK)
        val cleanId = if (rawId.contains("/")) rawId.substringAfterLast("/").substringBefore("?") else rawId

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tokenApiUrl = "https://cinemas.mybhiwani.in/api/video.php?video_id=$cleanId"
                val conn = URL(tokenApiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 7000
                conn.readTimeout = 7000

                val dmParams = "autoplay=1&mute=0&controls=1&ui-logo=0&ui-watermark=0&sharing-enable=0&endscreen-enable=0&ui-start-screen-info=0&queue-enable=0"
                var playerUrl = "https://geo.dailymotion.com/player/xtv3w.html?video=$cleanId&$dmParams"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val res = reader.readText()
                    reader.close()

                    val json = JSONObject(res)
                    if (json.optBoolean("status", false)) {
                        val data = json.getJSONObject("data")
                        playerUrl = data.optString("embed_url", playerUrl)
                    }
                }

                withContext(Dispatchers.Main) {
                    exoPlayer?.pause()
                    exoPlayerView.visibility = View.GONE
                    webView.visibility = View.VISIBLE

                    val playerHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                                iframe { width: 100%; height: 100%; border: none; }
                            </style>
                        </head>
                        <body>
                            <iframe
                                src="$playerUrl"
                                allow="autoplay; fullscreen; picture-in-picture; encrypted-media"
                                allowfullscreen>
                            </iframe>
                        </body>
                        </html>
                    """.trimIndent()

                    webView.loadDataWithBaseURL("https://www.dailymotion.com/", playerHtml, "text/html", "UTF-8", null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showToaster("Playback Error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun fetchCourseVideos(cId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL("https://cinemas.mybhiwani.in/api/courses.php?action=detail&id=$cId").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val res = reader.readText()
                    reader.close()

                    val json = JSONObject(res)
                    if (json.getBoolean("status")) {
                        val dataObj = json.getJSONObject("data")
                        val vids = dataObj.optJSONArray("videos") ?: org.json.JSONArray()
                        val chs = dataObj.optJSONArray("chapters")

                        val chapterMap = mutableMapOf<Int, String>()
                        if (chs != null) {
                            for (cIdx in 0 until chs.length()) {
                                val cObj = chs.getJSONObject(cIdx)
                                val cId = cObj.getInt("id")
                                val cTitle = cObj.getString("title")
                                chapterMap[cId] = cTitle
                            }
                        }

                        lessons.clear()

                        for (i in 0 until vids.length()) {
                            val v = vids.getJSONObject(i)
                            val chId = v.optInt("chapter_id", 0)
                            val chTitle = chapterMap[chId] ?: ""
                            lessons.add(
                                VideoLesson(
                                    id = v.getInt("id"),
                                    courseId = cId,
                                    chapterId = chId,
                                    chapterTitle = chTitle,
                                    title = v.getString("title"),
                                    dailymotionId = v.getString("dailymotion_id"),
                                    duration = v.optString("duration", "15:00"),
                                    sortOrder = v.optInt("sort_order", i + 1),
                                    isFreePreview = v.optInt("is_free_preview", 0) == 1
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            tvPlaylistCount.text = "${lessons.size} Lessons"
                            if (lessons.isNotEmpty()) {
                                if (initialVideoId > 0) {
                                    val foundIdx = lessons.indexOfFirst { it.id == initialVideoId }
                                    currentLessonIndex = if (foundIdx != -1) foundIdx else 0
                                } else {
                                    currentLessonIndex = 0
                                }
                                val first = lessons[currentLessonIndex]
                                playVideoDirect(first.dailymotionId, first.title)
                            } else {
                                tvCurrentLesson.text = "No lessons uploaded for this batch yet"
                                tvPlayerLessonCounter.text = "0 Lectures"
                                progressBar.visibility = View.GONE
                            }

                            lessonAdapter = LessonAdapter(lessons) { item: VideoLesson ->
                                currentLessonIndex = lessons.indexOf(item)
                                playVideoDirect(item.dailymotionId, item.title)
                            }
                            rvLessons.adapter = lessonAdapter
                            if (lessons.isNotEmpty()) {
                                lessonAdapter.setSelectedPosition(currentLessonIndex)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showToaster("Error loading lessons: ${e.localizedMessage}")
                }
            }
        }
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    private fun showToaster(msg: String) {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(Color.parseColor("#131B2E"))
            .setTextColor(Color.WHITE)
            .show()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        webView.destroy()
        super.onDestroy()
    }
}

