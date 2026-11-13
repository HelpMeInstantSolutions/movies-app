package `in`.mybhiwani.cinemas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import `in`.mybhiwani.cinemas.R

class MediaDetailActivity : AppCompatActivity() {

    private var mediaId: Int = 1
    private var mediaTitle: String = "Movie / Series"
    private var mediaDesc: String = ""
    private var mediaType: String = "movie"

    private lateinit var tvTopMediaTitle: TextView
    private lateinit var tvTopMediaSubtitle: TextView
    private lateinit var tvHeaderMediaTitle: TextView
    private lateinit var tvHeaderMediaDesc: TextView
    private lateinit var tvMediaStats: TextView
    private lateinit var tvSeasonCountPill: TextView
    private lateinit var seriesSectionHeader: View
    private lateinit var btnStartStreaming: Button
    private lateinit var mediaLoader: ProgressBar
    private lateinit var layoutEmptyMedia: View
    private lateinit var rvSeasons: RecyclerView

    private val seasonsList = mutableListOf<MediaSeason>()
    private var allEpisodesList = mutableListOf<MediaEpisode>()
    private lateinit var seasonAdapter: SeasonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Anti-Piracy Flag (FLAG_SECURE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_media_detail)

        mediaId = intent.getIntExtra("MEDIA_ID", intent.getIntExtra("COURSE_ID", 1))
        mediaTitle = intent.getStringExtra("MEDIA_TITLE") ?: (intent.getStringExtra("COURSE_TITLE") ?: "Cinema Showcase")
        mediaDesc = intent.getStringExtra("MEDIA_DESC") ?: (intent.getStringExtra("COURSE_DESC") ?: "")
        mediaType = intent.getStringExtra("MEDIA_TYPE") ?: "movie"

