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
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.advanced.Backup
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import dev.patrickgold.florisboard.ime.keyboard3.interaction.InteractionKind
import dev.patrickgold.florisboard.ime.keyboard3.interaction.LocalInteractionController
import dev.patrickgold.florisboard.ime.keyboard3.interaction.showLongToast
import dev.patrickgold.florisboard.ime.keyboard3.interaction.showShortToast
import dev.patrickgold.florisboard.ime.media.MediaSearchLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.k3lp.lib.text.asK3String
import java.io.File

private const val GIF_MIME_TYPE = "image/gif"
private const val GIF_SEARCH_MIN_QUERY_LENGTH = 2
private const val GIF_SEARCH_DEBOUNCE_MILLIS = 700L
private val GifTileSpacing = 4.dp

private enum class GifCategory(val icon: ImageVector, val labelResId: Int) {
    RECENT(Icons.Default.Schedule, R.string.gif__category__recent),
    FAVORITES(Icons.Default.Favorite, R.string.gif__category__favorites),
    TRENDING(Icons.AutoMirrored.Default.TrendingUp, R.string.gif__category__trending),
}

private sealed interface GifLoadState {
    data object Idle : GifLoadState
    data object Loading : GifLoadState
    data class Failed(val error: Throwable) : GifLoadState
}

private object GifImageLoader {
    private var instance: ImageLoader? = null

