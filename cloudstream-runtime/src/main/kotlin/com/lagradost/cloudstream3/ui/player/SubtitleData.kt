package com.lagradost.cloudstream3.ui.player
enum class SubtitleOrigin {
    URL,
    DOWNLOADED_FILE,
    EMBEDDED_IN_VIDEO
}

/**
 * @param originalName the start of the name to be displayed in the player
 * @param nameSuffix An extra suffix added to the subtitle to make sure it is unique
 * @param url Url for the subtitle, when EMBEDDED_IN_VIDEO this variable is used as the real backend id
 * @param headers if empty it will use the base onlineDataSource headers else only the specified headers
 * @param languageCode Not guaranteed to follow any standard. Could be something like "English 4" or "en".
 * */
data class SubtitleData(
    val originalName: String,
    val nameSuffix: String,
    val url: String,
    val origin: SubtitleOrigin,
    val mimeType: String,
    val headers: Map<String, String>,
    val languageCode: String?,
) {
    companion object {
        fun constructName(originalName: String, nameSuffix: String) = "$originalName $nameSuffix"
    }

    /** Internal ID for exoplayer, unique for each link*/
    fun getId(): String {
        return if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url
        else "$url|$name"
    }

    val name = constructName(originalName, nameSuffix)

    /**
     * Gets the URL, but tries to fix it if it is malformed.
     */
    fun getFixedUrl(): String {
        // Some extensions fail to include the protocol, this helps with that.
        val fixedSubUrl = if (this.url.startsWith("//")) {
            "https:${this.url}"
        } else {
            this.url
        }
        return fixedSubUrl
    }
}
