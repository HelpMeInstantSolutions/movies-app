package `in`.mybhiwani.courseapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onLessonClicked: (VideoLesson) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    class ChapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chapterHeaderRow: LinearLayout = itemView.findViewById(R.id.chapterHeaderRow)
        val tvChapterTitle: TextView = itemView.findViewById(R.id.tvChapterTitle)
        val tvChapterInfo: TextView = itemView.findViewById(R.id.tvChapterInfo)
        val ivExpandIndicator: ImageView = itemView.findViewById(R.id.ivExpandIndicator)
        val layoutLessonsContainer: LinearLayout = itemView.findViewById(R.id.layoutLessonsContainer)
        val rvChapterLessons: RecyclerView = itemView.findViewById(R.id.rvChapterLessons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter_card, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]

        holder.tvChapterTitle.text = chapter.title
        holder.tvChapterInfo.text = "${chapter.videos.size} Lectures"

        // Setup nested RecyclerView for lessons
        holder.rvChapterLessons.layoutManager = LinearLayoutManager(holder.itemView.context)
        val lessonAdapter = LessonAdapter(chapter.videos) { lesson ->
            onLessonClicked(lesson)
        }
        holder.rvChapterLessons.adapter = lessonAdapter

        // Expand / Collapse state
        updateExpandState(holder, chapter.isExpanded)

        holder.chapterHeaderRow.setOnClickListener {
            chapter.isExpanded = !chapter.isExpanded
            updateExpandState(holder, chapter.isExpanded)
        }
    }

    private fun updateExpandState(holder: ChapterViewHolder, isExpanded: Boolean) {
        if (isExpanded) {
            holder.layoutLessonsContainer.visibility = View.VISIBLE
            holder.ivExpandIndicator.setImageResource(R.drawable.ic_expand_less)
        } else {
            holder.layoutLessonsContainer.visibility = View.GONE
            holder.ivExpandIndicator.setImageResource(R.drawable.ic_expand_more)
        }
    }

    override fun getItemCount(): Int = chapters.size
}
