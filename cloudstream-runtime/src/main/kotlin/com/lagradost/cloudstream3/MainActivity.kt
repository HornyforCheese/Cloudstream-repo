package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.nicehttp.NiceFile
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

// Short name for requests client to make it nicer to use

var app: Requests = ProviderRequests(responseParser = object : ResponseParser {
    val mapper: ObjectMapper = jacksonObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    )

    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return mapper.readValue(text, kClass.java)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            mapper.readValue(text, kClass.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
}).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/** Keep the upstream Requests ABI while enforcing TLS for requests made through the host. */
private class ProviderRequests(responseParser: ResponseParser) : Requests(responseParser = responseParser) {
    override suspend fun custom(
        method: String,
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
        data: Map<String, String>?,
        files: List<NiceFile>?,
        json: Any?,
        requestBody: RequestBody?,
        allowRedirects: Boolean,
        cacheTime: Int,
        cacheUnit: TimeUnit,
        timeout: Long,
        interceptor: Interceptor?,
        verify: Boolean,
        responseParser: ResponseParser?,
    ): NiceResponse {
        require(url.toHttpUrlOrNull()?.isHttps == true) { "CloudStream network requests require HTTPS" }
        return super.custom(method, url, headers, referer, params, cookies, data, files, json,
            requestBody, allowRedirects, cacheTime, cacheUnit, timeout.takeIf { it in 1..40 } ?: 40,
            interceptor, true, responseParser)
    }
}
