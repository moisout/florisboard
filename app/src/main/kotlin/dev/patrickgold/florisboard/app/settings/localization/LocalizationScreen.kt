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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.components.FlorisScreenNg
import dev.patrickgold.florisboard.app.components.ScrollableScreenColumn
import dev.patrickgold.florisboard.app.components.SegmentedListColumn
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.extension.LocalExtensionController
import dev.patrickgold.florisboard.ime.keyboard3.extension.resolveKeyboardFor
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.serialization.json.Json
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

internal val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

@Composable
fun LocalizationScreen() {
    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val subtypeManager by context.subtypeManager()
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }

    FlorisScreenNg(
        title = stringRes(R.string.settings__localization__title),
        previewFieldVisible = true,
        fab = {
            ExtendedFloatingActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = stringRes(R.string.settings__localization__subtype_add_title),
                    )
                },
                shape = FloatingActionButtonDefaults.extendedFabShape,
                onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
            )
        },
    ) { contentPadding ->
        ScrollableScreenColumn(contentPadding) {
            SegmentedListColumn {
                val count = 3
                ListPreference(
                    prefs.localization.displayLanguageNamesIn,
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    title = stringRes(R.string.settings__localization__display_language_names_in__label),
                    entries = enumDisplayEntriesOf(DisplayLanguageNamesIn::class),
                )
                SwitchPreference(
                    prefs.localization.displayKeyboardLabelsInSubtypeLanguage,
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    title = stringRes(R.string.settings__localization__display_keyboard_labels_in_subtype_language),
                )
                Preference(
                    onClick = {
                        navController.navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
                    },
                    shapes = ListItemDefaults.segmentedShapes(2, count),
                    title = stringRes(R.string.settings__localization__language_pack_title),
                    summary = stringRes(R.string.settings__localization__language_pack_summary),
                )
            }

            SegmentedListColumn(title = stringRes(R.string.settings__localization__group_subtypes__label)) {
                val subtypes by subtypeManager.subtypesFlow.collectAsState()
                if (subtypes.isEmpty()) {
                    FlorisWarningCard(
                        text = stringRes(R.string.settings__localization__subtype_no_subtypes_configured_warning),
                    )
                } else {
                    val extensionIndex by LocalExtensionController.current.activeIndex.collectAsState()
                    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
                    subtypes.forEachIndexed { index, subtype ->
                        val keyboardName = extensionIndex.resolveKeyboardFor(subtype)?.component?.name
                        SegmentedListItem(
                            onClick = { navController.navigate(Routes.Settings.SubtypeEdit(subtype.id)) },
                            onLongClick = { chosenSubtypeToDelete = subtype },
                            shapes = ListItemDefaults.segmentedShapes(index, subtypes.size),
                            content = {
                                Text(
                                    when (displayLanguageNamesIn) {
                                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayName()
                                        DisplayLanguageNamesIn.NATIVE_LOCALE -> {
                                            subtype.primaryLocale.displayName(subtype.primaryLocale)
                                        }
                                    }
                                )
                            },
                            supportingContent = keyboardName?.let { { Text(it) } },
                        )
                    }
                }
            }
        }
    }

    DeleteSubtypeConfirmationDialog(
        subtypeToDelete = chosenSubtypeToDelete,
        onDismiss = {
            chosenSubtypeToDelete = null
        },
        onConfirm = {
            chosenSubtypeToDelete?.let { subtypeManager.removeSubtype(subtypeToRemove = it) }
            chosenSubtypeToDelete = null
        }
    )
}

@Composable
fun DeleteSubtypeConfirmationDialog(
    subtypeToDelete: Subtype?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
)   {
    subtypeToDelete?.let {
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__localization__subtype_delete_confirmation_title),
            confirmLabel = stringRes(R.string.action__yes),
            dismissLabel = stringRes(R.string.action__no),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            ) {
                Text(stringRes(R.string.settings__localization__subtype_delete_confirmation_warning))
            }
    }
}
