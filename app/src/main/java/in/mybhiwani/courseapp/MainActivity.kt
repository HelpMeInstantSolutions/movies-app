package `in`.mybhiwani.courseapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Course(
    val id: Int,
    val title: String,
    val description: String,
    val thumbnail: String,
    val price: Double,
    val totalChapters: Int = 0,
    val totalVideos: Int = 0
)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvCourseCountBadge: TextView
    private lateinit var etSearch: EditText

    private val allCourses = mutableListOf<Course>()
    private val displayedCourses = mutableListOf<Course>()
    private lateinit var adapter: CourseAdapter

    private val apiEndpoint = "https://cinemas.mybhiwani.in/api/courses.php?action=list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security feature: Disable screen recording & screenshots for pirated protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        val session = SessionManager(this)
        findViewById<TextView>(R.id.tvUserWelcome)?.text = "Hello, ${session.getUserName()} 👋"

        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            showLogoutConfirmation(session)
        }

        recyclerView = findViewById(R.id.recyclerViewCourses)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvCourseCountBadge = findViewById(R.id.tvCourseCountBadge)
        etSearch = findViewById(R.id.etSearchCourses)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CourseAdapter(displayedCourses) { course ->
            showCustomToast("Opening ${course.title} curriculum...")
            val intent = Intent(this@MainActivity, ChaptersActivity::class.java).apply {
                putExtra("COURSE_ID", course.id)
                putExtra("COURSE_TITLE", course.title)
                putExtra("COURSE_DESC", course.description)
                putExtra("COURSE_THUMBNAIL", course.thumbnail)
                putExtra("TOTAL_CHAPTERS", course.totalChapters)
                putExtra("TOTAL_VIDEOS", course.totalVideos)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        setupSearch()
        fetchCourses()
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCourses(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterCourses(query: String) {
        displayedCourses.clear()
        if (query.isEmpty()) {
            displayedCourses.addAll(allCourses)
        } else {
            val q = query.lowercase()
            for (c in allCourses) {
                if (c.title.lowercase().contains(q) || c.description.lowercase().contains(q)) {
                    displayedCourses.add(c)
                }
            }
        }
        adapter.notifyDataSetChanged()
        updateEmptyState(query)
    }

    private fun updateEmptyState(searchQuery: String = "") {
        if (displayedCourses.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            if (searchQuery.isNotEmpty()) {
                tvEmptyTitle.text = "No courses match \"$searchQuery\""
            } else {
                tvEmptyTitle.text = "No courses available yet"
            }
        } else {
            layoutEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
        tvCourseCountBadge.text = "${displayedCourses.size} Batches"
    }

    private fun showLogoutConfirmation(session: SessionManager) {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to log out from this device?")
            .setPositiveButton("Sign Out") { _, _ ->
                session.logout()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchCourses() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(apiEndpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    if (json.getBoolean("status")) {
                        val dataArray = json.getJSONArray("data")
                        allCourses.clear()

                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            allCourses.add(
                                Course(
                                    id = item.getInt("id"),
                                    title = item.getString("title"),
                                    description = item.optString("description", ""),
                                    thumbnail = item.optString("thumbnail", ""),
                                    price = item.optDouble("price", 0.0),
                                    totalChapters = item.optInt("total_chapters", 0),
                                    totalVideos = item.optInt("total_videos", 0)
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            filterCourses(etSearch.text.toString().trim())
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateEmptyState()
                        showCustomToast("Failed to load courses. Check internet connection.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateEmptyState()
                    showCustomToast("Network: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun showCustomToast(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(Color.parseColor("#131B2E"))
            .setTextColor(Color.WHITE)
            .show()
    }
}

class CourseAdapter(
    private val list: List<Course>,
    private val onClick: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgThumb)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val desc: TextView = v.findViewById(R.id.tvDesc)
        val lessons: TextView = v.findViewById(R.id.tvLessons)
        val priceTag: TextView = v.findViewById(R.id.tvPriceTag)
        val btnOpen: View = v.findViewById(R.id.btnOpenCourse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_course_card, parent, false)
        return VH(v)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.title.text = item.title
        holder.desc.text = if (item.description.isNotEmpty()) item.description else "Comprehensive online video batch."
        holder.lessons.text = if (item.totalChapters > 0) {
            "${item.totalChapters} Chapters • ${item.totalVideos} Lessons"
        } else {
            "${item.totalVideos} Lessons"
        }
        holder.priceTag.text = if (item.price > 0) "₹${item.price}" else "FREE BATCH"

        if (item.thumbnail.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.thumbnail)
                .centerCrop()
                .into(holder.img)
        } else {
            holder.img.setImageResource(R.drawable.bg_input_field)
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.btnOpen.setOnClickListener { onClick(item) }
    }
}