        initViews()
        loadMediaDetails()
    }

    private fun initViews() {
        val btnBack = findViewById<ImageButton>(R.id.btnBackFromDetails)
        tvTopMediaTitle = findViewById(R.id.tvTopMediaTitle)
        tvTopMediaSubtitle = findViewById(R.id.tvTopMediaSubtitle)
        tvHeaderMediaTitle = findViewById(R.id.tvHeaderMediaTitle)
        tvHeaderMediaDesc = findViewById(R.id.tvHeaderMediaDesc)
        tvMediaStats = findViewById(R.id.tvMediaStats)
        tvSeasonCountPill = findViewById(R.id.tvSeasonCountPill)
        seriesSectionHeader = findViewById(R.id.seriesSectionHeader)
        btnStartStreaming = findViewById(R.id.btnStartStreaming)
        mediaLoader = findViewById(R.id.mediaDetailLoader)
        layoutEmptyMedia = findViewById(R.id.layoutEmptyMedia)
        rvSeasons = findViewById(R.id.rvSeasons)

        if (mediaType == "series") {
            tvTopMediaSubtitle.text = "Seasons & Episodes"
            btnStartStreaming.text = "Watch S1:E1 ▶"
            seriesSectionHeader.visibility = View.VISIBLE
        } else {
            tvTopMediaSubtitle.text = "Full Feature Movie"
            btnStartStreaming.text = "Play Movie Now ▶"
            seriesSectionHeader.visibility = View.GONE
        }

        tvTopMediaTitle.text = mediaTitle
        tvHeaderMediaTitle.text = mediaTitle
        tvHeaderMediaDesc.text = mediaDesc.ifEmpty { "High-definition streaming exclusively on Cinemas App." }

        btnBack.setOnClickListener { finish() }

        rvSeasons.layoutManager = LinearLayoutManager(this)
        seasonAdapter = SeasonAdapter(seasonsList) { selectedEpisode ->
            openPlayer(selectedEpisode)
        }
        rvSeasons.adapter = seasonAdapter

        btnStartStreaming.setOnClickListener {
            if (allEpisodesList.isNotEmpty()) {
                openPlayer(allEpisodesList[0])
            } else {
                Snackbar.make(rvSeasons, "No streamable video found yet", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMediaDetails() {
        mediaLoader.visibility = View.VISIBLE
        layoutEmptyMedia.visibility = View.GONE
        rvSeasons.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val mId = mediaId
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("https://cinemas.mybhiwani.in/api/movies.php?action=detail&id=$mId").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000

                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                    sb.toString()
                }

                val json = JSONObject(result)
                if (json.optBoolean("status", false)) {
                    val data = json.getJSONObject("data")
                    val seasonsArr = data.optJSONArray("seasons") ?: data.optJSONArray("chapters")

                    seasonsList.clear()
                    allEpisodesList.clear()
                    var totalEpCount = 0

                    if (seasonsArr != null) {
                        for (i in 0 until seasonsArr.length()) {
                            val sObj = seasonsArr.getJSONObject(i)
                            val sId = sObj.optInt("id", 0)
                            val sTitle = sObj.optString("title", "Season ${i + 1}")
                            val sDesc = sObj.optString("description", "")
                            val sNum = sObj.optInt("season_number", sObj.optInt("sort_order", i + 1))

                            val epArr = sObj.optJSONArray("episodes") ?: sObj.optJSONArray("videos")
                            val episodesInSeason = mutableListOf<MediaEpisode>()

                            if (epArr != null) {
                                for (j in 0 until epArr.length()) {
                                    val epObj = epArr.getJSONObject(j)
                                    val episode = MediaEpisode(
                                        id = epObj.optInt("id", 0),
                                        seriesId = mediaId,
                                        seasonId = sId,
                                        seasonTitle = sTitle,
                                        title = epObj.optString("title", "Episode ${j + 1}"),
                                        description = epObj.optString("description", ""),
                                        dailymotionId = epObj.optString("dailymotion_id", ""),
                                        duration = epObj.optString("duration", "45:00"),
                                        episodeNumber = epObj.optInt("episode_number", epObj.optInt("sort_order", j + 1)),
                                        isFreePreview = epObj.optInt("is_free_preview", 1) == 1
                                    )
                                    episodesInSeason.add(episode)
                                    allEpisodesList.add(episode)
                                }
                            }

                            totalEpCount += episodesInSeason.size
                            seasonsList.add(
                                MediaSeason(
                                    id = sId,
                                    seriesId = mediaId,
                                    title = sTitle,
                                    description = sDesc,
                                    seasonNumber = sNum,
                                    totalEpisodes = episodesInSeason.size,
                                    episodes = episodesInSeason,
                                    isExpanded = (i == 0)
                                )
                            )
                        }
                    }

                    mediaLoader.visibility = View.GONE

                    if (mediaType == "movie") {
                        tvMediaStats.text = "HD 1080p • Dolby Atmos Audio"
                        tvSeasonCountPill.text = "Feature Film"
                        seriesSectionHeader.visibility = View.GONE
                        layoutEmptyMedia.visibility = View.GONE
                        rvSeasons.visibility = View.GONE
                        btnStartStreaming.visibility = View.VISIBLE
                    } else if (seasonsList.isEmpty() || totalEpCount == 0) {
                        layoutEmptyMedia.visibility = View.VISIBLE
                        rvSeasons.visibility = View.GONE
                        tvMediaStats.text = "0 Seasons • 0 Episodes"
                        tvSeasonCountPill.text = "0 Streams"
                        btnStartStreaming.visibility = View.GONE
                    } else {
                        layoutEmptyMedia.visibility = View.GONE
                        rvSeasons.visibility = View.VISIBLE
                        seriesSectionHeader.visibility = View.VISIBLE
                        btnStartStreaming.visibility = View.VISIBLE
                        tvMediaStats.text = "${seasonsList.size} Seasons • $totalEpCount Episodes"
                        tvSeasonCountPill.text = "${seasonsList.size} Seasons"
                        seasonAdapter.notifyDataSetChanged()
                    }
                } else {
                    showError(json.optString("message", "Failed to load details"))
                }
            } catch (e: Exception) {
                showError("Network error: ${e.localizedMessage}")
            }
        }
    }

    private fun showError(msg: String) {
        mediaLoader.visibility = View.GONE
        layoutEmptyMedia.visibility = View.VISIBLE
        rvSeasons.visibility = View.GONE
        Snackbar.make(rvSeasons, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun openPlayer(episode: MediaEpisode) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("MEDIA_ID", mediaId)
            putExtra("COURSE_ID", mediaId)
            putExtra("MEDIA_TITLE", mediaTitle)
            putExtra("COURSE_TITLE", mediaTitle)
            putExtra("MEDIA_TYPE", mediaType)
            putExtra("EPISODE_ID", episode.id)
            putExtra("LESSON_ID", episode.id)
            putExtra("EPISODE_TITLE", episode.title)
            putExtra("LESSON_TITLE", episode.title)
            putExtra("DAILYMOTION_ID", episode.dailymotionId)
            putExtra("EPISODE_DURATION", episode.duration)
        }
        startActivity(intent)
    }
}

// Legacy alias so Manifest and external intents seamlessly resolve
typealias ChaptersActivity = MediaDetailActivity
