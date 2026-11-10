package com.mutinymedia.android

import android.app.DownloadManager
import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.graphics.Bitmap
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
    private fun playerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) playerView(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun screenshot(name: String) {
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        if(Build.VERSION.SDK_INT >= 29) {
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MutinyRuntime")
            }
            target.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?.let { uri ->
                target.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
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
        rule.onNodeWithText("Library").performClick()
        rule.onNodeWithText(item.title).assertExists()
        rule.onNodeWithText("Find streams").performScrollTo().performClick()
        rule.onNodeWithText("Title details").assertExists()
        rule.waitUntil(60_000) { vm.currentItem.value?.id == item.id && !vm.busy.value }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Title details").assertExists()
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
            rule.onNodeWithText("Playback").performClick()
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
        rule.onNodeWithText("Mutiny Media").assertExists()
        video.delete()
    }

    @Test fun test05_searchScreenShowsAiAndCatalogFilters() {
        nav("Search")
        rule.onNodeWithText("Search titles").assertExists()
        rule.onNodeWithText("Load saved catalogs").assertExists()
        rule.onNodeWithTag("catalog-ask-ai").assertExists()
        rule.onNodeWithText("Filters").assertExists()
    }

    private fun assertHttpsPlayback(url: String,label: String) {
        val vm=model()
        rule.runOnIdle { vm.play(StreamOption("https-$label", "$label HTTPS fixture",url,"Public test media")) }
        lateinit var active: Player
        rule.waitUntil(120_000) {
            var found=false
            rule.activityRule.scenario.onActivity { activity -> playerView(activity.window.decorView)?.player?.let { active=it;found=true } }
            found
        }
        rule.waitUntil(180_000) {
            var playing=false
            rule.activityRule.scenario.onActivity {
                active.playerError?.let { throw AssertionError("$label playback failed: ${it.errorCodeName}",it) }
                playing=active.isPlaying && active.currentPosition>0
            }
            playing
        }
        screenshot("https-$label")
        rule.onNodeWithText("Close").performClick()
    }

    @Test fun test06_publicStremioManifestCatalogAndEpisodes() = runBlocking {
        val client=com.mutinymedia.android.data.StremioClient()
        val addon=client.inspectAddon("https://v3-cinemeta.strem.io/manifest.json")
        assertTrue(addon.supportsCatalog && addon.supportsMeta)
        assertTrue(client.catalog(addon,"Big Buck Bunny").any { it.title.contains("Big Buck Bunny",true) })
        val episodes=client.episodes(addon,MediaItem("tt0944947","series","Series fixture"))
        assertTrue(episodes.isNotEmpty())
        assertTrue(episodes.all { it.id.isNotBlank() })
    }
    @Test fun test07_httpsMp4Playback() = assertHttpsPlayback("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4","mp4")
    @Test fun test08_httpsHlsPlayback() = assertHttpsPlayback("https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8","hls")
    @Test fun test09_httpsDashPlayback() = assertHttpsPlayback("https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd","dash")

    @Test fun test10_stremioLoginScreenRejectsInvalidCredentials() {
        val vm=model()
        org.junit.Assume.assumeTrue(!vm.accounts.stremioStatus.value.saved && !vm.accounts.stremioStatus.value.connected)
        nav("Settings")
        rule.onNodeWithText("Account").performClick()
        rule.onNodeWithText("Stremio email").performScrollTo().performTextInput("mutiny-runtime-fixture@example.invalid")
        rule.onNodeWithText("Stremio password").performScrollTo().performTextInput("not-a-real-password")
        rule.onNodeWithText("Sign in to Stremio").performScrollTo().performClick()
        rule.waitUntil(120_000) { !vm.busy.value && vm.accounts.stremioStatus.value.error!=null }
        assertFalse(vm.accounts.stremioStatus.value.connected)
        assertNull(vm.secrets.readSecret("stremio_session"))
        screenshot("stremio-signin")
    }
    @Test fun test11_nuvioAdapterDiscoveryAndLoginScreen() {
        val vm=model()
        org.junit.Assume.assumeTrue(!vm.accounts.nuvioStatus.value.saved && !vm.accounts.nuvioStatus.value.connected)
        nav("Settings")
        rule.onNodeWithText("Account").performClick()
        rule.onNodeWithText("Nuvio email").performScrollTo().performTextInput("mutiny-runtime-fixture@example.invalid")
        rule.onNodeWithText("Nuvio password").performScrollTo().performTextInput("not-a-real-password")
        rule.onNodeWithText("Sign in to Nuvio").performScrollTo().performClick()
        rule.waitUntil(120_000) { !vm.busy.value && vm.accounts.nuvioStatus.value.error!=null }
        assertFalse(vm.accounts.nuvioStatus.value.connected)
        assertNull(vm.secrets.readSecret("nuvio_session"))
        screenshot("nuvio-signin")
    }
    @Test fun test12_stremioAuthKeyValidation() {
        val vm=model()
        org.junit.Assume.assumeTrue(!vm.accounts.stremioStatus.value.saved && !vm.accounts.stremioStatus.value.connected)
        nav("Settings")
        rule.onNodeWithText("Account").performClick()
        rule.onNodeWithText("Use an auth key instead").performScrollTo().performClick()
        rule.onNodeWithText("Stremio auth key").performScrollTo().performTextInput("not-a-real-auth-key")
        rule.onNodeWithText("Connect Stremio auth key").performScrollTo().performClick()
        rule.waitUntil(120_000) { !vm.busy.value && vm.accounts.stremioStatus.value.error!=null }
        assertFalse(vm.accounts.stremioStatus.value.connected)
        assertNull(vm.secrets.readSecret("stremio_session"))
    }

}
