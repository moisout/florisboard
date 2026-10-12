/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.media

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.ImeActions
import dev.patrickgold.florisboard.ime.keyboard3.ui.ImeKeyButton
import dev.patrickgold.florisboard.ime.media.emoji.EmojiData
import dev.patrickgold.florisboard.ime.media.emoji.EmojiPaletteView
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSearchLayout
import dev.patrickgold.florisboard.ime.media.gif.GifPanel
import dev.patrickgold.florisboard.ime.media.gif.GifSearchLayout
import dev.patrickgold.florisboard.ime.media.gif.KlipyApi
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

@SuppressLint("MutableCollectionMutableState")
@Composable
fun MediaInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var emojiLayoutDataMap by remember { mutableStateOf(EmojiData.Fallback) }
    LaunchedEffect(Unit) {
        emojiLayoutDataMap = EmojiData.get(context, "ime/media/emoji/root.txt")
    }

    val isGifPanelAvailable = KlipyApi.isConfigured
    var mediaTab by remember { mutableStateOf(MediaTab.EMOJI) }
    var isSearch by remember { mutableStateOf(false) }
    if (isSearch) {
        when (mediaTab) {
            MediaTab.EMOJI -> EmojiSearchLayout(onDismiss = { isSearch = false })
            MediaTab.GIF -> GifSearchLayout(onDismiss = { isSearch = false })
        }
    } else {
        SnyggColumn(
            elementName = FlorisImeUi.Media.elementName,
            modifier = modifier
                .fillMaxWidth()
                // The media panel is a bit taller than the keyboard to show more at once
                .height(FlorisImeSizing.imeUiHeight() + FlorisImeSizing.keyboardRowBaseHeight * 0.75f),
        ) {
            when (mediaTab) {
                MediaTab.EMOJI -> EmojiPaletteView(
                    modifier = Modifier.weight(1f),
                    fullEmojiMappings = emojiLayoutDataMap,
                    tabRowLeadingContent = { MediaSearchButton(onClick = { isSearch = true }) },
                )
                MediaTab.GIF -> GifPanel(
                    modifier = Modifier.weight(1f),
                    tabRowLeadingContent = { MediaSearchButton(onClick = { isSearch = true }) },
                )
            }
            SnyggRow(
                elementName = FlorisImeUi.MediaBottomRow.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
            ) {
                val bottomRowStyle = rememberSnyggThemeQuery(FlorisImeUi.MediaBottomRowButton.elementName)
                ImeKeyButton(
                    elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                    output = ImeActions.ShowTextPanel,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    // The label is drawn as text with a fixed size instead of the icon of the action, as the size
                    // of icons in this row depends on the theme and is tiny for themes which do not define it
                    Text(
                        text = stringRes(R.string.media__text_panel_label),
                        color = bottomRowStyle.foreground(),
                        fontSize = MediaBottomRowLabelFontSize,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isGifPanelAvailable) {
                    for (tab in MediaTab.entries) {
                        SnyggButton(
                            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .align(Alignment.CenterVertically),
                            onClick = { mediaTab = tab },
                        ) {
                            val selector = if (mediaTab == tab) SnyggSelector.FOCUS else SnyggSelector.NONE
                            when (tab) {
                                MediaTab.EMOJI -> SnyggIcon(
                                    elementName = FlorisImeUi.MediaEmojiTab.elementName,
                                    selector = selector,
                                    modifier = Modifier.size(MediaBottomRowIconSize),
                                    imageVector = Icons.Default.EmojiEmotions,
                                )
                                MediaTab.GIF -> {
                                    val tabStyle = rememberSnyggThemeQuery(
                                        elementName = FlorisImeUi.MediaEmojiTab.elementName,
                                        selector = selector,
                                    )
                                    Text(
                                        text = stringRes(R.string.gif__tab_label),
                                        color = tabStyle.foreground(),
                                        fontSize = MediaBottomRowLabelFontSize,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                ImeKeyButton(
                    elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                    output = ImeActions.Backspace,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

private val MediaBottomRowLabelFontSize = 16.sp
private val MediaBottomRowIconSize = 24.dp

private enum class MediaTab {
    EMOJI,
    GIF,
}
