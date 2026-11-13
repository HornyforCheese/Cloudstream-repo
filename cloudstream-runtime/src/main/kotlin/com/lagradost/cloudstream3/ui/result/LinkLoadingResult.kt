package com.lagradost.cloudstream3.ui.result
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.ui.player.SubtitleData
data class LinkLoadingResult(
    val links: List<ExtractorLink>,
    val subs: List<SubtitleData>,
)
