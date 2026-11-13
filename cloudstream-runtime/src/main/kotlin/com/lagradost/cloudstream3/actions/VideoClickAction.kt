package com.lagradost.cloudstream3.actions
import com.lagradost.api.Log
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import kotlin.reflect.jvm.jvmName

/** Android application bridge replacing upstream CommonActivity and MainActivity. */
object PluginActivityBridge {
    var activity: java.lang.ref.WeakReference<Activity>? = null
    var launchResult: ((Intent, ActivityOptionsCompat?) -> Unit)? = null
    fun toast(text: String?, duration: Int) {
        val current = activity?.get() ?: return
        current.runOnUiThread { Toast.makeText(current, text, duration).show() }
    }
}
object VideoClickActionHolder {
    val allVideoClickActions = threadSafeListOf<VideoClickAction>()

    init {
        Log.d("VideoClickActionHolder", "allVideoClickActions: ${allVideoClickActions.map { it.uniqueId() }}")
    }

    private const val ACTION_ID_OFFSET = 1000

    fun makeOptionMap(activity: Activity?, video: ResultEpisode) = allVideoClickActions
        // We need to have index before filtering
        .mapIndexed { id, it -> it to id + ACTION_ID_OFFSET }
        .filter { it.first.shouldShowSafe(activity, video) }
        .map { it.first.name to it.second }


    fun getActionById(id: Int): VideoClickAction? = allVideoClickActions.getOrNull(id - ACTION_ID_OFFSET)

    fun getByUniqueId(uniqueId: String): VideoClickAction? = allVideoClickActions.firstOrNull { it.uniqueId() == uniqueId }

    fun uniqueIdToId(uniqueId: String?): Int? {
        if (uniqueId == null) return null
        return allVideoClickActions
            .mapIndexed { id, it -> it to id + ACTION_ID_OFFSET }
            .firstOrNull { it.first.uniqueId() == uniqueId }
            ?.second
    }

    fun getPlayers(activity: Activity? = null) = allVideoClickActions.filter { it.isPlayer && it.shouldShowSafe(activity, null) }
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
        val future = FutureTask{
            try {
                Result.success(callable.call())
            } catch (t : Throwable) {
                Result.failure(t)
            }
        }
        PluginActivityBridge.activity?.get()?.runOnUiThread(future) ?: throw ErrorLoadingException("No UI Activity, this should never happened")
        val result = withContext(Dispatchers.IO) {
            return@withContext future.get()
        }
        return result.getOrThrow()
    }

    /** Internally uses activityResultLauncher,
     * use this when the activity has a result like watched position */
    @Throws
    suspend fun launchResult(intent : Intent?, options : ActivityOptionsCompat? = null) {
        if (intent == null) {
            return
        }

        uiThread {
            PluginActivityBridge.launchResult?.invoke(intent, options) ?: throw ErrorLoadingException("No active activity result handler")
        }
    }

    /** Internally uses startActivity, use this when you don't
     * have any result that needs to be stored when exiting the activity  */
    @Throws
    suspend fun launch(intent : Intent?, bundle : Bundle? = null) {
        if (intent == null) {
            return
        }

        uiThread {
            PluginActivityBridge.activity?.get()?.startActivity(intent, bundle) ?: throw ErrorLoadingException("No active Android activity")
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
        }  catch (_ : NotImplementedError) {
            PluginActivityBridge.toast("runAction has not been implemented for ${name.asStringNull(context)}, please contact the extension developer of $sourcePlugin", Toast.LENGTH_LONG)
        } catch (error : ErrorLoadingException) {
            PluginActivityBridge.toast(error.message, Toast.LENGTH_LONG)
        } catch (_: ActivityNotFoundException) {
            PluginActivityBridge.toast("No application can open this action", Toast.LENGTH_LONG)
        } catch (t : Throwable) {
            logError(t)
            PluginActivityBridge.toast(t.toString(), Toast.LENGTH_LONG)
        }
    }
}
