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
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.input.getTextAfterSelection
import androidx.compose.ui.text.input.getTextBeforeSelection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.emoji2.text.EmojiCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.io.LocalStorageController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.ImeController
import dev.patrickgold.florisboard.ime.keyboard3.ImeEditor
import dev.patrickgold.florisboard.ime.keyboard3.ImeState
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import dev.patrickgold.florisboard.ime.keyboard3.interaction.InteractionKind
import dev.patrickgold.florisboard.ime.keyboard3.interaction.LocalInteractionController
import dev.patrickgold.florisboard.ime.keyboard3.ui.ImeKeyboardBox
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.window.ImeModalOverlay
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.k3lp.lib.text.asK3String
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference

private const val EmojiSearchMaxResults = 30L
private val EmojiSearchFontSize = 16.sp

@Composable
fun EmojiSearchLayout(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val imeController = LocalImeController.current
    val interactionController = LocalInteractionController.current
    val storageController = LocalStorageController.current
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

    val editor = remember {
        // Must be a text editor, an editor without input type is treated as a raw editor without selection support
        val searchEditorInfo = FlorisEditorInfo.wrap(
            EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_SEARCH
            }
        )
        object : ImeEditor(WeakReference(null), searchEditorInfo) {
            var value by mutableStateOf(TextFieldValue())

            override fun replaceText(range: IntRange, text: String, newSelection: K3TextRange, newComposition: K3TextRange?) {
                value = value.copy(
                    text = value.text.replaceRange(range, text),
                    selection = newSelection.let { TextRange(it.start, it.end) },
                    composition = newComposition?.let { TextRange(it.start, it.end) },
                )
            }

            override fun setComposition(newComposition: K3TextRange?) {
                value = value.copy(
                    composition = newComposition?.let { TextRange(it.start, it.end) },
                )
            }

            override fun getSurroundingText(charsBefore: Int, charsAfter: Int): K3SurroundingText {
                return value.getSurroundingText(charsBefore, charsAfter)
            }

            override fun setSelection(start: Int, end: Int) {
                val length = value.text.length
                value = value.copy(selection = TextRange(start.coerceIn(0, length), end.coerceIn(0, length)))
            }

            override fun deleteRange(start: Int, end: Int) {
                val length = value.text.length
                val range = TextRange(start.coerceIn(0, length), end.coerceIn(0, length))
                value = TextFieldValue(
                    text = value.text.removeRange(range.min, range.max),
                    selection = TextRange(range.min),
                )
            }

            override fun sendDownUpKeyEvent(keyCode: Int, metaState: Int): Boolean {
                val text = value.text
                val cursor = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val offset = value.selection.min
                        if (value.selection.collapsed && offset > 0) text.offsetByCodePoints(offset, -1) else offset
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val offset = value.selection.max
                        if (value.selection.collapsed && offset < text.length) text.offsetByCodePoints(offset, 1) else offset
                    }
                    else -> return false
                }
                value = value.copy(selection = TextRange(cursor))
                return true
            }
        }
    }
    val inputMethod = remember {
        ImeController(storageController, imeController.clipboardManager, ImeState(editor = editor), imeController.touchModelCache)
    }
    val inputMethodState by inputMethod.activeState.collectAsState()

    LaunchedEffect(imeState.model) {
        inputMethod.updateState {
            switchModel(imeState.model)
        }
    }
    // Searching is modal: the app behind is dimmed and tapping it or pressing back closes the search
    val windowController = LocalWindowController.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(windowController) {
        val modalOverlay = ImeModalOverlay(onDismiss = { currentOnDismiss() })
        windowController.showModalOverlay(modalOverlay)
        onDispose { windowController.hideModalOverlay(modalOverlay) }
    }
    // Mirrors what the system does for a real editor: report selection changes which were not caused by typing,
    // e.g. by swipe gestures or by tapping into the search field
    LaunchedEffect(editor, inputMethod) {
        snapshotFlow { editor.value.text to editor.value.selection }.collect { (_, selection) ->
            inputMethod.updateState {
                handleUpdateSelection(selection.toK3TextRange())
            }
        }
    }
    // Keys of the search keyboard which switch away from the text panel (e.g. the emoji key) close the search
    LaunchedEffect(inputMethodState.flags.imeUiMode) {
        if (inputMethodState.flags.imeUiMode != ImeUiMode.TEXT) {
            onDismiss()
        }
    }

    fun updateSearchQuery(newValue: TextFieldValue) {
        scope.launch {
            inputMethod.updateState {
                editor.value = newValue
                resetContent(
                    newSelection = newValue.selection.toK3TextRange(),
                    newSurrounding = newValue.getSurroundingText(50, 10),
                )
            }
        }
    }

    val searchQuery = editor.value.text.trim()
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

    val style = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val textStyle = TextStyle(color = style.foreground(), fontSize = EmojiSearchFontSize)

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth(),
    ) {
        SnyggRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIconButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                onClick = {
                    interactionController.performFeedback(InteractionKind.KeyPress)
                    onDismiss()
                },
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Default.ArrowBack)
            }
            EmojiSearchField(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                value = editor.value,
                onSelectionChange = { selection -> editor.value = editor.value.copy(selection = selection) },
                textStyle = textStyle,
                placeholder = stringRes(R.string.emoji__search__placeholder),
            )
            if (editor.value.text.isNotEmpty()) {
                SnyggIconButton(
                    elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                    onClick = {
                        interactionController.performFeedback(InteractionKind.KeyPress)
                        updateSearchQuery(TextFieldValue())
                    },
                ) {
                    SnyggIcon(imageVector = Icons.Default.Close)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.keyboardRowBaseHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
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
        CompositionLocalProvider(LocalImeController provides inputMethod) {
            ImeKeyboardBox()
        }
    }
}

