package `in`.mybhiwani.cinemas

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import `in`.mybhiwani.cinemas.R

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
    val totalSeasons: Int = 0,
    val totalEpisodes: Int = 0
)

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvMediaCountBadge: TextView
    private lateinit var etSearch: EditText
    private lateinit var tvTabAll: TextView
    private lateinit var tvTabMovies: TextView
    private lateinit var tvTabSeries: TextView

    private val allMedia = mutableListOf<MediaItem>()
    private val displayedMedia = mutableListOf<MediaItem>()
    private lateinit var adapter: MediaAdapter
    private var activeFilter: String = "all"

    private val apiEndpoint = "https://cinemas.mybhiwani.in/api/movies.php?action=list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Anti-Piracy Flag (FLAG_SECURE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        initViews()
        setupDrawer()
        setupListeners()
        loadMedia()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        recyclerView = findViewById(R.id.recyclerViewMedia)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvMediaCountBadge = findViewById(R.id.tvMediaCountBadge)
        etSearch = findViewById(R.id.etSearchMedia)
        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabMovies = findViewById(R.id.tvTabMovies)
        tvTabSeries = findViewById(R.id.tvTabSeries)

        tvTabMovies.text = "🎬 Movies"
        tvTabSeries.text = "📺 Web Series"

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = MediaAdapter(displayedMedia) { media ->
            openMediaDetail(media)
        }
        recyclerView.adapter = adapter
    }

    private fun setupDrawer() {
        findViewById<View>(R.id.btnOpenDrawer).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val tvDrawerName = findViewById<TextView>(R.id.tvDrawerUserName)
        val tvDrawerPhone = findViewById<TextView>(R.id.tvDrawerUserPhone)
        val tvDrawerEmail = findViewById<TextView>(R.id.tvDrawerUserEmail)

        val name = sessionManager.getUserName()
        val phone = sessionManager.getUserPhone()
        val email = sessionManager.getUserEmail()

        tvDrawerName.text = if (name.isNotEmpty()) name else "Cinemas Subscriber"
        tvDrawerPhone.text = if (phone.isNotEmpty()) "Mobile: $phone" else "Mobile: Not Set"
        tvDrawerEmail.text = if (email.isNotEmpty()) email else "user@cinemas.in"

        findViewById<View>(R.id.drawerItemAll).setOnClickListener {
            setFilter("all")
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.drawerItemMovies).setOnClickListener {
            setFilter("movie")
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.drawerItemSeries).setOnClickListener {
            setFilter("series")
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.btnDrawerLogout).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            confirmLogout()
        }
    }

    private fun setupListeners() {
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
                                totalSeasons = obj.optInt("total_seasons", 0),
                                totalEpisodes = obj.optInt("total_episodes", 0)
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
                    item.description.lowercase().contains(q) ||
                    item.language.lowercase().contains(q)

            if (matchesFilter && matchesSearch) {
                displayedMedia.add(item)
            }
        }

        adapter.notifyDataSetChanged()
        tvMediaCountBadge.text = "${displayedMedia.size} Titles"

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
        val intent = Intent(this, MediaDetailActivity::class.java).apply {
            putExtra("MEDIA_ID", media.id)
            putExtra("COURSE_ID", media.id)
            putExtra("MEDIA_TITLE", media.title)
            putExtra("COURSE_TITLE", media.title)
            putExtra("MEDIA_DESC", media.description)
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
            .setMessage("Are you sure you want to log out from Cinemas?")
            .setPositiveButton("Log Out") { _, _ ->
                sessionManager.logout()
                SessionManager.clearSession(this)
                val intent = Intent(this, AuthActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

class MediaAdapter(
    private val items: List<MediaItem>,
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    class MediaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivPoster: ImageView = v.findViewById(R.id.ivMediaPoster)
        val tvTitle: TextView = v.findViewById(R.id.tvMediaTitle)
        val tvDesc: TextView = v.findViewById(R.id.tvMediaDescription)
        val tvBadge: TextView = v.findViewById(R.id.tvMediaTypeBadge)
        val tvStats: TextView = v.findViewById(R.id.tvMediaMeta)
        val btnPlay: View = v.findViewById(R.id.btnWatchMedia)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_card, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        // No genre - clean cinema info
        holder.tvDesc.text = "${item.releaseYear} • ${item.language} • ★ ${item.rating}"
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
