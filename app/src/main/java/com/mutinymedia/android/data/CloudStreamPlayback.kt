package com.mutinymedia.android.data

import com.lagradost.cloudstream3.app
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Session cookies use normal domain/path/expiry rules and are shared with playback. */
object CloudStreamPlayback {
    private val clients = ConcurrentHashMap<String, OkHttpClient>()
    private val cookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
            }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
            cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return cookies.filter { it.matches(url) }
        }
    }
    @Synchronized fun initialize() {
        if (initialized) return
        app.baseClient = app.baseClient.newBuilder().cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                if (!chain.request().url.isHttps) throw java.io.IOException("CloudStream requests require HTTPS")
                chain.proceed(chain.request())
            }.build()
        initialized = true
    }
    private var initialized = false
    fun register(id: String, interceptor: Interceptor?) {
        val builder = app.baseClient.newBuilder()
        if (interceptor != null) builder.addInterceptor(interceptor)
        clients[id] = builder.build()
    }
    fun client(id: String): OkHttpClient? = clients[id]
    fun remove(packageId: String) { clients.keys.removeAll { it.startsWith("cloudstream:$packageId/") } }
}
