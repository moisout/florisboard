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

package dev.patrickgold.florisboard.ime.keyboard3.extension

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.extension.ExtensionComponentName
import dev.patrickgold.florisboard.ime.extension.ExtensionIndex
import dev.patrickgold.florisboard.ime.io.FlorisRef
import dev.patrickgold.florisboard.lib.FlorisLocale

private const val FOUNDATION_EXTENSION_ID = "org.florisboard.k3.foundation"
private const val FALLBACK_KEYBOARD_ID = "qwerty"

fun extCoreKeyboard(id: String) = ExtensionComponentName(
    extensionId = FOUNDATION_EXTENSION_ID,
    componentId = id,
)

/**
 * A keyboard component which is known to exist in the extension index.
 */
data class ResolvedKeyboard(
    val name: ExtensionComponentName,
    val component: Keyboard3Extension.KeyboardComponent,
    val sourceRef: FlorisRef,
) {
    val fileRef: FlorisRef
        get() = sourceRef.subRef("keyboard").subRef("${component.id}.xml")
}

/**
 * Returns all keyboards of this index, sorted by their display name.
 */
fun ExtensionIndex.keyboards(): List<ResolvedKeyboard> {
    return extensions.values
        .filterIsInstance<Keyboard3Extension>()
        .flatMap { ext ->
            ext.manifest.keyboards.map { component ->
                ResolvedKeyboard(
                    name = ExtensionComponentName(ext.manifest.meta.id, component.id),
                    component = component,
                    sourceRef = ext.sourceRef,
                )
            }
        }
        .sortedBy { it.component.name.lowercase() }
}

/**
 * Returns the keyboard to use for given [subtype]. This is the keyboard set in the subtype if it exists, else the
 * keyboard which fits best to the primary locale of the subtype.
 */
fun ExtensionIndex.resolveKeyboardFor(subtype: Subtype): ResolvedKeyboard? {
    return keyboards().resolveKeyboardFor(subtype.keyboard, subtype.primaryLocale)
}

fun List<ResolvedKeyboard>.resolveKeyboardFor(
    keyboard: ExtensionComponentName?,
    locale: FlorisLocale,
): ResolvedKeyboard? {
    val name = selectKeyboardFor(associate { it.name to it.component }, keyboard, locale)
    return find { it.name == name }
}

/**
 * Selects the keyboard to use out of given [keyboards]. This is [keyboard] if it exists, else the keyboard which
 * fits best to given [locale], else the fallback keyboard.
 */
fun selectKeyboardFor(
    keyboards: Map<ExtensionComponentName, Keyboard3Extension.KeyboardComponent>,
    keyboard: ExtensionComponentName?,
    locale: FlorisLocale,
): ExtensionComponentName? {
    if (keyboard != null && keyboard in keyboards) {
        return keyboard
    }
    return keyboards.entries
        .map { (name, component) -> name to component.localeMatchScore(locale) }
        .filter { (_, score) -> score > 0 }
        .maxByOrNull { (_, score) -> score }?.first
        ?: extCoreKeyboard(FALLBACK_KEYBOARD_ID).takeIf { it in keyboards }
        ?: keyboards.keys.firstOrNull()
}

/**
 * Returns how well this keyboard fits to given [locale]: 2 if both language and country match, 1 if the keyboard
 * is declared for the whole language, else 0.
 */
private fun Keyboard3Extension.KeyboardComponent.localeMatchScore(locale: FlorisLocale): Int {
    return locales.maxOfOrNull { tag ->
        val keyboardLocale = FlorisLocale.fromTag(tag)
        when {
            keyboardLocale.language != locale.language -> 0
            keyboardLocale.country.isBlank() -> 1
            keyboardLocale.country == locale.country -> 2
            else -> 0
        }
    } ?: 0
}
