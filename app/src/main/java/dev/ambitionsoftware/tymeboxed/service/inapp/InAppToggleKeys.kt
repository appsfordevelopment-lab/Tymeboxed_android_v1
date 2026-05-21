package dev.ambitionsoftware.tymeboxed.service.inapp

/**
 * Toggle keys aligned with [Switchly](https://gitlab.com/Saltyy/switchly-public) / `BlockingToggleKeys`
 * so options stay familiar and migration is possible.
 */
object InAppToggleKeys {
    const val KEY_BLOCK_INAPP = "block_inapp_toggle"

    const val KEY_BLOCK_YT_SHORTS = "block_yt_shorts"
    const val KEY_BLOCK_YT_SEARCH = "block_yt_search"
    const val KEY_BLOCK_YT_COMMENTS = "block_yt_comments"
    const val KEY_BLOCK_YT_PIP = "block_yt_pip"

    const val KEY_BLOCK_IG_REELS = "block_ig_reels"
    const val KEY_BLOCK_IG_EXPLORE = "block_ig_explore"
    const val KEY_BLOCK_IG_SEARCH = "block_ig_search"
    const val KEY_BLOCK_IG_STORIES = "block_ig_stories"
    const val KEY_BLOCK_IG_COMMENTS = "block_ig_comments"

    const val KEY_BLOCK_X_HOME = "block_x_home"
    const val KEY_BLOCK_X_SEARCH = "block_x_search"
    const val KEY_BLOCK_X_GROK = "block_x_grok"
    const val KEY_BLOCK_X_NOTIFICATIONS = "block_x_notifications"

    const val KEY_BLOCK_SNAP_MAP = "block_snap_map"
    const val KEY_BLOCK_SNAP_STORIES = "block_snap_stories"
    const val KEY_BLOCK_SNAP_SPOTLIGHT = "block_snap_spotlight"
    const val KEY_BLOCK_SNAP_FOLLOWING = "block_snap_following"

    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    const val X_TWITTER = "com.twitter.android"
    const val SNAPCHAT = "com.snapchat.android"

    fun isSupportedApp(pkg: String): Boolean = pkg in setOf(
        YOUTUBE,
        INSTAGRAM,
        X_TWITTER,
        SNAPCHAT,
    )
}
