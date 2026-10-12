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

import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The favorite and recently used GIFs of the user. Both are stored on the device only.
 */
@Serializable
data class GifHistory(
    val favorites: List<Gif>,
    val recent: List<Gif>,
) {
    fun isFavorite(gif: Gif): Boolean {
        return favorites.any { it.id == gif.id }
    }

    object Serializer : PreferenceSerializer<GifHistory> {
        private val HistoryJson = Json { ignoreUnknownKeys = true }

        override fun serialize(value: GifHistory): String {
            return HistoryJson.encodeToString(value)
        }

        override fun deserialize(value: String): GifHistory {
            try {
                return HistoryJson.decodeFromString(value)
            } catch (e: Exception) {
                flogError { "Failed to deserialize GifHistory: $e" }
                return Empty
            }
        }
    }

    companion object {
        val Empty = GifHistory(emptyList(), emptyList())

        const val MAX_RECENT_SIZE = 48
    }
}

object GifHistoryHelper {
    private val historyGuard = Mutex(locked = false)

    suspend fun markGifUsed(prefs: FlorisPreferenceModel, gif: Gif): Unit = historyGuard.withLock {
        val history = prefs.gif.historyData.get()
        val recent = (listOf(gif) + history.recent.filter { it.id != gif.id }).take(GifHistory.MAX_RECENT_SIZE)
        prefs.gif.historyData.set(history.copy(recent = recent))
    }

    /**
     * Adds given [gif] to the favorites or removes it, if it already is one. Returns true if it is a favorite now.
     */
    suspend fun toggleFavorite(prefs: FlorisPreferenceModel, gif: Gif): Boolean = historyGuard.withLock {
        val history = prefs.gif.historyData.get()
        val isFavorite = history.isFavorite(gif)
        val favorites = if (isFavorite) {
            history.favorites.filter { it.id != gif.id }
        } else {
            listOf(gif) + history.favorites
        }
        prefs.gif.historyData.set(history.copy(favorites = favorites))
        !isFavorite
    }

    /**
     * Returns the random ID which identifies this installation towards the GIF provider, as required by its API.
     */
    suspend fun customerId(prefs: FlorisPreferenceModel): String = historyGuard.withLock {
        prefs.gif.customerId.get().ifBlank {
            UUID.randomUUID().toString().also { prefs.gif.customerId.set(it) }
        }
    }
}
