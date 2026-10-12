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

import dev.patrickgold.florisboard.ime.keyboard3.extension.extCoreKeyboard
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SubtypeTest : FunSpec({
    // Subtype as written by versions before the keyboard3 rework
    fun legacySubtypeJson(id: Long, locale: String, characters: String) = """
        {"id":$id,"primaryLocale":"$locale","secondaryLocales":[],
        "nlpProviders":{"spelling":"org.florisboard.nlp.providers.latin","suggestion":"org.florisboard.nlp.providers.latin"},
        "composer":"org.florisboard.composers:appender","currencySet":"org.florisboard.currencysets:dollar",
        "punctuationRule":"org.florisboard.localization:default","popupMapping":"org.florisboard.localization:de",
        "layoutMap":{"characters":"$characters","symbols":"org.florisboard.layouts:western",
        "symbols2":"org.florisboard.layouts:western","numeric":"org.florisboard.layouts:western_arabic"}}
    """.trimIndent()

    fun migrate(listRaw: String): List<Subtype> {
        return SubtypeJsonConfig.decodeFromString(Subtype.migrateLegacySubtypeList(listRaw))
    }

    test("legacy characters layout is migrated to the matching keyboard") {
        val subtypes = migrate(
            "[" + legacySubtypeJson(1702891067800, "de-CH", "org.florisboard.layouts:swiss_german") + "," +
                legacySubtypeJson(2, "de-DE", "org.florisboard.layouts:german") + "]"
        )
        subtypes.map { it.id } shouldBe listOf(1702891067800, 2)
        subtypes[0].primaryLocale shouldBe FlorisLocale.from("de", "CH")
        subtypes[0].keyboard shouldBe extCoreKeyboard("swiss_german")
        subtypes[1].keyboard shouldBe extCoreKeyboard("qwertz")
    }

    test("legacy layouts without keyboard3 counterpart leave the keyboard unset") {
        val subtypes = migrate(
            "[" + legacySubtypeJson(1, "fr-FR", "org.florisboard.layouts:azerty") + "," +
                legacySubtypeJson(2, "de-CH", "com.example.layouts:swiss_german") + "]"
        )
        subtypes.map { it.keyboard } shouldBe listOf(null, null)
    }

    test("migration keeps already migrated subtype lists as they are") {
        val list = listOf(
            Subtype(1, FlorisLocale.from("de", "CH"), emptyList(), keyboard = extCoreKeyboard("qwerty")),
            Subtype(2, FlorisLocale.from("en", "US"), emptyList()),
        )
        migrate(SubtypeJsonConfig.encodeToString(list)) shouldBe list
        migrate("[]") shouldBe emptyList()
    }
})