/**
 * A single line text field for use within the IME window. As the IME window can never be focused, a regular text
 * field would neither show its cursor nor its selection, so both are drawn here.
 */
@Composable
private fun EmojiSearchField(
    value: TextFieldValue,
    onSelectionChange: (TextRange) -> Unit,
    textStyle: TextStyle,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Restart the blinking on each change, so the cursor is always visible while typing or moving it
    val isCursorVisible by produceState(initialValue = true, value) {
        this.value = true
        while (true) {
            delay(500)
            this.value = !this.value
        }
    }
    val text = remember(value.text, value.composition) {
        buildAnnotatedString {
            append(value.text)
            value.composition?.let { composition ->
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline), composition.min, composition.max)
            }
        }
    }

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.text.isEmpty()) {
            SnyggText(text = placeholder)
        }
        BasicText(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        textLayout?.let { onSelectionChange(TextRange(it.getOffsetForPosition(position))) }
                    }
                }
                .drawWithContent {
                    drawContent()
                    val layout = textLayout ?: return@drawWithContent
                    val length = layout.layoutInput.text.length
                    val start = value.selection.min.coerceIn(0, length)
                    val end = value.selection.max.coerceIn(0, length)
                    if (start != end) {
                        drawPath(layout.getPathForRange(start, end), textStyle.color.copy(alpha = 0.3f))
                    } else if (isCursorVisible) {
                        val cursorRect = layout.getCursorRect(end)
                        drawLine(
                            color = textStyle.color,
                            start = cursorRect.topCenter,
                            end = cursorRect.bottomCenter,
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                },
            text = text,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayout = it },
        )
    }
}

fun TextRange.toK3TextRange() = K3TextRange(start, end)

fun TextFieldValue.getSurroundingText(charsBefore: Int, charsAfter: Int) = K3SurroundingText(
    textBefore = getTextBeforeSelection(charsBefore).text,
    textSelected = getSelectedText().text,
    textAfter = getTextAfterSelection(charsAfter).text,
)
