package `in`.mybhiwani.cinemas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import `in`.mybhiwani.cinemas.R

class SeasonAdapter(
    private val seasons: List<MediaSeason>,
    private val onEpisodeClicked: (MediaEpisode) -> Unit
) : RecyclerView.Adapter<SeasonAdapter.SeasonViewHolder>() {

    class SeasonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chapterHeaderRow: LinearLayout = itemView.findViewById(R.id.chapterHeaderRow)
        val tvChapterTitle: TextView = itemView.findViewById(R.id.tvChapterTitle)
        val tvChapterInfo: TextView = itemView.findViewById(R.id.tvChapterInfo)
        val ivExpandIndicator: ImageView = itemView.findViewById(R.id.ivExpandIndicator)
        val layoutLessonsContainer: LinearLayout = itemView.findViewById(R.id.layoutLessonsContainer)
        val rvChapterLessons: RecyclerView = itemView.findViewById(R.id.rvChapterLessons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_season_card, parent, false)
        return SeasonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
        val season = seasons[position]

        holder.tvChapterTitle.text = season.title
        holder.tvChapterInfo.text = "${season.episodes.size} Episodes"

        holder.rvChapterLessons.layoutManager = LinearLayoutManager(holder.itemView.context)
        val episodeAdapter = EpisodeAdapter(season.episodes) { ep ->
            onEpisodeClicked(ep)
        }
        holder.rvChapterLessons.adapter = episodeAdapter

        updateExpandState(holder, season.isExpanded)

        holder.chapterHeaderRow.setOnClickListener {
            season.isExpanded = !season.isExpanded
            updateExpandState(holder, season.isExpanded)
        }
    }

    private fun updateExpandState(holder: SeasonViewHolder, isExpanded: Boolean) {
        if (isExpanded) {
            holder.layoutLessonsContainer.visibility = View.VISIBLE
            holder.ivExpandIndicator.setImageResource(R.drawable.ic_expand_less)
        } else {
            holder.layoutLessonsContainer.visibility = View.GONE
            holder.ivExpandIndicator.setImageResource(R.drawable.ic_expand_more)
        }
    }

    override fun getItemCount(): Int = seasons.size
}

// Legacy alias
typealias ChapterAdapter = SeasonAdapter
