/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.media.gif

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads GIF files into the cache directory of the app, so they can be displayed and shared with other apps.
 */
object GifFileCache {
    /** Name of the cache sub directory, must match the path declared in `res/xml/file_paths.xml`. */
    private const val DIR_NAME = "gifs"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_PARALLEL_DOWNLOADS = 4
    private const val MAX_CACHE_SIZE_BYTES = 64L * 1024 * 1024
    private const val MAX_FILE_SIZE_BYTES = 16L * 1024 * 1024

    private val downloadPermits = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val downloadGuards = ConcurrentHashMap<String, Mutex>()

    private fun dirOf(context: Context): FsDir {
        return context.cacheDir.subDir(DIR_NAME)
    }

    /**
     * Returns the cached file of given [url], downloading it first if required.
     */
    suspend fun get(context: Context, url: String): FsFile = withContext(Dispatchers.IO) {
        val dir = dirOf(context)
        val file = dir.subFile("${sha256(url)}.gif")
        downloadGuards.getOrPut(url) { Mutex() }.withLock {
            if (file.length() == 0L) {
                dir.mkdirs()
                downloadPermits.withPermit { download(url, file) }
                prune(dir, keep = file)
            } else {
                // Marks the file as recently used for pruning
                file.setLastModified(System.currentTimeMillis())
            }
        }
        downloadGuards.remove(url)
        file
    }

    private fun download(url: String, file: FsFile) {
        val tempFile = FsFile(file.path + ".tmp")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            if (connection.contentLengthLong > MAX_FILE_SIZE_BYTES) {
                throw IOException("GIF is too large")
            }
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tempFile.renameTo(file)) {
                throw IOException("Failed to store GIF")
            }
        } finally {
            tempFile.delete()
            connection.disconnect()
        }
    }

    /**
     * Deletes the least recently used files until the cache is within its size limit.
     */
    private fun prune(dir: FsDir, keep: FsFile) {
        val files = dir.listFiles().orEmpty().sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        for (file in files) {
            if (size <= MAX_CACHE_SIZE_BYTES) break
            if (file == keep) continue
            size -= file.length()
            file.delete()
        }
    }

    private fun sha256(str: String): String {
        return MessageDigest.getInstance("SHA-256").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
