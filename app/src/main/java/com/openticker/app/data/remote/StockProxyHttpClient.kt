package com.openticker.app.data.remote

import com.openticker.app.settings.proxyCredentialScope
import com.openticker.app.settings.validProxyToken
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class ProxyAuthenticationException : IOException("Worker access token required")

/** Dedicated transport: token never reaches weather/sports, redirects, or an upstream provider. */
class StockProxyHttpClient(
    private val tokenFor: suspend (String) -> String?,
    client: OkHttpClient = OkHttpClient()
) {
    internal val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

    suspend fun get(baseUrl: String, url: HttpUrl): String {
        val scope = proxyCredentialScope(baseUrl)
        val base = scope.toHttpUrl()
        require(url.isHttps && url.host == base.host && url.port == base.port &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            url.encodedPath in listOf("${base.encodedPath.trimEnd('/')}/v1/quotes", "${base.encodedPath.trimEnd('/')}/v1/history"))
        val token = tokenFor(baseUrl)?.takeIf(::validProxyToken) ?: throw ProxyAuthenticationException()
        val request = Request.Builder().url(url).header("User-Agent", "OpenTicker/1.4.2 (Android)")
            .header("Authorization", "Bearer $token").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        if (!response.isSuccessful) throw FeedHttpException(response.code)
                        response.body?.string() ?: throw IOException("Empty proxy response")
                    } }
                    continuation.resumeWith(result)
                }
            })
        }
    }
}
