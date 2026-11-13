package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType

enum class VideoWatchState {
    /** Default value when no key is set */
    None,
    Watched
}

data class ResultEpisode(
    val headerName: String,
    val name: String?,
    val poster: String?,
    val episode: Int,
    val seasonIndex: Int?,  // this is the "season" index used season names
    val season: Int?, // this is the display
    val data: String,
    val apiName: String,
    val id: Int,
    val index: Int,
    val position: Long, // time in MS
    val duration: Long, // duration in MS
    val score: Score?,
    val description: String?,
    val isFiller: Boolean?,
    val tvType: TvType,
    val parentId: Int,
    /**
     * Conveys if the episode itself is marked as watched
     **/
    val videoWatchState: VideoWatchState,
    /** Sum of all previous season episode counts + episode */
    val totalEpisodeIndex: Int? = null,
    val airDate: Long? = null,
    val runTime: Int? = null,
)

fun ResultEpisode.getRealPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 5 || percentage >= 95) return 0
    return position
}

fun ResultEpisode.getDisplayPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 1) return 0
    if (percentage <= 5) return 5 * duration / 100
    if (percentage >= 95) return duration
    return position
}
