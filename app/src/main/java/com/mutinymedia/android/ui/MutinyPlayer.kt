package com.mutinymedia.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.delay
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mutinymedia.android.domain.StreamOption
import com.mutinymedia.android.domain.UserSettings

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MutinyPlayer(stream: StreamOption, settings: UserSettings, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var savedPosition by rememberSaveable(stream.url) { mutableLongStateOf(0L) }
    var shouldPlay by rememberSaveable(stream.url) { mutableStateOf(true) }
    val selector = remember(stream.url) { DefaultTrackSelector(context) }
    val bandwidthMeter = remember(stream.url, stream.headers) {
        DefaultBandwidthMeter.Builder(context).build()
    }
    val player = remember(stream.url, stream.headers) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(stream.headers)
        ExoPlayer.Builder(context).setTrackSelector(selector)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))).build().apply {
            setMediaItem(MediaItem.fromUri(requireNotNull(stream.url)))
            seekTo(savedPosition)
            prepare()
            playWhenReady = shouldPlay
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    savedPosition = player.currentPosition
                    shouldPlay = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = shouldPlay
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savedPosition = player.currentPosition
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(settings.lowBandwidth, settings.videoKbps, settings.audioKbps, settings.resolution, settings.adaptive) {
        val builder = selector.buildUponParameters()
        if (settings.lowBandwidth) {
            val width = when (settings.resolution) { 240 -> 426; 360 -> 640; else -> 854 }
            builder.setMaxVideoBitrate(settings.videoKbps * 1000)
                .setMaxVideoSize(width, settings.resolution)
                .setMaxAudioBitrate(settings.audioKbps * 1000)
                .setForceLowestBitrate(!settings.adaptive)
        } else {
            builder.setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxAudioBitrate(Int.MAX_VALUE)
                .setForceLowestBitrate(false)
        }
        selector.setParameters(builder)
    }
    var resizeMode by remember(stream.url) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackError by remember(stream.url) { mutableStateOf<String?>(null) }
    var showBitrate by rememberSaveable(stream.url) { mutableStateOf(false) }
    var bitrateText by remember(player) { mutableStateOf<String?>(null) }
    LaunchedEffect(player, showBitrate) {
        while (showBitrate) {
            bitrateText = "Video: ${formatBitrate(player.videoFormat?.bitrate?.toLong())} · " +
                "Audio: ${formatBitrate(player.audioFormat?.bitrate?.toLong())} · " +
                "Network estimate: ${formatBitrate(bandwidthMeter.bitrateEstimate)}"
            delay(1500)
        }
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "Playback failed: ${error.errorCodeName}. Try another source."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Now playing", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Text(stream.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }) {
                Text(if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fill / crop" else "Fit screen")
            }
            OutlinedButton(onClick = onExit) { Text("Close") }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = true
                        this.resizeMode = resizeMode
                        keepScreenOn = true
                    }
                },
                update = { it.player = player; it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize(),
            )
        }
        TextButton(onClick = { showBitrate = !showBitrate }) {
            Text(if (showBitrate) "Hide bitrate" else "Show bitrate")
        }
        if (showBitrate) {
            Text(
                bitrateText.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (playbackError != null) {
            Text(playbackError!!, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { playbackError = null; player.prepare(); player.play() }) { Text("Retry playback") }
        }
        if (settings.lowBandwidth) Text("Low bandwidth preference: ${settings.videoKbps} kbps, ${settings.resolution}p. Only available encoded tracks can be selected.",
            modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBitrate(bitsPerSecond: Long?): String =
    if (bitsPerSecond == null || bitsPerSecond <= 0) "Unavailable"
    else "${bitsPerSecond / 1000} kbps"
