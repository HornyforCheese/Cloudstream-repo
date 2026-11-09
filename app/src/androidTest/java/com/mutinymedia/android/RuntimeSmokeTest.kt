package com.mutinymedia.android

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mutinymedia.android.data.SettingsStore
import com.mutinymedia.android.domain.MediaItem
import com.mutinymedia.android.domain.StreamOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** Offline device smoke tests. Catalog/player fixtures do not establish live provider compatibility. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RuntimeSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun model(): MutinyViewModel {
        lateinit var result: MutinyViewModel
        rule.activityRule.scenario.onActivity { result = ViewModelProvider(it)[MutinyViewModel::class.java] }
        return result
    }
    private var stateCaptured = false
    private var originalEncryptedKey: String? = null
    private var originalLowBandwidth = false
    private var originalFixture: MediaItem? = null

    @Before fun preserveStoredState() {
        originalEncryptedKey = target.getSharedPreferences("mutiny_secure", Context.MODE_PRIVATE).getString("openrouter", null)
        val store = SettingsStore(target)
        originalLowBandwidth = runBlocking { store.settings.first() }.lowBandwidth
        originalFixture = runBlocking { store.watchlist.first() }.firstOrNull { it.id == "runtime-fixture" && it.type == "movie" }
        stateCaptured = true
    }

    @After fun restoreStoredState() {
        if (!stateCaptured) return
        target.getSharedPreferences("mutiny_secure", Context.MODE_PRIVATE).edit().putString("openrouter", originalEncryptedKey).commit()
        runBlocking {
            val store = SettingsStore(target)
            store.setLowBandwidth(originalLowBandwidth)
            originalFixture?.let { store.addToWatchlist(it) }
                ?: store.removeFromWatchlist(MediaItem("runtime-fixture", "movie", "Offline runtime fixture"))
        }
    }

    private fun nav(label: String) { rule.onAllNodesWithText(label).onLast().performClick() }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    private fun playerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) playerView(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun screenshot(name: String) {
        val dir = target.getExternalFilesDir("Pictures")!!
        dir.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun test01_navigationWatchlistAndMissingKey() {
        rule.waitForIdle()
        val vm = model()
        vm.secrets.saveOpenRouter("")
        nav("Browse")
        rule.onNodeWithText("Discover & play").assertIsDisplayed()
        val item = MediaItem("runtime-fixture", "movie", "Offline runtime fixture")
        rule.runOnIdle { vm.catalog.value = listOf(item) }
        rule.onNodeWithText("Save to watchlist").performScrollTo().performClick()
        rule.waitUntil(60_000) { vm.watchlist.value.any { it.id == item.id } }
        nav("Home")
        rule.onNodeWithText("My local library").performScrollTo().performClick()
        rule.onNodeWithText(item.title).assertExists()
        rule.onNodeWithText("Find streams").performScrollTo().performClick()
        rule.onNodeWithText("Discover & play").assertExists()
        rule.waitUntil(60_000) { vm.currentItem.value?.id == item.id && !vm.busy.value }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Discover & play").assertExists()
        assertTrue(runBlocking { SettingsStore(target).watchlist.first() }.any { it.id == item.id })
        nav("Ask AI")
        rule.onNode(hasSetTextAction()).performTextInput("Recommend a film")
        rule.onNodeWithText("Send").performClick()
        rule.onNodeWithText("Connect OpenRouter in Settings first").assertExists()
        assertTrue(model().chat.value.isEmpty())
        screenshot("navigation-ai")
    }

    @Test fun test02_settingsAndEncryptedKey() {
        val vm = model()
        try {
            vm.secrets.saveOpenRouter("runtime-fixture-not-a-real-key")
            assertEquals("runtime-fixture-not-a-real-key", vm.secrets.readOpenRouter())
            val raw = target.getSharedPreferences("mutiny_secure", Context.MODE_PRIVATE).getString("openrouter", "")!!
            assertFalse(raw.contains("runtime-fixture-not-a-real-key"))
            nav("Settings")
            rule.onNodeWithText("Low bandwidth").performClick()
            val initial = originalLowBandwidth
            rule.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
            rule.waitUntil(60_000) { vm.settings.value.lowBandwidth != initial }
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText("Ultra Low Bandwidth Mode").assertExists()
            assertEquals(!initial, runBlocking { SettingsStore(target).settings.first() }.lowBandwidth)
            screenshot("settings")
        } finally {
            vm.secrets.saveOpenRouter("")
            runBlocking { vm.store.setLowBandwidth(originalLowBandwidth) }
        }
        assertNull(vm.secrets.readOpenRouter())
    }

    @Test fun test03_downloadManagerQueue() {
        val manager = target.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = model().android.downloadFile("https://example.org/", "runtime-check.html")
        try {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("runtime-check.html", cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                android.util.Log.i("MutinyRuntime", "DownloadManager queued fixture id=$id status=$status; completion not asserted")
            }
        } finally { manager.remove(id) }
    }

    @Test fun test04_offlinePlaybackLifecycleAndFit() {
        val video = File(target.cacheDir, "runtime-player.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("player-sample.mp4").use { input ->
            video.outputStream().use { input.copyTo(it) }
        }
        // Inject a local fixture directly: production browsing still accepts HTTPS sources only.
        val vm = model()
        rule.runOnIdle { vm.activeStream.value = StreamOption("offline-runtime", "Offline playback fixture", video.toURI().toString(), "Device test") }
        lateinit var player: Player
        rule.waitUntil(120_000) {
            var found = false
            rule.activityRule.scenario.onActivity { activity ->
                playerView(activity.window.decorView)?.player?.let { player = it; player.repeatMode = Player.REPEAT_MODE_ONE; found = true }
            }
            found
        }
        rule.waitUntil(180_000) {
            var playing = false
            rule.activityRule.scenario.onActivity { playing = player.isPlaying && player.currentPosition > 0 }
            playing
        }
        screenshot("player")
        rule.onNodeWithText("Fill / crop").performClick()
        rule.activityRule.scenario.onActivity { assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, playerView(it.window.decorView)!!.resizeMode) }
        rule.onNodeWithText("Fit screen").performClick()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.onActivity { assertFalse(player.playWhenReady) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(60_000) {
            var playing = false
            rule.activityRule.scenario.onActivity { playing = player.isPlaying }
            playing
        }
        rule.activityRule.scenario.onActivity { player.pause() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.activityRule.scenario.onActivity { assertFalse(player.playWhenReady) }
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("All aboard, mateys.").assertExists()
        video.delete()
    }

    @Test fun test05_installedGameDiscoveryAndLaunch() {
        val vm = model()
        val game = vm.android.installedGames().firstOrNull { it.packageName == "com.mutinymedia.fixturegame" }
        org.junit.Assume.assumeNotNull(game) // Install the isolated game fixture to exercise this optional test.
        nav("Games")
        rule.onNodeWithText("Debug Game Fixture").assertExists()
        assertTrue(vm.android.launchGame(game!!))
        try {
            rule.waitUntil(120_000) {
                shell("dumpsys activity activities").lineSequence().any { it.contains("mResumedActivity") && it.contains(game.packageName) }
            }
            screenshot("game-launched")
        } finally { shell("input keyevent 4") }
    }
}
