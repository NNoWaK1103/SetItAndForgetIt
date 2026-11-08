package com.openticker.app

import com.openticker.app.settings.*
import com.openticker.app.data.remote.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ProxyAuthenticationTest {
    private val token = "a".repeat(64) // Synthetic fixture, never a deployed credential.
    @Test fun `tokens require exact lowercase hexadecimal and redact diagnostics`() {
        assertTrue(validProxyToken(token))
        listOf("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), token+"\n").forEach { assertFalse(validProxyToken(it)) }
        assertFalse(StockSettings(accessTokenEdit=ProxyTokenEdit(token)).toString().contains(token))
        assertEquals(proxyCredentialScope("https://EXAMPLE.com:443/market/"), proxyCredentialScope("https://example.com/market"))
        assertNotEquals(proxyCredentialScope("https://example.com/market"), proxyCredentialScope("https://example.com/other"))
    }
    @Test fun `stock transport uses bearer and never follows redirects`() = runTest {
        var calls=0
        val transport=OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("Bearer $token", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("redirect")
                .header("Location","https://another.example/v1/quotes").body("".toResponseBody()).build()
        }.build()
        val client=StockProxyHttpClient({ token },transport)
        assertFalse(client.client.followRedirects); assertFalse(client.client.followSslRedirects)
        try { client.get("https://example.com", "https://example.com/v1/quotes?symbols=HD".toHttpUrl()); fail() }
        catch(e: FeedHttpException) { assertEquals(302,e.statusCode) }
        assertEquals(1,calls)
    }
    @Test fun `missing malformed and wrong scope credentials fail before network`() = runTest {
        var calls=0; var lookups=0
        val transport=OkHttpClient.Builder().addInterceptor { calls++; error("Unexpected network") }.build()
        for(secret in listOf(null,"bad")) {
            val client=StockProxyHttpClient({ lookups++; secret },transport)
            try { client.get("https://example.com", "https://example.com/v1/quotes".toHttpUrl()); fail() }
            catch(_: ProxyAuthenticationException) { }
        }
        val client=StockProxyHttpClient({ lookups++; token },transport)
        for(url in listOf("https://evil.example/v1/quotes","http://example.com/v1/quotes", "https://example.com/other/v1/quotes", "https://example.com/v1/orders")) {
            try { client.get("https://example.com",url.toHttpUrl()); fail() } catch(_: IllegalArgumentException) { }
        }
        assertEquals(0,calls); assertEquals(2,lookups)
    }
}
