package com.openticker.app.data.remote

import java.io.IOException

class FeedHttpException(val statusCode: Int) : IOException("Provider returned HTTP $statusCode")
fun feedFailureMessage(error: Exception, label: String): String = when (error) {
    is FeedHttpException -> "$label service error (HTTP ${error.statusCode}) · retrying"
    is IOException -> "$label connection failed · check network or provider · retrying"
    else -> "$label response could not be read · retrying"
}
