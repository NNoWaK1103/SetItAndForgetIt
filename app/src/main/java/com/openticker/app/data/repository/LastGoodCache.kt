package com.openticker.app.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Versioned, private, bounded snapshots. A failed write never replaces the last complete file. */
class LastGoodCache(private val directory: File) {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }
    @PublishedApi internal suspend fun readText(key: String): String? = withContext(Dispatchers.IO) {
        try { file(key).takeIf { it.isFile && it.length() in 1..MAX_BYTES }?.readText() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    suspend inline fun <reified T> read(key: String): T? {
        val text = readText(key) ?: return null
        return try { json.decodeFromString<T>(text) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    suspend inline fun <reified T> write(key: String, value: T): Boolean = try {
        writeText(key, json.encodeToString(value))
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }

    @PublishedApi internal suspend fun writeText(key: String, text: String): Boolean = withContext(Dispatchers.IO) {
        var temporary: File? = null
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_BYTES) return@withContext false
            directory.mkdirs()
            temporary = File.createTempFile("snapshot-", ".tmp", directory)
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file(key).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // Bound old location/provider snapshots; current successful writes are most recent.
            directory.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }
                ?.drop(16)?.forEach { it.delete() }
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        finally { temporary?.delete() }
    }
    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "v1-$digest.json")
    }
    companion object { private const val MAX_BYTES = 8L * 1024 * 1024 }
}
