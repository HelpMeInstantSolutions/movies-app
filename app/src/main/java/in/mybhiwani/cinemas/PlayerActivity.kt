package `in`.mybhiwani.cinemas

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import android.webkit.JavascriptInterface
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
import java.net.HttpURLConnection
import java.net.URL
import `in`.mybhiwani.cinemas.R

class PlayerActivity : AppCompatActivity() {

    // Native ExoPlayer (Media3 - Seamless multi-stream gapless playback)
    private var exoPlayer: ExoPlayer? = null
    private lateinit var exoPlayerView: PlayerView

    // Fallback WebView (Authenticated Dailymotion / Cloud Streams)
    private lateinit var webView: WebView

    private lateinit var progressBar: ProgressBar
    private lateinit var videoContainer: RelativeLayout
    private lateinit var playerTopBar: View
    private lateinit var playerMetaSection: View
    private lateinit var playlistHeaderSection: View
    private lateinit var rvStreamsList: RecyclerView

    private lateinit var tvPlayerMediaTitle: TextView
    private lateinit var tvPlayerStreamCounter: TextView
    private lateinit var tvCurrentStreamTitle: TextView
    private lateinit var tvPlaylistCount: TextView
    private lateinit var btnNextStream: Button

    private lateinit var btnSpeedToggle: TextView
    private lateinit var btnLockToggle: ImageButton
    private lateinit var btnFullscreenToggle: ImageButton
    private lateinit var layoutLockOverlay: View
    private lateinit var btnUnlockTap: View
    private lateinit var seekLeftBubble: View
    private lateinit var seekRightBubble: View

    private lateinit var episodeAdapter: EpisodeAdapter
    private val streamsList = mutableListOf<MediaEpisode>()

    private var mediaId: Int = 1
    private var mediaTitle: String = "Cinema Stream"
    private var mediaType: String = "movie"
    private var initialVideoId: Int = 0
    private var currentStreamIndex: Int = 0
    private var currentSpeed: Double = 1.0
    private var isFullscreen: Boolean = false
    private var isScreenLocked: Boolean = false
    private var isMovieMode: Boolean = true
    private var lastStreamStartTimeMs: Long = 0L

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

        mediaId = intent.getIntExtra("MEDIA_ID", intent.getIntExtra("COURSE_ID", 1))
        mediaTitle = intent.getStringExtra("MEDIA_TITLE") ?: (intent.getStringExtra("COURSE_TITLE") ?: "Cinemas Stream")
        mediaType = intent.getStringExtra("MEDIA_TYPE") ?: "movie"
        isMovieMode = (mediaType == "movie")
        initialVideoId = intent.getIntExtra("EPISODE_ID", intent.getIntExtra("LESSON_ID", 0))

