package com.mutinymedia.android.data

import android.content.Context
import com.lagradost.api.setContext
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.app
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

internal object CloudStreamNetwork {
    private val sessions = object : CookieJar {
        private val cookies = linkedMapOf<Triple<String, String, String>, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                val key = Triple(cookie.domain, cookie.path, cookie.name)
                if (cookie.expiresAt > System.currentTimeMillis()) this.cookies[key] = cookie
                else this.cookies.remove(key)
            }
            while (this.cookies.size > 1024) this.cookies.remove(this.cookies.keys.first())
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            cookies.values.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return cookies.values.filter { it.matches(url) }.sortedByDescending { it.path.length }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(sessions)
        .followSslRedirects(false)
        .callTimeout(40, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!request.url.isHttps) throw IOException("CloudStream network requests require HTTPS")
            val initial = chain.call().request().url
            if (initial.host == request.url.host && initial.port == request.url.port && initial.scheme == request.url.scheme) {
                chain.proceed(request)
            } else {
                val scoped = request.newBuilder().removeHeader("Authorization").removeHeader("Cookie")
                val matching = sessions.loadForRequest(request.url)
                if (matching.isNotEmpty()) scoped.header("Cookie", matching.joinToString("; ") { "${it.name}=${it.value}" })
                chain.proceed(scoped.build())
            }
        }
        .build()

    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        setContext(WeakReference(context.applicationContext))
        app.baseClient = client
        NewPipe.init(checkNotNull(DownloaderTestImpl.init(client.newBuilder())))
        initialized = true
    }
}
