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
import androidx.recyclerview.widget.GridLayoutManager
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

data class MediaItem(
    val id: Int,
    val type: String, // "movie" or "series"
    val title: String,
    val description: String,
    val poster: String,
    val backdrop: String,
    val releaseYear: String,
    val duration: String,
    val rating: String,
    val language: String,
    val genreName: String,
    val totalSeasons: Int = 0,
    val totalEpisodes: Int = 0
)

// Legacy alias for Course so existing references compile seamlessly
typealias Course = MediaItem

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvCourseCountBadge: TextView
    private lateinit var etSearch: EditText
    private lateinit var tvTabAll: TextView
    private lateinit var tvTabMovies: TextView
    private lateinit var tvTabSeries: TextView

    private val allMedia = mutableListOf<MediaItem>()
    private val displayedMedia = mutableListOf<MediaItem>()
    private lateinit var adapter: MediaAdapter
    private var activeFilter: String = "all" // "all", "movie", "series"

    private val apiEndpoint = "https://cinemas.mybhiwani.in/api/courses.php?action=list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Anti-Screen Capture & Anti-Piracy Flag (FLAG_SECURE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        loadMedia()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewCourses)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvCourseCountBadge = findViewById(R.id.tvCourseCountBadge)
        etSearch = findViewById(R.id.etSearchCourses)
        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabMovies = findViewById(R.id.tvTabPaid)
        tvTabSeries = findViewById(R.id.tvTabFree)

        tvTabMovies.text = "🎬 Movies"
        tvTabSeries.text = "📺 Series"

        // Grid Layout: 2 columns for movie/series poster cards
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = MediaAdapter(displayedMedia) { media ->
            openMediaDetail(media)
        }
        recyclerView.adapter = adapter

        // Setup user header
        val tvUserWelcome = findViewById<TextView>(R.id.tvUserWelcome)
        val userName = SessionManager.getUserName(this)
        tvUserWelcome.text = if (!userName.isNullOrEmpty()) "Welcome, $userName 🍿" else "Cinemas & Shows 🍿"
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            confirmLogout()
        }

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            loadMedia()
        }

        tvTabAll.setOnClickListener {
            setFilter("all")
        }

        tvTabMovies.setOnClickListener {
            setFilter("movie")
        }

        tvTabSeries.setOnClickListener {
            setFilter("series")
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setFilter(filter: String) {
        activeFilter = filter
        updateTabStyles()
        filterList(etSearch.text.toString())
    }

    private fun updateTabStyles() {
        tvTabAll.setBackgroundResource(if (activeFilter == "all") R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tvTabAll.setTextColor(if (activeFilter == "all") Color.WHITE else Color.parseColor("#94A3B8"))

        tvTabMovies.setBackgroundResource(if (activeFilter == "movie") R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tvTabMovies.setTextColor(if (activeFilter == "movie") Color.WHITE else Color.parseColor("#94A3B8"))

        tvTabSeries.setBackgroundResource(if (activeFilter == "series") R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tvTabSeries.setTextColor(if (activeFilter == "series") Color.WHITE else Color.parseColor("#94A3B8"))
    }

    private fun loadMedia() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL(apiEndpoint)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000

                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    response.toString()
                }

                val json = JSONObject(result)
                if (json.optBoolean("status", false)) {
                    val arr = json.getJSONArray("data")
                    allMedia.clear()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        allMedia.add(
                            MediaItem(
                                id = obj.optInt("id", 0),
                                type = obj.optString("type", "movie"),
                                title = obj.optString("title", "Untitled"),
                                description = obj.optString("description", ""),
                                poster = obj.optString("poster", obj.optString("thumbnail", "")),
                                backdrop = obj.optString("backdrop", ""),
                                releaseYear = obj.optString("release_year", "2024"),
                                duration = obj.optString("duration", ""),
                                rating = obj.optString("rating", "8.5"),
                                language = obj.optString("language", "Hindi"),
                                genreName = obj.optString("genre_name", obj.optString("category_name", "Cinema")),
                                totalSeasons = obj.optInt("total_seasons", obj.optInt("total_chapters", 0)),
                                totalEpisodes = obj.optInt("total_episodes", obj.optInt("total_videos", 0))
                            )
                        )
                    }
                    filterList(etSearch.text.toString())
                }
            } catch (e: Exception) {
                Snackbar.make(recyclerView, "Network error: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun filterList(query: String) {
        val q = query.lowercase().trim()
        displayedMedia.clear()

        for (item in allMedia) {
            val matchesFilter = when (activeFilter) {
                "movie" -> item.type == "movie"
                "series" -> item.type == "series"
                else -> true
            }

            val matchesSearch = q.isEmpty() ||
                    item.title.lowercase().contains(q) ||
                    item.genreName.lowercase().contains(q) ||
                    item.language.lowercase().contains(q)

            if (matchesFilter && matchesSearch) {
                displayedMedia.add(item)
            }
        }

        adapter.notifyDataSetChanged()
        tvCourseCountBadge.text = "${displayedMedia.size} Titles"

        if (displayedMedia.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvEmptyTitle.text = if (q.isNotEmpty()) "No titles matching '$q'" else "No movies or shows available"
        } else {
            layoutEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openMediaDetail(media: MediaItem) {
        val intent = Intent(this, ChaptersActivity::class.java).apply {
            putExtra("COURSE_ID", media.id)
            putExtra("COURSE_TITLE", media.title)
            putExtra("COURSE_DESC", media.description)
            putExtra("MEDIA_TYPE", media.type)
            putExtra("MEDIA_POSTER", media.poster)
            putExtra("MEDIA_BACKDROP", media.backdrop)
        }
        startActivity(intent)
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Do you want to sign out from Cinemas App?")
            .setPositiveButton("Sign Out") { _, _ ->
                SessionManager.clearSession(this)
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class MediaAdapter(
    private val items: List<MediaItem>,
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    class MediaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivPoster: ImageView = v.findViewById(R.id.ivCourseThumbnail)
        val tvTitle: TextView = v.findViewById(R.id.tvCourseTitle)
        val tvDesc: TextView = v.findViewById(R.id.tvCourseDescription)
        val tvBadge: TextView = v.findViewById(R.id.tvCourseCategory)
        val tvStats: TextView = v.findViewById(R.id.tvCoursePrice)
        val btnPlay: View = v.findViewById(R.id.btnOpenCourse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course_card, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvDesc.text = "${item.releaseYear} • ${item.genreName} • ★ ${item.rating}"
        holder.tvBadge.text = item.type.uppercase()
        holder.tvStats.text = if (item.type == "series") "${item.totalSeasons} Seasons" else (if (item.duration.isNotEmpty()) item.duration else "Full Movie")

        Glide.with(holder.itemView.context)
            .load(if (item.poster.isNotEmpty()) item.poster else item.backdrop)
            .placeholder(R.drawable.bg_rounded_card)
            .error(R.drawable.bg_rounded_card)
            .centerCrop()
            .into(holder.ivPoster)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.btnPlay.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