    @Synchronized
    fun get(context: Context): ImageLoader {
        return instance ?: ImageLoader.Builder(context.applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
            .also { instance = it }
    }
}

/**
 * Holds what is needed to insert GIFs into the editor and to manage the favorites.
 */
private class GifActions(
    val sendingGifId: String?,
    val send: (Gif) -> Unit,
    val toggleFavorite: (Gif) -> Unit,
)

@Composable
private fun rememberGifActions(): GifActions {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val imeController = LocalImeController.current
    val interactionController = LocalInteractionController.current
    val scope = rememberCoroutineScope()

    var sendingGifId by remember { mutableStateOf<String?>(null) }

    return GifActions(
        sendingGifId = sendingGifId,
        send = send@{ gif ->
            if (sendingGifId != null) return@send
            interactionController.performFeedback(InteractionKind.KeyPress)
            sendingGifId = gif.id
            scope.launch {
                try {
                    val file = GifFileCache.get(context, gif.url)
                    val uri = FileProvider.getUriForFile(context, Backup.FILE_PROVIDER_AUTHORITY, file)
                    val isCommitted: Boolean
                    imeController.updateState {
                        isCommitted = emitContent(uri, GIF_MIME_TYPE, gif.title.ifBlank { "GIF" })
                    }
                    if (!isCommitted) {
                        imeController.updateState {
                            emitText(gif.url.asK3String())
                        }
                        interactionController.showLongToast(context.getString(R.string.gif__error__not_supported))
                    }
                    GifHistoryHelper.markGifUsed(prefs, gif)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    interactionController.showLongToast(
                        context.getString(R.string.gif__error__sending),
                        "error_message" to e.message,
                    )
                } finally {
                    sendingGifId = null
                }
            }
        },
        toggleFavorite = { gif ->
            interactionController.performFeedback(InteractionKind.LongPress)
            scope.launch {
                val isFavorite = GifHistoryHelper.toggleFavorite(prefs, gif)
                interactionController.showShortToast(
                    context.getString(if (isFavorite) R.string.gif__favorites__added else R.string.gif__favorites__removed)
                )
            }
        },
    )
}

@Composable
private fun gifErrorMessage(error: Throwable): String {
    return when (error) {
        is GifRateLimitException -> stringRes(R.string.gif__error__rate_limit)
        else -> stringRes(R.string.gif__error__loading, "error_message" to (error.message ?: error::class.simpleName))
    }
}

/**
 * The GIF panel of the media UI, showing the recently used, favorite and trending GIFs.
 */
@Composable
fun GifPanel(
    modifier: Modifier = Modifier,
    tabRowLeadingContent: @Composable () -> Unit = {},
) {
    val prefs by FlorisPreferenceStore
    val interactionController = LocalInteractionController.current
    val scope = rememberCoroutineScope()

    val history by prefs.gif.historyData.collectAsState()
    val actions = rememberGifActions()

    var category by remember {
        mutableStateOf(if (prefs.gif.historyData.get().recent.isEmpty()) GifCategory.TRENDING else GifCategory.RECENT)
    }

    var trendingGifs by remember { mutableStateOf(emptyList<Gif>()) }
    var trendingNextPage by remember { mutableStateOf<Int?>(1) }
    var trendingLoadState by remember { mutableStateOf<GifLoadState>(GifLoadState.Idle) }

    fun loadMoreTrending() {
        val page = trendingNextPage ?: return
        if (trendingLoadState == GifLoadState.Loading) return
        trendingLoadState = GifLoadState.Loading
        scope.launch {
            trendingLoadState = try {
                val gifPage = KlipyApi.trending(GifHistoryHelper.customerId(prefs), page)
                trendingGifs = (trendingGifs + gifPage.gifs).distinctBy { it.id }
                trendingNextPage = if (gifPage.hasNext && gifPage.gifs.isNotEmpty()) page + 1 else null
                GifLoadState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GifLoadState.Failed(e)
            }
        }
    }

    LaunchedEffect(category) {
        if (category == GifCategory.TRENDING && trendingGifs.isEmpty() && trendingLoadState == GifLoadState.Idle) {
            loadMoreTrending()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabRowLeadingContent()
            for (entry in GifCategory.entries) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            onClick = {
                                interactionController.performFeedback(InteractionKind.KeyPress)
                                category = entry
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    SnyggIcon(
                        elementName = FlorisImeUi.MediaEmojiTab.elementName,
                        selector = if (category == entry) SnyggSelector.FOCUS else SnyggSelector.NONE,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        imageVector = entry.icon,
                        contentDescription = stringRes(entry.labelResId),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val gifs = when (category) {
                GifCategory.RECENT -> history.recent
                GifCategory.FAVORITES -> history.favorites
                GifCategory.TRENDING -> trendingGifs
            }
            val loadState = if (category == GifCategory.TRENDING) trendingLoadState else GifLoadState.Idle
            when {
                gifs.isNotEmpty() -> GifGrid(
                    gifs = gifs,
                    history = history,
                    actions = actions,
                    isLoadingMore = loadState == GifLoadState.Loading,
                    onEndReached = { if (category == GifCategory.TRENDING) loadMoreTrending() },
                )
                loadState is GifLoadState.Loading -> GifLoadingIndicator()
                loadState is GifLoadState.Failed -> GifMessage(
                    text = gifErrorMessage(loadState.error),
                    onRetry = { loadMoreTrending() },
                )
                else -> GifMessage(
                    text = stringRes(when (category) {
                        GifCategory.RECENT -> R.string.gif__recent__empty_message
                        GifCategory.FAVORITES -> R.string.gif__favorites__empty_message
                        GifCategory.TRENDING -> R.string.gif__search__no_results
                    }),
                )
            }
        }
    }
}

/**
 * The search UI for GIFs. Shows the recently used GIFs until a search query has been entered.
 */
@Composable
fun GifSearchLayout(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val history by prefs.gif.historyData.collectAsState()

    MediaSearchLayout(
        onDismiss = onDismiss,
        placeholder = stringRes(R.string.gif__search__placeholder),
        resultsHeight = FlorisImeSizing.keyboardRowBaseHeight * 2f,
        modifier = modifier,
    ) { searchQuery ->
        val actions = rememberGifActions()
        var searchResults by remember { mutableStateOf<List<Gif>?>(null) }
        var loadState by remember { mutableStateOf<GifLoadState>(GifLoadState.Idle) }
        var retryCount by remember { mutableStateOf(0) }

        LaunchedEffect(searchQuery, retryCount) {
            searchResults = null
            if (searchQuery.length < GIF_SEARCH_MIN_QUERY_LENGTH) {
                loadState = GifLoadState.Idle
                return@LaunchedEffect
            }
            loadState = GifLoadState.Loading
            // Wait until the user pauses typing, as each search counts towards the hourly limit of the provider
            delay(GIF_SEARCH_DEBOUNCE_MILLIS)
            loadState = try {
                searchResults = KlipyApi.search(GifHistoryHelper.customerId(prefs), searchQuery, page = 1).gifs
                GifLoadState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GifLoadState.Failed(e)
            }
        }

        val results = searchResults
        val state = loadState
        when {
            state is GifLoadState.Loading -> GifLoadingIndicator()
            state is GifLoadState.Failed -> GifMessage(
                text = gifErrorMessage(state.error),
                onRetry = { retryCount++ },
            )
            results != null && results.isEmpty() -> GifMessage(text = stringRes(R.string.gif__search__no_results))
            else -> {
                val gifs = results ?: history.recent
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(GifTileSpacing),
                    horizontalArrangement = Arrangement.spacedBy(GifTileSpacing),
                ) {
                    items(gifs, key = { it.id }) { gif ->
                        GifTile(
                            gif = gif,
                            isFavorite = history.isFavorite(gif),
                            actions = actions,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(gif.aspectRatio),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GifGrid(
    gifs: List<Gif>,
    history: GifHistory,
    actions: GifActions,
    isLoadingMore: Boolean,
    onEndReached: () -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(gridState, gifs.size) {
        snapshotFlow {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            gifs.isNotEmpty() && lastVisibleIndex >= gifs.size - 4
        }.collect { isEndReached ->
            if (isEndReached) onEndReached()
        }
    }
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxSize(),
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(GifTileSpacing),
        verticalItemSpacing = GifTileSpacing,
        horizontalArrangement = Arrangement.spacedBy(GifTileSpacing),
    ) {
        items(gifs, key = { it.id }) { gif ->
            GifTile(
                gif = gif,
                isFavorite = history.isFavorite(gif),
                actions = actions,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(gif.aspectRatio),
            )
        }
        if (isLoadingMore) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.smartbarHeight),
                ) {
                    GifLoadingIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GifTile(
    gif: Gif,
    isFavorite: Boolean,
    actions: GifActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val style = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val previewFile by produceState<File?>(initialValue = null, gif.previewUrl) {
        value = try {
            GifFileCache.get(context, gif.previewUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(style.foreground().copy(alpha = 0.12f))
            .combinedClickable(
                onClick = { actions.send(gif) },
                onLongClick = { actions.toggleFavorite(gif) },
            ),
    ) {
        if (previewFile != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = previewFile,
                imageLoader = GifImageLoader.get(context),
                contentDescription = gif.title,
                contentScale = ContentScale.Crop,
            )
        }
        if (isFavorite) {
            SnyggIcon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp),
                imageVector = Icons.Default.Favorite,
            )
        }
        if (actions.sendingGifId == gif.id) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                GifLoadingIndicator()
            }
        }
    }
}

@Composable
private fun BoxScope.GifLoadingIndicator() {
    val style = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    CircularProgressIndicator(
        modifier = Modifier
            .align(Alignment.Center)
            .size(24.dp),
        color = style.foreground(),
        strokeWidth = 2.dp,
    )
}

@Composable
private fun BoxScope.GifMessage(
    text: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SnyggText(text = text)
        if (onRetry != null) {
            SnyggButton(onClick = onRetry) {
                SnyggText(text = stringRes(R.string.gif__action__retry))
            }
        }
    }
}
