/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.media.emoji

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.emoji2.text.EmojiCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import dev.patrickgold.florisboard.ime.keyboard3.interaction.InteractionKind
import dev.patrickgold.florisboard.ime.keyboard3.interaction.LocalInteractionController
import dev.patrickgold.florisboard.ime.media.MediaSearchLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggText
import org.k3lp.lib.text.asK3String

private const val EmojiSearchMaxResults = 30L

@Composable
fun EmojiSearchLayout(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val imeController = LocalImeController.current
    val interactionController = LocalInteractionController.current
    val subtypeManager by context.subtypeManager()
    val scope = rememberCoroutineScope()

    val imeState by imeController.activeState.collectAsState()
    val editorInfo by remember { derivedStateOf { imeState.editor.info } }
    val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
    val preferredSkinTone by prefs.emoji.preferredSkinTone.collectAsState()
    val emojiHistoryEnabled by prefs.emoji.historyEnabled.collectAsState()

    val systemFontPaint = remember(Typeface.DEFAULT) {
        Paint().apply {
            typeface = Typeface.DEFAULT
        }
    }
    val metadataVersion = editorInfo.emojiCompatMetadataVersion
    val emojiCompatInstance by FlorisEmojiCompat.getAsFlow(editorInfo.emojiCompatReplaceAll).collectAsState()

    var emojiData by remember { mutableStateOf(EmojiData.Fallback) }
    LaunchedEffect(activeSubtype, preferredSkinTone) {
        // Fall back to english names if there is no emoji data for the active subtype
        emojiData = EmojiData.get(context, activeSubtype.primaryLocale)
            .takeUnless { it.bySkinTone[preferredSkinTone].isNullOrEmpty() }
            ?: EmojiData.get(context, FlorisLocale.ENGLISH)
    }
    val emojis = remember(emojiData, preferredSkinTone, emojiCompatInstance, metadataVersion, systemFontPaint) {
        emojiData.bySkinTone[preferredSkinTone].orEmpty().filter { emoji ->
            emojiCompatInstance?.getEmojiMatch(emoji.value, metadataVersion) == EmojiCompat.EMOJI_SUPPORTED ||
                systemFontPaint.hasGlyph(emoji.value)
        }
    }

    MediaSearchLayout(
        onDismiss = onDismiss,
        placeholder = stringRes(R.string.emoji__search__placeholder),
        resultsHeight = FlorisImeSizing.keyboardRowBaseHeight,
        modifier = modifier,
    ) { searchQuery ->
        var searchResults by remember { mutableStateOf(emptyList<Emoji>()) }
        LaunchedEffect(emojis, searchQuery, emojiHistoryEnabled) {
            searchResults = if (searchQuery.isNotEmpty()) {
                emojis.searchByInput(searchQuery, EmojiSearchMaxResults)
            } else {
                val keyguardManager = context.systemService(AndroidKeyguardManager::class)
                val deviceLocked = keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked
                if (emojiHistoryEnabled && !deviceLocked) {
                    prefs.emoji.historyData.get().let { it.pinned + it.recent }
                } else {
                    emptyList()
                }
            }.distinctBy { it.value }
        }

        if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
            SnyggText(
                modifier = Modifier.padding(horizontal = 12.dp),
                text = stringRes(R.string.emoji__search__no_results),
            )
        } else {
            LazyRow(modifier = Modifier.fillMaxWidth()) {
                items(searchResults, key = { it.value }) { emoji ->
                    SnyggBox(
                        elementName = FlorisImeUi.MediaEmojiKey.elementName,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        clickAndSemanticsModifier = Modifier.clickable {
                            interactionController.performFeedback(InteractionKind.KeyPress)
                            scope.launch {
                                imeController.updateState {
                                    emitText(emoji.value.asK3String())
                                }
                                EmojiHistoryHelper.markEmojiUsed(prefs, emoji)
                            }
                        },
                    ) {
                        EmojiText(
                            modifier = Modifier.align(Alignment.Center),
                            text = emoji.value,
                            emojiCompatInstance = emojiCompatInstance,
                        )
                    }
                }
            }
        }
    }
}
