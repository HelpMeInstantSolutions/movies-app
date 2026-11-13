package `in`.mybhiwani.courseapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LessonAdapter(
    private val items: List<VideoLesson>,
    private val onSelect: (VideoLesson) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonVH>() {

    private var selectedIndex = 0

    fun setSelectedPosition(position: Int) {
        selectedIndex = position
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

        if (item.isFreePreview) {
            holder.tvBadge.text = "• Free Demo"
            holder.tvBadge.setTextColor(Color.parseColor("#10B981"))
        } else {
            holder.tvBadge.text = "• Enrolled"
            holder.tvBadge.setTextColor(Color.parseColor("#94A3B8"))
        }

        val isPlaying = (position == selectedIndex)
        if (isPlaying) {
            holder.root.setBackgroundResource(R.drawable.bg_gradient_brand)
            holder.tvTitle.setTextColor(Color.WHITE)
            holder.tvDuration.setTextColor(Color.parseColor("#E0E7FF"))
            holder.tvNumber.setBackgroundColor(Color.parseColor("#312E81"))
            holder.tvNumber.setTextColor(Color.parseColor("#38BDF8"))
            holder.iconPlay.setImageResource(R.drawable.ic_play_arrow)
            holder.iconPlay.setColorFilter(Color.WHITE)
        } else {
            holder.root.setBackgroundResource(R.drawable.bg_rounded_card)
            holder.tvTitle.setTextColor(Color.WHITE)
            holder.tvDuration.setTextColor(Color.parseColor("#94A3B8"))
            holder.tvNumber.setBackgroundResource(R.drawable.bg_tab_inactive)
            holder.tvNumber.setTextColor(Color.parseColor("#38BDF8"))
            holder.iconPlay.setImageResource(R.drawable.ic_play_arrow)
            holder.iconPlay.setColorFilter(Color.parseColor("#64748B"))
        }

        holder.itemView.setOnClickListener {
            selectedIndex = position
            notifyDataSetChanged()
            onSelect(item)
        }
    }
}

