package `in`.mybhiwani.cinemas

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import `in`.mybhiwani.cinemas.R

class LessonAdapter(
    private val items: List<VideoLesson>,
    private var currentVideoId: Int = 0,
    private val onSelect: (VideoLesson) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonVH>() {

    fun updateCurrentVideoId(id: Int) {
        currentVideoId = id
        notifyDataSetChanged()
    }

    class LessonVH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.layoutLessonItem)
        val tvNumber: TextView = v.findViewById(R.id.tvLessonNum)
        val tvTitle: TextView = v.findViewById(R.id.tvLessonTitle)
        val tvDuration: TextView = v.findViewById(R.id.tvLessonDuration)
        val tvBadge: TextView = v.findViewById(R.id.tvLessonBadge)
        val iconPlay: ImageView = v.findViewById(R.id.icPlayStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lesson_row, parent, false)
        return LessonVH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: LessonVH, position: Int) {
        val item = items[position]
        holder.tvNumber.text = String.format("%02d", position + 1)
        holder.tvTitle.text = item.title
        holder.tvDuration.text = item.duration

        val isSelected = (item.id == currentVideoId)
        if (isSelected) {
            holder.root.setBackgroundColor(Color.parseColor("#1E293B"))
            holder.tvTitle.setTextColor(Color.parseColor("#E11D48"))
            holder.iconPlay.setImageResource(R.drawable.ic_play_arrow)
            holder.iconPlay.setColorFilter(Color.parseColor("#E11D48"))
        } else {
            holder.root.setBackgroundColor(Color.TRANSPARENT)
            holder.tvTitle.setTextColor(Color.WHITE)
            holder.iconPlay.setImageResource(R.drawable.ic_play_arrow)
            holder.iconPlay.setColorFilter(Color.parseColor("#64748B"))
        }

        holder.tvBadge.text = "• Stream"
        holder.tvBadge.setTextColor(Color.parseColor("#10B981"))

        holder.root.setOnClickListener {
            updateCurrentVideoId(item.id)
            onSelect(item)
        }
    }
}
