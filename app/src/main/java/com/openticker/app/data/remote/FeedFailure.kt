package com.openticker.app.data.remote

import java.io.IOException

class FeedHttpException(val statusCode: Int) : IOException("Provider returned HTTP $statusCode")
fun feedFailureMessage(error: Exception, label: String): String = when (error) {
    is ProxyAuthenticationException -> "Add or replace the Worker access token in Settings"
    is FeedHttpException -> if (error.statusCode == 401 || error.statusCode == 403) "$label access denied · check the Worker access token and access policy" else "$label service error (HTTP ${error.statusCode}) · retrying"
    is IOException -> "$label connection failed · check network or provider · retrying"
    else -> "$label response could not be read · retrying"
}
