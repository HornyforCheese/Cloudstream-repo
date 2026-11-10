package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import kotlin.reflect.jvm.jvmName

object VideoClickActionHolder {
    val allVideoClickActions = threadSafeListOf<VideoClickAction>()
    var activity: WeakReference<Activity>? = null
    var resultLauncher: ((Intent, ActivityOptionsCompat?) -> Unit)? = null
}

abstract class VideoClickAction {
    abstract val name: UiText

    /** if true, the app will show dialog to select source - result.links[index] */
    open val oneSource : Boolean = false

    /** if true, this action could be selected as default player (one press action) in settings */
    open val isPlayer: Boolean = false

    /** Which type of sources this action can handle. */
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    /** Even if VideoClickAction should not run any UI code, startActivity requires it,
     * this is a wrapper for runOnUiThread in a suspended safe context that bubble up exceptions  */
    @Throws
    suspend fun <T> uiThread(callable : Callable<T>) : T? {
        checkNotNull(VideoClickActionHolder.activity?.get()) { "No Mutiny Media activity is attached" }
        return withContext(Dispatchers.Main.immediate) { callable.call() }
    }

    @Throws
    suspend fun launchResult(intent: Intent?, options: ActivityOptionsCompat? = null) {
        if (intent != null) uiThread {
            checkNotNull(VideoClickActionHolder.resultLauncher) { "No activity result launcher is attached" }(intent, options)
        }
    }

    @Throws
    suspend fun launch(intent: Intent?, bundle: Bundle? = null) {
        if (intent != null) uiThread {
            checkNotNull(VideoClickActionHolder.activity?.get()).startActivity(intent, bundle)
        }
    }

    fun uniqueId() = "$sourcePlugin:${this::class.jvmName}"

    @Throws
    abstract fun shouldShow(context: Context?, video: ResultEpisode?): Boolean

    /** Safe version of shouldShow, as we don't trust extension devs to handle exceptions,
     * however no dev *should* throw in shouldShow */
    fun shouldShowSafe(context: Context?, video: ResultEpisode?): Boolean {
        return try {
            shouldShow(context,video)
        } catch (t : Throwable) {
            logError(t)
            false
        }
    }

    /**
     *  This function is called when the action is clicked.
     *  @param context The current activity
     *  @param video The episode/movie that was clicked
     *  @param result The result of the link loading, contains video & subtitle links
     *  @param index if oneSource is true, this is the index of the selected source
     */
    @Throws
    abstract suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?)

    /** Safe version of runAction, as we don't trust extension devs to handle exceptions */
    fun runActionSafe(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) = ioSafe {
        try {
            runAction(context, video, result, index)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logError(error)
        } catch (error: LinkageError) {
            logError(error)
        } catch (error: NotImplementedError) {
            logError(error)
        }
    }
}
