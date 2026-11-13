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
        val seasonHeaderRow: LinearLayout = itemView.findViewById(R.id.seasonHeaderRow)
        val tvSeasonTitle: TextView = itemView.findViewById(R.id.tvSeasonTitle)
        val tvSeasonInfo: TextView = itemView.findViewById(R.id.tvSeasonInfo)
        val tvSeasonEpisodesBadge: TextView = itemView.findViewById(R.id.tvSeasonEpisodesBadge)
        val ivSeasonChevron: ImageView = itemView.findViewById(R.id.ivSeasonChevron)
        val seasonDivider: View = itemView.findViewById(R.id.seasonDivider)
        val rvSeasonEpisodes: RecyclerView = itemView.findViewById(R.id.rvSeasonEpisodes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_season_card, parent, false)
        return SeasonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
        val season = seasons[position]

        holder.tvSeasonTitle.text = season.title
        holder.tvSeasonInfo.text = "${season.episodes.size} Episodes • HD 1080p"
        holder.tvSeasonEpisodesBadge.text = "${season.episodes.size} Episodes"

        holder.rvSeasonEpisodes.layoutManager = LinearLayoutManager(holder.itemView.context)
        val episodeAdapter = EpisodeAdapter(season.episodes) { ep ->
            onEpisodeClicked(ep)
        }
        holder.rvSeasonEpisodes.adapter = episodeAdapter

        updateExpandState(holder, season.isExpanded)

        holder.seasonHeaderRow.setOnClickListener {
            season.isExpanded = !season.isExpanded
            updateExpandState(holder, season.isExpanded)
        }
    }

    private fun updateExpandState(holder: SeasonViewHolder, isExpanded: Boolean) {
        if (isExpanded) {
            holder.rvSeasonEpisodes.visibility = View.VISIBLE
            holder.seasonDivider.visibility = View.VISIBLE
            holder.ivSeasonChevron.setImageResource(R.drawable.ic_expand_less)
        } else {
            holder.rvSeasonEpisodes.visibility = View.GONE
            holder.seasonDivider.visibility = View.GONE
            holder.ivSeasonChevron.setImageResource(R.drawable.ic_expand_more)
        }
    }

    override fun getItemCount(): Int = seasons.size
}

// Legacy alias for backwards compatibility
typealias ChapterAdapter = SeasonAdapter
