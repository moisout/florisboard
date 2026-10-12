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

package dev.patrickgold.florisboard.ime.core

import dev.patrickgold.florisboard.ime.extension.ExtensionComponentName
import dev.patrickgold.florisboard.ime.keyboard3.extension.extCoreKeyboard
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Data class which represents an user-specified set of language and layout. String representations
 * of this object are stored as an Json array in the preference datastore.
 *
 * @property id The ID of this subtype.
 * @property primaryLocale The primary locale of this subtype.
 * @property secondaryLocales The secondary locales of this subtype. May be an empty list.
 * @property nlpProviders The NLP provider map to instantiate the correct provider for each category.
 * @property keyboard The keyboard of this subtype. If null the keyboard is chosen based on the primary locale.
 */
@Serializable
data class Subtype(
    val id: Long,
    val primaryLocale: FlorisLocale,
    val secondaryLocales: List<FlorisLocale>,
    val nlpProviders: SubtypeNlpProviderMap = SubtypeNlpProviderMap(),
    val keyboard: ExtensionComponentName? = null,
) {
    companion object {
        /**
         * Subtype to use when prefs do not contain any valid subtypes.
         */
        val DEFAULT = Subtype(
            id = -1,
            primaryLocale = FlorisLocale.from("en", "US"),
            secondaryLocales = emptyList(),
            nlpProviders = SubtypeNlpProviderMap(),
        )

        private const val LEGACY_LAYOUTS_EXTENSION_ID = "org.florisboard.layouts"

        /**
         * Maps the character layouts of the legacy layouts extension to their keyboard3 counterparts.
         */
        private val LegacyCharacterLayoutToKeyboard = mapOf(
            "german" to extCoreKeyboard("qwertz"),
            "qwerty" to extCoreKeyboard("qwerty"),
            "qwertz" to extCoreKeyboard("qwertz"),
            "swiss_german" to extCoreKeyboard("swiss_german"),
        )

        /**
         * Migrates a raw subtype list created before the keyboard3 rework, where each subtype had a layout map
         * pointing to the legacy JSON layouts. The characters layout is translated to the matching keyboard, if
         * there is one, else the keyboard is left unset so it gets chosen based on the locale of the subtype.
         */
        fun migrateLegacySubtypeList(listRaw: String): String {
            val list = SubtypeJsonConfig.parseToJsonElement(listRaw).jsonArray
            val newList = list.map { element ->
                val subtype = element.jsonObject
                val legacyLayout = subtype["layoutMap"]?.jsonObject?.get("characters")?.jsonPrimitive?.contentOrNull
                    ?: return@map subtype
                val keyboard = runCatching { ExtensionComponentName.from(legacyLayout) }.getOrNull()
                    ?.takeIf { it.extensionId == LEGACY_LAYOUTS_EXTENSION_ID }
                    ?.let { LegacyCharacterLayoutToKeyboard[it.componentId] }
                JsonObject(buildMap {
                    putAll(subtype.filterKeys { it != "layoutMap" })
                    if (keyboard != null && subtype["keyboard"] == null) {
                        put("keyboard", JsonPrimitive(keyboard.toString()))
                    }
                })
            }
            return SubtypeJsonConfig.encodeToString(JsonArray(newList))
        }
    }

    /**
     * Returns an accumulated list of all locales of this subtype.
     */
    fun locales(): List<FlorisLocale> {
        val locales = mutableListOf(primaryLocale)
        locales.addAll(secondaryLocales)
        return locales
    }

    /**
     * Converts this object into its short string representation, used for debugging. Format:
     *  <id>/<language_tag>/<currency_set_name>
     */
    fun toShortString(): String {
        val languageTag = primaryLocale.languageTag()
        return "$id/$languageTag"
    }

    fun equalsExcludingId(other: Subtype): Boolean {
        if (other.primaryLocale != primaryLocale) return false
        if (other.secondaryLocales != secondaryLocales) return false
        if (other.nlpProviders != nlpProviders) return false
        if (other.keyboard != keyboard) return false

        return true
    }
}

@Serializable
data class SubtypeNlpProviderMap(
    val spelling: String = LatinLanguageProvider.ProviderId,
    val suggestion: String = LatinLanguageProvider.ProviderId,
) {
    inline fun forEach(action: (String, String) -> Unit) {
        action("spelling", spelling)
        action("suggestion", suggestion)
    }
}
