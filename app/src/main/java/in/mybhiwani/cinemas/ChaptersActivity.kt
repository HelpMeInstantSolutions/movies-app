package `in`.mybhiwani.courseapp

import android.content.Intent
import android.graphics.Color
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

class ChaptersActivity : AppCompatActivity() {

    private var courseId: Int = 1
    private var courseTitle: String = "Movie / Series"
    private var courseDesc: String = ""
    private var mediaType: String = "movie"

    private lateinit var tvTopCourseTitle: TextView
    private lateinit var tvHeaderCourseTitle: TextView
    private lateinit var tvHeaderCourseDesc: TextView
    private lateinit var tvCurriculumStats: TextView
    private lateinit var tvChapterCountPill: TextView
    private lateinit var btnStartLearning: Button
    private lateinit var chaptersLoader: ProgressBar
    private lateinit var layoutEmptyChapters: View
    private lateinit var rvChapters: RecyclerView

    private val chaptersList = mutableListOf<Chapter>()
    private var allVideosList = mutableListOf<VideoLesson>()
    private lateinit var chapterAdapter: ChapterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Anti-Screen Capture & Anti-Piracy Flag
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_chapters)

        courseId = intent.getIntExtra("COURSE_ID", 1)
        courseTitle = intent.getStringExtra("COURSE_TITLE") ?: "Cinema Showcase"
        courseDesc = intent.getStringExtra("COURSE_DESC") ?: ""
        mediaType = intent.getStringExtra("MEDIA_TYPE") ?: "movie"

        initViews()
        loadCurriculum()
    }

    private fun initViews() {
        val btnBack = findViewById<ImageButton>(R.id.btnBackFromChapters)
        tvTopCourseTitle = findViewById(R.id.tvTopCourseTitle)
        val tvTopSubtitle = findViewById<TextView>(R.id.tvTopCourseSubtitle)
        tvHeaderCourseTitle = findViewById(R.id.tvHeaderCourseTitle)
        tvHeaderCourseDesc = findViewById(R.id.tvHeaderCourseDesc)
        tvCurriculumStats = findViewById(R.id.tvCurriculumStats)
        tvChapterCountPill = findViewById(R.id.tvChapterCountPill)
        btnStartLearning = findViewById(R.id.btnStartLearning)
        chaptersLoader = findViewById(R.id.chaptersLoader)
        layoutEmptyChapters = findViewById(R.id.layoutEmptyChapters)
        rvChapters = findViewById(R.id.rvChapters)

        val tvSectionTitle = findViewById<TextView>(R.id.tvSectionTitle)
        if (mediaType == "series") {
            tvTopSubtitle.text = "Seasons & Episodes"
            tvSectionTitle.text = "Series Seasons"
            btnStartLearning.text = "Watch S1:E1 ▶"
        } else {
            tvTopSubtitle.text = "Full Movie Streaming"
            tvSectionTitle.text = "Streaming Sources"
            btnStartLearning.text = "Play Movie Now ▶"
        }

        tvTopCourseTitle.text = courseTitle
        tvHeaderCourseTitle.text = courseTitle
        tvHeaderCourseDesc.text = courseDesc.ifEmpty { "High-definition streaming exclusively on Cinemas App." }

        btnBack.setOnClickListener { finish() }

        rvChapters.layoutManager = LinearLayoutManager(this)
        chapterAdapter = ChapterAdapter(this, chaptersList) { selectedVideo ->
            openPlayer(selectedVideo)
        }
        rvChapters.adapter = chapterAdapter

        btnStartLearning.setOnClickListener {
            if (allVideosList.isNotEmpty()) {
                openPlayer(allVideosList[0])
            } else {
                Snackbar.make(rvChapters, "No streamable video found yet", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCurriculum() {
        chaptersLoader.visibility = View.VISIBLE
        layoutEmptyChapters.visibility = View.GONE
        rvChapters.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val cId = courseId
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("https://cinemas.mybhiwani.in/api/courses.php?action=detail&id=$cId").openConnection() as HttpURLConnection
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
                    val chaptersArr = data.optJSONArray("chapters") ?: data.optJSONArray("seasons")

                    chaptersList.clear()
                    allVideosList.clear()

                    var totalLessonsCount = 0

                    if (chaptersArr != null) {
                        for (i in 0 until chaptersArr.length()) {
                            val cObj = chaptersArr.getJSONObject(i)
                            val cIdLocal = cObj.optInt("id", 0)
                            val cTitle = cObj.optString("title", "Season ${i + 1}")
                            val cDesc = cObj.optString("description", "")
                            val cSort = cObj.optInt("sort_order", cObj.optInt("season_number", i + 1))

                            val vArr = cObj.optJSONArray("videos") ?: cObj.optJSONArray("episodes")
                            val lessonsInChapter = mutableListOf<VideoLesson>()

                            if (vArr != null) {
                                for (j in 0 until vArr.length()) {
                                    val vObj = vArr.getJSONObject(j)
                                    val lesson = VideoLesson(
                                        id = vObj.optInt("id", 0),
                                        title = vObj.optString("title", "Episode ${j + 1}"),
                                        description = vObj.optString("description", ""),
                                        dailymotionId = vObj.optString("dailymotion_id", ""),
                                        duration = vObj.optString("duration", "45:00"),
                                        sortOrder = vObj.optInt("sort_order", vObj.optInt("episode_number", j + 1)),
                                        isFreePreview = vObj.optInt("is_free_preview", 1) == 1
                                    )
                                    lessonsInChapter.add(lesson)
                                    allVideosList.add(lesson)
                                }
                            }

                            totalLessonsCount += lessonsInChapter.size
                            chaptersList.add(
                                Chapter(
                                    id = cIdLocal,
                                    courseId = courseId,
                                    title = cTitle,
                                    description = cDesc,
                                    sortOrder = cSort,
                                    totalVideos = lessonsInChapter.size,
                                    videos = lessonsInChapter,
                                    isExpanded = (i == 0) // First season expanded by default
                                )
                            )
                        }
                    }

                    chaptersLoader.visibility = View.GONE

                    if (chaptersList.isEmpty() || totalLessonsCount == 0) {
                        layoutEmptyChapters.visibility = View.VISIBLE
                        rvChapters.visibility = View.GONE
                        tvCurriculumStats.text = if (mediaType == "series") "0 Seasons • 0 Episodes" else "Movie Stream Unavailable"
                        tvChapterCountPill.text = "0 Streams"
                        btnStartLearning.visibility = View.GONE
                    } else {
                        layoutEmptyChapters.visibility = View.GONE
                        rvChapters.visibility = View.VISIBLE
                        btnStartLearning.visibility = View.VISIBLE

                        if (mediaType == "series") {
                            tvCurriculumStats.text = "${chaptersList.size} Seasons • $totalLessonsCount Episodes"
                            tvChapterCountPill.text = "${chaptersList.size} Seasons"
                        } else {
                            tvCurriculumStats.text = "HD 1080p • Dolby Atmos Audio"
                            tvChapterCountPill.text = "Feature Film"
                        }

                        chapterAdapter.notifyDataSetChanged()
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
        chaptersLoader.visibility = View.GONE
        layoutEmptyChapters.visibility = View.VISIBLE
        rvChapters.visibility = View.GONE
        Snackbar.make(rvChapters, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun openPlayer(lesson: VideoLesson) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("COURSE_ID", courseId)
            putExtra("COURSE_TITLE", courseTitle)
            putExtra("LESSON_ID", lesson.id)
            putExtra("LESSON_TITLE", lesson.title)
            putExtra("DAILYMOTION_ID", lesson.dailymotionId)
            putExtra("LESSON_DURATION", lesson.duration)
        }
        startActivity(intent)
    }
}

