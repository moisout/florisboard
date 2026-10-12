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

import dev.patrickgold.florisboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class GifRateLimitException : IOException("GIF provider rate limit reached")

/**
 * Client for the GIF API of KLIPY. The API key is provided at build time through `klipy.apiKey` in the
 * `local.properties` file, without it the GIF feature is not available.
 *
 * Responses are cached in memory, as the number of API calls per hour is limited.
 */
object KlipyApi {
    const val PER_PAGE = 24

    private const val BASE_URL = "https://api.klipy.com/api/v1"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_CACHED_PAGES = 48
    private const val HTTP_TOO_MANY_REQUESTS = 429

    val isConfigured: Boolean
        get() = BuildConfig.KLIPY_API_KEY.isNotBlank()

    private val cachedPages = object : LinkedHashMap<String, GifPage>(MAX_CACHED_PAGES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GifPage>?): Boolean {
            return size > MAX_CACHED_PAGES
        }
    }

    suspend fun trending(customerId: String, page: Int): GifPage {
        return request("gifs/trending", customerId, page, query = null)
    }

    suspend fun search(customerId: String, query: String, page: Int): GifPage {
        return request("gifs/search", customerId, page, query)
    }

    private suspend fun request(path: String, customerId: String, page: Int, query: String?): GifPage {
        val cacheKey = "$path|$page|${query?.lowercase()}"
        synchronized(cachedPages) { cachedPages[cacheKey] }?.let { return it }
        val params = buildMap {
            put("page", page.toString())
            put("per_page", PER_PAGE.toString())
            put("customer_id", customerId)
            put("locale", Locale.getDefault().language)
            if (query != null) put("q", query)
        }
        val paramsStr = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        val url = URL("$BASE_URL/${BuildConfig.KLIPY_API_KEY}/$path?$paramsStr")
        val json = withContext(Dispatchers.IO) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                    HTTP_TOO_MANY_REQUESTS -> throw GifRateLimitException()
                    // The URL contains the API key, so it must not end up in the error message
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        return parsePage(json).also { gifPage ->
            synchronized(cachedPages) { cachedPages[cacheKey] = gifPage }
        }
    }

    /**
     * Parses a page of GIFs as returned by the trending and search endpoints. Items which are not GIFs or lack
     * the required files are skipped.
     */
    fun parsePage(json: String): GifPage {
        val root = Json.parseToJsonElement(json).obj()
        if (root?.get("result").primitive()?.booleanOrNull == false) {
            throw IOException("GIF provider reported an error")
        }
        val data = root?.get("data").obj() ?: throw IOException("Unexpected response of GIF provider")
        val items = data["data"] as? List<*> ?: emptyList<JsonElement>()
        val gifs = items.mapNotNull { item ->
            val gif = (item as? JsonElement).obj() ?: return@mapNotNull null
            if (gif["type"].primitive()?.contentOrNull?.let { it != "gif" } == true) return@mapNotNull null
            val files = gif["file"].obj() ?: return@mapNotNull null
            val preview = files.gifFileOf("sm", "xs", "md", "hd") ?: return@mapNotNull null
            val full = files.gifFileOf("md", "hd", "sm", "xs") ?: return@mapNotNull null
            Gif(
                id = gif["id"].primitive()?.contentOrNull ?: return@mapNotNull null,
                title = gif["title"].primitive()?.contentOrNull ?: "",
                previewUrl = preview["url"].primitive()?.contentOrNull ?: return@mapNotNull null,
                previewWidth = preview["width"].primitive()?.intOrNull ?: 0,
                previewHeight = preview["height"].primitive()?.intOrNull ?: 0,
                url = full["url"].primitive()?.contentOrNull ?: return@mapNotNull null,
            )
        }
        return GifPage(
            gifs = gifs.distinctBy { it.id },
            page = data["current_page"].primitive()?.intOrNull ?: 1,
            hasNext = data["has_next"].primitive()?.booleanOrNull ?: false,
        )
    }

    private fun JsonObject.gifFileOf(vararg sizes: String): JsonObject? {
        return sizes.firstNotNullOfOrNull { size ->
            this[size].obj()?.get("gif").obj()?.takeIf { it["url"].primitive()?.contentOrNull != null }
        }
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.primitive(): JsonPrimitive? = this as? JsonPrimitive
}
