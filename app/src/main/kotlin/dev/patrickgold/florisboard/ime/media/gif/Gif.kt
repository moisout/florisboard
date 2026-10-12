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

import kotlinx.serialization.Serializable

/**
 * A GIF of a GIF provider.
 *
 * @property id The ID of the GIF, unique for the provider.
 * @property title The title of the GIF, used as its description.
 * @property previewUrl The URL of a small animated preview.
 * @property previewWidth The width of the preview in pixels.
 * @property previewHeight The height of the preview in pixels.
 * @property url The URL of the GIF which is inserted into editors.
 */
@Serializable
data class Gif(
    val id: String,
    val title: String,
    val previewUrl: String,
    val previewWidth: Int,
    val previewHeight: Int,
    val url: String,
) {
    val aspectRatio: Float
        get() = if (previewWidth > 0 && previewHeight > 0) previewWidth.toFloat() / previewHeight else 1f
}

data class GifPage(
    val gifs: List<Gif>,
    val page: Int,
    val hasNext: Boolean,
)
