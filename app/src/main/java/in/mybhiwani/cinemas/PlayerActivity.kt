package `in`.mybhiwani.cinemas

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
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
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
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

    // Native ExoPlayer (Media3 - Ultra-fast HD HLS/MP4 Streaming)
    private var exoPlayer: ExoPlayer? = null
    private lateinit var exoPlayerView: PlayerView

    // Fallback WebView (Authenticated Dailymotion / External Streams)
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
    private lateinit var btnAspectRatioToggle: ImageButton
    private lateinit var layoutLockOverlay: View
    private lateinit var btnUnlockTap: View
    private lateinit var seekLeftBubble: View
    private lateinit var seekRightBubble: View

    private lateinit var lessonAdapter: LessonAdapter
    private val lessons = mutableListOf<VideoLesson>()

    private var courseId: Int = 1
    private var courseTitle: String = "Movie / Episode"
    private var initialVideoId: Int = 0
    private var currentLessonIndex: Int = 0
    private var currentSpeed: Double = 1.0
    private var isFullscreen: Boolean = false
    private var isScreenLocked: Boolean = false
    private var currentResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private lateinit var gestureDetector: GestureDetector
    private val hideHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Anti-Piracy & Anti-Screen Capture (FLAG_SECURE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_player)

        courseId = intent.getIntExtra("COURSE_ID", 1)
        courseTitle = intent.getStringExtra("COURSE_TITLE") ?: "Cinemas Stream"
        initialVideoId = intent.getIntExtra("LESSON_ID", 0)

        initViews()
        setupExoPlayer()
        setupGestures()
        setupControls()
        loadPlaylist()
    }

    private fun initViews() {
        videoContainer = findViewById(R.id.videoContainer)
        exoPlayerView = findViewById(R.id.exoPlayerView)
        webView = findViewById(R.id.webViewPlayer)
        progressBar = findViewById(R.id.playerProgressBar)
        playerTopBar = findViewById(R.id.playerTopBar)
        playerMetaSection = findViewById(R.id.playerMetaSection)
        playlistHeaderSection = findViewById(R.id.playlistHeaderSection)
        rvLessons = findViewById(R.id.rvPlaylistLessons)

        tvPlayerCourseTitle = findViewById(R.id.tvPlayerCourseTitle)
        tvPlayerLessonCounter = findViewById(R.id.tvPlayerLessonCounter)
        tvCurrentLesson = findViewById(R.id.tvCurrentLessonTitle)
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
        lessonAdapter = LessonAdapter(lessons, initialVideoId) { selected ->
            playVideo(selected)
        }
        rvLessons.adapter = lessonAdapter

        setupWebView()
    }

    private fun setupExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        exoPlayerView.player = exoPlayer
        exoPlayerView.resizeMode = currentResizeMode
        exoPlayerView.controllerShowTimeoutMs = 3000
        exoPlayerView.controllerAutoShow = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> progressBar.visibility = View.GONE
                    Player.STATE_ENDED -> {
                        progressBar.visibility = View.GONE
                        playNextVideo()
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                progressBar.visibility = View.GONE
                // Seamless fallback to web embed if direct native streaming stream fails
                if (lessons.isNotEmpty() && currentLessonIndex < lessons.size) {
                    loadInWebView(lessons[currentLessonIndex].dailymotionId)
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 90) progressBar.visibility = View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                val width = videoContainer.width
                if (e.x < width / 2) {
                    // Double Tap Left: Rewind 10s
                    seekRelative(-10000)
                    showSeekFeedback(seekLeftBubble)
                } else {
                    // Double Tap Right: Forward 10s
                    seekRelative(10000)
                    showSeekFeedback(seekRightBubble)
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) {
                    layoutLockOverlay.visibility = View.VISIBLE
                    hideHandler.removeCallbacksAndMessages(null)
                    hideHandler.postDelayed({ layoutLockOverlay.visibility = View.GONE }, 2500)
                    return true
                }
                return false
            }
        })

        videoContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun setupControls() {
        findViewById<View>(R.id.btnPlayerBack).setOnClickListener { finish() }

        btnSpeedToggle.setOnClickListener {
            togglePlaybackSpeed()
        }

        btnFullscreenToggle.setOnClickListener {
            toggleFullscreen()
        }

        btnLockToggle.setOnClickListener {
            toggleScreenLock()
        }

        btnUnlockTap.setOnClickListener {
            unlockScreen()
        }
    }

    private fun togglePlaybackSpeed() {
        currentSpeed = when (currentSpeed) {
            1.0 -> 1.25
            1.25 -> 1.5
            1.5 -> 2.0
            2.0 -> 0.75
            else -> 1.0
        }
        btnSpeedToggle.text = "${currentSpeed}x"
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed.toFloat())
        Snackbar.make(videoContainer, "Speed: ${currentSpeed}x", Snackbar.LENGTH_SHORT).show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_exit)
            enterFullscreenMode()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen)
            exitFullscreenMode()
        }
    }

    private fun enterFullscreenMode() {
        playerTopBar.visibility = View.GONE
        playerMetaSection.visibility = View.GONE
        playlistHeaderSection.visibility = View.GONE
        rvLessons.visibility = View.GONE

        val params = videoContainer.layoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        videoContainer.layoutParams = params

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun exitFullscreenMode() {
        playerTopBar.visibility = View.VISIBLE
        playerMetaSection.visibility = View.VISIBLE
        playlistHeaderSection.visibility = View.VISIBLE
        rvLessons.visibility = View.VISIBLE

        val height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 220f, resources.displayMetrics
        ).toInt()

        val params = videoContainer.layoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = height
        videoContainer.layoutParams = params

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun toggleScreenLock() {
        isScreenLocked = true
        layoutLockOverlay.visibility = View.VISIBLE
        Snackbar.make(videoContainer, "Screen Locked. Tap unlock icon to resume controls.", Snackbar.LENGTH_SHORT).show()
        hideHandler.postDelayed({ layoutLockOverlay.visibility = View.GONE }, 2500)
    }

    private fun unlockScreen() {
        isScreenLocked = false
        layoutLockOverlay.visibility = View.GONE
        Snackbar.make(videoContainer, "Controls Unlocked", Snackbar.LENGTH_SHORT).show()
    }

    private fun seekRelative(offsetMs: Long) {
        exoPlayer?.let {
            val target = (it.currentPosition + offsetMs).coerceIn(0, it.duration.coerceAtLeast(0))
            it.seekTo(target)
        }
    }

    private fun showSeekFeedback(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.animate()
            .alpha(0f)
            .setDuration(600)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    private fun playVideo(lesson: VideoLesson) {
        currentLessonIndex = lessons.indexOfFirst { it.id == lesson.id }.coerceAtLeast(0)
        lessonAdapter.updateCurrentVideoId(lesson.id)

        tvCurrentLesson.text = lesson.title
        tvPlayerLessonCounter.text = if (lessons.size > 1) "Stream ${currentLessonIndex + 1} of ${lessons.size}" else "Full Feature"

        progressBar.visibility = View.VISIBLE

        val rawId = lesson.dailymotionId.trim()

        // 1. Direct HLS / MP4 Stream Playback with ExoPlayer
        if (rawId.startsWith("http") && (rawId.contains(".m3u8") || rawId.contains(".mp4") || rawId.contains(".webm"))) {
            playDirectUrl(rawId)
            return
        }

        // 2. Fetch authenticated HLS streaming token or embed via cinemas.mybhiwani.in API
        fetchAndPlayStream(rawId)
    }

    private fun playDirectUrl(streamUrl: String) {
        webView.visibility = View.GONE
        exoPlayerView.visibility = View.VISIBLE

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "CinemasApp/1.0 (Android Native)",
                    "Referer" to "https://cinemas.mybhiwani.in/"
                )
            )

        val uri = Uri.parse(streamUrl)
        val mediaSource = if (streamUrl.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
        } else {
            androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        }

        exoPlayer?.stop()
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    private fun fetchAndPlayStream(cleanId: String) {
        lifecycleScope.launch {
            try {
                val tokenApiUrl = "https://cinemas.mybhiwani.in/api/video.php?video_id=$cleanId"
                val response = withContext(Dispatchers.IO) {
                    val conn = URL(tokenApiUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.inputStream.bufferedReader().use { it.readText() }
                }

                val json = JSONObject(response)
                val data = json.optJSONObject("data")

                val hlsStream = data?.optString("hls_url", "")
                if (!hlsStream.isNullOrEmpty() && hlsStream.startsWith("http")) {
                    playDirectUrl(hlsStream)
                } else {
                    val embedUrl = data?.optString("embed_url", "https://geo.dailymotion.com/player/xtv3w.html?video=$cleanId&autoplay=1&mute=0&controls=1&ui-logo=0&ui-watermark=0&sharing-enable=0")
                    loadInWebViewWithUrl(embedUrl ?: "https://geo.dailymotion.com/player/xtv3w.html?video=$cleanId")
                }
            } catch (e: Exception) {
                loadInWebView(cleanId)
            }
        }
    }

    private fun loadInWebView(cleanId: String) {
        val dmParams = "autoplay=1&mute=0&controls=1&ui-logo=0&ui-watermark=0&sharing-enable=0&endscreen-enable=0&ui-start-screen-info=0"
        val playerUrl = "https://geo.dailymotion.com/player/xtv3w.html?video=$cleanId&$dmParams"
        loadInWebViewWithUrl(playerUrl)
    }

    private fun loadInWebViewWithUrl(playerUrl: String) {
        exoPlayer?.stop()
        exoPlayerView.visibility = View.GONE
        webView.visibility = View.VISIBLE

        val playerHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background: #000; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                    iframe { width: 100%; height: 100%; border: none; }
                </style>
            </head>
            <body>
                <iframe src="$playerUrl" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://cinemas.mybhiwani.in/", playerHtml, "text/html", "UTF-8", null)
    }

    private fun playNextVideo() {
        if (currentLessonIndex + 1 < lessons.size) {
            playVideo(lessons[currentLessonIndex + 1])
        }
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            try {
                val cId = courseId
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("https://cinemas.mybhiwani.in/api/courses.php?action=detail&id=$cId").openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.inputStream.bufferedReader().use { it.readText() }
                }

                val json = JSONObject(result)
                if (json.optBoolean("status", false)) {
                    val data = json.getJSONObject("data")
                    val chaptersArr = data.optJSONArray("seasons") ?: data.optJSONArray("chapters")

                    lessons.clear()
                    if (chaptersArr != null) {
                        for (i in 0 until chaptersArr.length()) {
                            val cObj = chaptersArr.getJSONObject(i)
                            val vArr = cObj.optJSONArray("episodes") ?: cObj.optJSONArray("videos")
                            if (vArr != null) {
                                for (j in 0 until vArr.length()) {
                                    val vObj = vArr.getJSONObject(j)
                                    lessons.add(
                                        VideoLesson(
                                            id = vObj.optInt("id", 0),
                                            title = vObj.optString("title", "Episode ${j + 1}"),
                                            description = vObj.optString("description", ""),
                                            dailymotionId = vObj.optString("dailymotion_id", ""),
                                            duration = vObj.optString("duration", "45:00"),
                                            sortOrder = vObj.optInt("sort_order", j + 1),
                                            isFreePreview = true
                                        )
                                    )
                                }
                            }
                        }
                    }

                    tvPlaylistCount.text = "${lessons.size} Available"
                    lessonAdapter.notifyDataSetChanged()

                    val target = if (initialVideoId > 0) {
                        lessons.find { it.id == initialVideoId } ?: lessons.firstOrNull()
                    } else {
                        lessons.firstOrNull()
                    }

                    target?.let { playVideo(it) }
                }
            } catch (e: Exception) {
                Snackbar.make(videoContainer, "Could not load playlist", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isFullscreen = true
            btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_exit)
            enterFullscreenMode()
        } else {
            isFullscreen = false
            btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen)
            exitFullscreenMode()
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        webView.destroy()
    }
}
