package `in`.mybhiwani.courseapp

data class VideoLesson(
    val id: Int,
    val courseId: Int = 0,
    val chapterId: Int = 0,
    val chapterTitle: String = "",
    val title: String,
    val dailymotionId: String,
    val duration: String = "15:00",
    val sortOrder: Int = 1,
    val isFreePreview: Boolean = false
)

data class Chapter(
    val id: Int,
    val courseId: Int,
    val title: String,
    val description: String = "",
    val sortOrder: Int = 1,
    val videos: List<VideoLesson> = emptyList(),
    var isExpanded: Boolean = true
) {
    val totalVideos: Int get() = videos.size
}
