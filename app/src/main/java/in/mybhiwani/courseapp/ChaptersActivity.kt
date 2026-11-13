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
    private var courseTitle: String = "Course Curriculum"
    private var courseDesc: String = ""

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
        courseTitle = intent.getStringExtra("COURSE_TITLE") ?: "Course Curriculum"
        courseDesc = intent.getStringExtra("COURSE_DESC") ?: ""

        initViews()
        fetchCourseCurriculum(courseId)
    }

    private fun initViews() {
        tvTopCourseTitle = findViewById(R.id.tvTopCourseTitle)
        tvHeaderCourseTitle = findViewById(R.id.tvHeaderCourseTitle)
        tvHeaderCourseDesc = findViewById(R.id.tvHeaderCourseDesc)
        tvCurriculumStats = findViewById(R.id.tvCurriculumStats)
        tvChapterCountPill = findViewById(R.id.tvChapterCountPill)
        btnStartLearning = findViewById(R.id.btnStartLearning)
        chaptersLoader = findViewById(R.id.chaptersLoader)
        layoutEmptyChapters = findViewById(R.id.layoutEmptyChapters)
        rvChapters = findViewById(R.id.rvChapters)

        tvTopCourseTitle.text = courseTitle
        tvHeaderCourseTitle.text = courseTitle
        if (courseDesc.isNotEmpty()) {
            tvHeaderCourseDesc.text = courseDesc
        }

        findViewById<ImageButton>(R.id.btnBackFromChapters)?.setOnClickListener {
            onBackPressed()
        }

        rvChapters.layoutManager = LinearLayoutManager(this)
        chapterAdapter = ChapterAdapter(chaptersList) { lesson ->
            openPlayerForLesson(lesson.id)
        }
        rvChapters.adapter = chapterAdapter

        btnStartLearning.setOnClickListener {
            if (allVideosList.isNotEmpty()) {
                openPlayerForLesson(allVideosList[0].id)
            } else {
                showToaster("No lessons uploaded for this batch yet")
            }
        }
    }

    private fun openPlayerForLesson(videoId: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("COURSE_ID", courseId)
            putExtra("COURSE_TITLE", courseTitle)
            putExtra("INITIAL_VIDEO_ID", videoId)
        }
        startActivity(intent)
    }

    private fun fetchCourseCurriculum(cId: Int) {
        chaptersLoader.visibility = View.VISIBLE
        layoutEmptyChapters.visibility = View.GONE

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
                    if (json.optBoolean("status", false)) {
                        val data = json.getJSONObject("data")
                        val apiTitle = data.optString("title", courseTitle)
                        val apiDesc = data.optString("description", courseDesc)

                        chaptersList.clear()
                        allVideosList.clear()

                        val chArray = data.optJSONArray("chapters")
                        if (chArray != null) {
                            for (i in 0 until chArray.length()) {
                                val chObj = chArray.getJSONObject(i)
                                val vList = mutableListOf<VideoLesson>()
                                val vArray = chObj.optJSONArray("videos")
                                if (vArray != null) {
                                    for (j in 0 until vArray.length()) {
                                        val vObj = vArray.getJSONObject(j)
                                        val lesson = VideoLesson(
                                            id = vObj.getInt("id"),
                                            courseId = vObj.optInt("course_id", cId),
                                            chapterId = vObj.optInt("chapter_id", chObj.optInt("id", 0)),
                                            chapterTitle = chObj.optString("title", ""),
                                            title = vObj.getString("title"),
                                            dailymotionId = vObj.getString("dailymotion_id"),
                                            duration = vObj.optString("duration", "15:00"),
                                            sortOrder = vObj.optInt("sort_order", j + 1),
                                            isFreePreview = vObj.optInt("is_free_preview", 0) == 1
                                        )
                                        vList.add(lesson)
                                        allVideosList.add(lesson)
                                    }
                                }

                                chaptersList.add(
                                    Chapter(
                                        id = chObj.getInt("id"),
                                        courseId = cId,
                                        title = chObj.getString("title"),
                                        description = chObj.optString("description", ""),
                                        sortOrder = chObj.optInt("sort_order", i + 1),
                                        videos = vList,
                                        isExpanded = (i == 0) // First chapter expanded by default
                                    )
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            chaptersLoader.visibility = View.GONE
                            tvHeaderCourseTitle.text = apiTitle
                            if (apiDesc.isNotEmpty()) tvHeaderCourseDesc.text = apiDesc

                            val totalLessons = allVideosList.size
                            val totalChaps = chaptersList.size

                            tvCurriculumStats.text = "$totalChaps Chapters • $totalLessons Lessons"
                            tvChapterCountPill.text = "$totalChaps Chapters"

                            if (chaptersList.isEmpty() && allVideosList.isEmpty()) {
                                layoutEmptyChapters.visibility = View.VISIBLE
                                btnStartLearning.visibility = View.GONE
                            } else {
                                layoutEmptyChapters.visibility = View.GONE
                                btnStartLearning.visibility = View.VISIBLE
                            }

                            chapterAdapter.notifyDataSetChanged()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chaptersLoader.visibility = View.GONE
                    showToaster("Error loading curriculum: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun showToaster(msg: String) {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(Color.parseColor("#131B2E"))
            .setTextColor(Color.WHITE)
            .show()
    }
}

