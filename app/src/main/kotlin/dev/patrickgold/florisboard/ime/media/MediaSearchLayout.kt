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

package dev.patrickgold.florisboard.ime.media

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.input.getTextAfterSelection
import androidx.compose.ui.text.input.getTextBeforeSelection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference

private val MediaSearchFontSize = 16.sp
private val MediaSearchButtonHeight = 32.dp

/**
 * A search UI for media, consisting of a search field, an area for the results and a keyboard which types into the
 * search field instead of the editor of the app.
 *
 * Searching is modal: the app behind is dimmed and tapping it, pressing back or hiding the keyboard closes the
 * search.
 *
 * @param onDismiss Called if the search should be closed.
 * @param placeholder The text shown in the search field while it is empty.
 * @param resultsHeight The height of the results area.
 * @param results The content of the results area for the given trimmed search query. Inside of it
 *  [LocalImeController] is the controller of the app editor, so results can be inserted with it.
 */
@Composable
fun MediaSearchLayout(
    onDismiss: () -> Unit,
    placeholder: String,
    resultsHeight: Dp,
    modifier: Modifier = Modifier,
    results: @Composable BoxScope.(query: String) -> Unit,
) {
    val imeController = LocalImeController.current
    val interactionController = LocalInteractionController.current
    val storageController = LocalStorageController.current
    val scope = rememberCoroutineScope()

    val imeState by imeController.activeState.collectAsState()

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

    val style = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val textStyle = TextStyle(color = style.foreground(), fontSize = MediaSearchFontSize)

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
            MediaSearchField(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                value = editor.value,
                onSelectionChange = { selection -> editor.value = editor.value.copy(selection = selection) },
                textStyle = textStyle,
                placeholder = placeholder,
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
                .height(resultsHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            results(searchQuery)
        }
        CompositionLocalProvider(LocalImeController provides inputMethod) {
            ImeKeyboardBox()
        }
    }
}

/**
 * A small button which looks like a search field and opens the media search.
 */
@Composable
fun MediaSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionController = LocalInteractionController.current
    val style = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val color = style.foreground()

    Row(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .height(MediaSearchButtonHeight)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable {
                interactionController.performFeedback(InteractionKind.KeyPress)
                onClick()
            }
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = color.copy(alpha = 0.7f),
        )
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = stringRes(R.string.emoji__search__action),
            color = color.copy(alpha = 0.7f),
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/**
 * A single line text field for use within the IME window. As the IME window can never be focused, a regular text
 * field would neither show its cursor nor its selection, so both are drawn here.
 */
@Composable
private fun MediaSearchField(
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
