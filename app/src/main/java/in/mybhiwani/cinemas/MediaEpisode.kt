package `in`.mybhiwani.cinemas

data class MediaEpisode(
    val id: Int,
    val seriesId: Int = 0,
    val seasonId: Int = 0,
    val seasonTitle: String = "",
    val title: String,
    val description: String = "",
    val dailymotionId: String,
    val duration: String = "45:00",
    val episodeNumber: Int = 1,
    val sortOrder: Int = 1,
    val isFreePreview: Boolean = true
)

// Legacy alias so existing code seamlessly operates
typealias VideoLesson = MediaEpisode

data class MediaSeason(
    val id: Int,
    val seriesId: Int,
    val title: String,
    val description: String = "",
    val seasonNumber: Int = 1,
    val totalEpisodes: Int = 0,
    val episodes: List<MediaEpisode> = emptyList(),
    var isExpanded: Boolean = true
)

// Legacy alias
typealias Chapter = MediaSeason