        initViews()
        setupExoPlayer()
        setupGestures()
        setupControls()
        loadPlaylist()
    }

    private fun initViews() {
        videoContainer = findViewById(R.id.videoContainer)
        exoPlayerView = findViewById(R.id.exoPlayerView)
        webView = findViewById(R.id.playerWebView)
        progressBar = findViewById(R.id.playerLoader)
        playerTopBar = findViewById(R.id.playerTopBar)
        playerMetaSection = findViewById(R.id.playerMetaSection)
        playlistHeaderSection = findViewById(R.id.playlistHeaderSection)
        rvStreamsList = findViewById(R.id.rvStreamsList)

        tvPlayerMediaTitle = findViewById(R.id.tvPlayerMediaTitle)
        tvPlayerStreamCounter = findViewById(R.id.tvPlayerStreamCounter)
        tvCurrentStreamTitle = findViewById(R.id.tvCurrentStreamTitle)
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount)
        btnNextStream = findViewById(R.id.btnNextStream)

        btnSpeedToggle = findViewById(R.id.btnSpeedToggle)
        btnLockToggle = findViewById(R.id.btnLockToggle)
        btnFullscreenToggle = findViewById(R.id.btnFullscreenToggle)
        layoutLockOverlay = findViewById(R.id.layoutLockOverlay)
        btnUnlockTap = findViewById(R.id.btnUnlockTap)
        seekLeftBubble = findViewById(R.id.seekLeftBubble)
        seekRightBubble = findViewById(R.id.seekRightBubble)

        tvPlayerMediaTitle.text = mediaTitle
        tvCurrentStreamTitle.text = mediaTitle
        tvPlayerStreamCounter.text = if (isMovieMode) "Full Feature Film" else "Loading streams..."

        if (isMovieMode) {
            playlistHeaderSection.visibility = View.GONE
            rvStreamsList.visibility = View.GONE
            btnNextStream.visibility = View.GONE
        }

        rvStreamsList.layoutManager = LinearLayoutManager(this)
        episodeAdapter = EpisodeAdapter(streamsList, initialVideoId) { selected ->
            playStream(selected)
        }
        rvStreamsList.adapter = episodeAdapter

        setupWebView()
    }

    private fun setupExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        exoPlayerView.player = exoPlayer
        exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        exoPlayerView.controllerShowTimeoutMs = 3000
        exoPlayerView.controllerAutoShow = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> progressBar.visibility = View.GONE
                    Player.STATE_ENDED -> {
                        progressBar.visibility = View.GONE
                        // Seamless auto-play next link/part (User does not feel it's 2 separate videos)
                        playNextStream()
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                progressBar.visibility = View.GONE
                if (streamsList.isNotEmpty() && currentStreamIndex < streamsList.size) {
                    loadInWebView(streamsList[currentStreamIndex].dailymotionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val newIndex = exoPlayer?.currentMediaItemIndex ?: 0
                if (newIndex in streamsList.indices && newIndex != currentStreamIndex) {
                    currentStreamIndex = newIndex
                    val ep = streamsList[newIndex]
                    episodeAdapter.updateCurrentVideoId(ep.id)
                    tvCurrentStreamTitle.text = if (isMovieMode) mediaTitle else ep.title
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

        // JavaScript Interface so Dailymotion ended event triggers seamless next part in Android
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

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

    inner class WebAppInterface {
        @JavascriptInterface
        fun onVideoEnded() {
            val elapsed = System.currentTimeMillis() - lastStreamStartTimeMs
            // Must have been playing for at least 10 seconds to prevent premature skip on ads or init
            if (elapsed < 10000) {
                return
            }
            runOnUiThread {
                // Seamlessly advance to Part 2 / next stream
                playNextStream()
            }
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                val width = videoContainer.width
                if (e.x < width / 2) {
                    seekRelative(-10000)
                    showSeekFeedback(seekLeftBubble)
                } else {
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
        findViewById<View>(R.id.btnBackFromPlayer).setOnClickListener { finish() }

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

        btnNextStream.setOnClickListener {
            playNextStream()
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
        rvStreamsList.visibility = View.GONE

        val params = videoContainer.layoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        videoContainer.layoutParams = params

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun exitFullscreenMode() {
        playerTopBar.visibility = View.VISIBLE
        playerMetaSection.visibility = View.VISIBLE

        if (!isMovieMode) {
            playlistHeaderSection.visibility = View.VISIBLE
            rvStreamsList.visibility = View.VISIBLE
        }

        val height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 225f, resources.displayMetrics
        ).toInt()

        val params = videoContainer.layoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = height
        videoContainer.layoutParams = params

        @Suppress("DEPRECATION")
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

    private fun parseDurationToSeconds(durStr: String): Int {
        val s = durStr.trim()
        val parts = s.split(":")
        return when (parts.size) {
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            else -> 0
        }
    }

    /**
     * Seamless multi-link gapless playback:
     * If a movie has multiple parts/links, they are played continuously one after another.
     * The user is shown ONLY the movie title - never "Part 1" or "Part 2" so they feel it's 1 movie!
     */
    private fun playStream(episode: MediaEpisode) {
        currentStreamIndex = streamsList.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        episodeAdapter.updateCurrentVideoId(episode.id)
        lastStreamStartTimeMs = System.currentTimeMillis()

        // For movie mode: Always keep Movie Title!
        tvCurrentStreamTitle.text = if (isMovieMode) mediaTitle else episode.title

        progressBar.visibility = View.VISIBLE

        val rawId = episode.dailymotionId.trim()

        if (rawId.startsWith("http") && (rawId.contains(".m3u8") || rawId.contains(".mp4") || rawId.contains(".webm"))) {
            playDirectUrl(rawId)
            return
        }

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
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.inputStream.bufferedReader().use { it.readText() }
                }

                val json = JSONObject(response)
                val data = json.optJSONObject("data")

                val hlsStream = data?.optString("hls_url", "")
                if (!hlsStream.isNullOrEmpty() && hlsStream.startsWith("http")) {
                    playDirectUrl(hlsStream)
                } else {
                    val embedUrl = data?.optString("embed_url", "")
                    if (!embedUrl.isNullOrEmpty() && embedUrl.startsWith("http")) {
                        loadInWebViewWithUrl(embedUrl)
                    } else {
                        loadInWebView(cleanId)
                    }
                }
            } catch (e: Exception) {
                loadInWebView(cleanId)
            }
        }
    }

    private fun loadInWebView(cleanId: String) {
        val dmParams = "autoplay=1&mute=0&controls=1&ui-logo=0&ui-watermark=0&sharing-enable=0&endscreen-enable=0&ui-start-screen-info=0&queue-enable=0"
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
                <iframe id="dmp" src="$playerUrl" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen></iframe>
                <script>
                    window.addEventListener('message', function(e) {
                        try {
                            var data = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
                            if (data) {
                                var ev = data.event || data.type || '';
                                if (ev === 'video_end' || ev === 'ended') {
                                    if (window.AndroidBridge && window.AndroidBridge.onVideoEnded) {
                                        window.AndroidBridge.onVideoEnded();
                                    }
                                }
                            }
                        } catch(err){}
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://cinemas.mybhiwani.in/", playerHtml, "text/html", "UTF-8", null)
    }

    private fun playNextStream() {
        if (currentStreamIndex + 1 < streamsList.size) {
            val nextEp = streamsList[currentStreamIndex + 1]
            if (isMovieMode) {
                Snackbar.make(videoContainer, "Continuing Feature Film...", Snackbar.LENGTH_SHORT).show()
            }
            playStream(nextEp)
        } else {
            if (isMovieMode) {
                Snackbar.make(videoContainer, "Movie Finished", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            try {
                val mId = mediaId
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("https://cinemas.mybhiwani.in/api/movies.php?action=detail&id=$mId").openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.inputStream.bufferedReader().use { it.readText() }
                }

                val json = JSONObject(result)
                if (json.optBoolean("status", false)) {
                    val data = json.getJSONObject("data")
                    val mType = data.optString("type", mediaType)
                    isMovieMode = (mType == "movie")

                    val seasonsArr = data.optJSONArray("seasons") ?: data.optJSONArray("chapters")

                    streamsList.clear()
                    if (seasonsArr != null) {
                        for (i in 0 until seasonsArr.length()) {
                            val sObj = seasonsArr.getJSONObject(i)
                            val epArr = sObj.optJSONArray("episodes") ?: sObj.optJSONArray("videos")
                            if (epArr != null) {
                                for (j in 0 until epArr.length()) {
                                    val epObj = epArr.getJSONObject(j)
                                    streamsList.add(
                                        MediaEpisode(
                                            id = epObj.optInt("id", 0),
                                            seriesId = mediaId,
                                            seasonId = sObj.optInt("id", 1),
                                            seasonTitle = sObj.optString("title", "Main Feature"),
                                            title = epObj.optString("title", "Episode ${j + 1}"),
                                            description = epObj.optString("description", ""),
                                            dailymotionId = epObj.optString("dailymotion_id", ""),
                                            duration = epObj.optString("duration", "45:00"),
                                            episodeNumber = epObj.optInt("episode_number", epObj.optInt("sort_order", j + 1)),
                                            sortOrder = epObj.optInt("sort_order", j + 1),
                                            isFreePreview = true
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Strict sorting: Part 1 MUST be index 0
                    streamsList.sortWith(compareBy({ it.episodeNumber }, { it.sortOrder }, { it.id }))

                    // Calculate total combined runtime for movies
                    var totalMovieSeconds = 0
                    for (s in streamsList) {
                        totalMovieSeconds += parseDurationToSeconds(s.duration)
                    }

                    val formattedTotalRuntime = if (totalMovieSeconds > 0) {
                        val hrs = totalMovieSeconds / 3600
                        val mins = (totalMovieSeconds % 3600) / 60
                        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                    } else {
                        data.optString("duration", "2h 44m")
                    }

                    // For Movie with multiple links: Seamlessly hide playlist list so user feels it's 1 single movie
                    if (isMovieMode) {
                        playlistHeaderSection.visibility = View.GONE
                        rvStreamsList.visibility = View.GONE
                        btnNextStream.visibility = View.GONE
                        tvPlayerStreamCounter.text = "Full Feature Film • $formattedTotalRuntime"
                        tvCurrentStreamTitle.text = mediaTitle
                    } else {
                        playlistHeaderSection.visibility = View.VISIBLE
                        rvStreamsList.visibility = View.VISIBLE
                        btnNextStream.visibility = View.VISIBLE
                        tvPlaylistCount.text = "${streamsList.size} Episodes"
                    }

                    episodeAdapter.notifyDataSetChanged()

                    // For movie mode: ALWAYS start from Part 1 (first item)
                    val target = if (isMovieMode) {
                        streamsList.firstOrNull()
                    } else {
                        if (initialVideoId > 0) streamsList.find { it.id == initialVideoId } ?: streamsList.firstOrNull()
                        else streamsList.firstOrNull()
                    }

                    target?.let { playStream(it) }
                }
            } catch (e: Exception) {
                Snackbar.make(videoContainer, "Could not load streams", Snackbar.LENGTH_SHORT).show()
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
