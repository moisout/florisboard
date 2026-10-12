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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import dev.patrickgold.florisboard.ime.keyboard3.ImeActions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QuickActionMigrationTest : FunSpec({
    // Arrangement as written by versions before the keyboard3 rework
    val legacyArrangementJson = """
        {"stickyAction":{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-301,"label":"settings"}},
        "dynamicActions":[
        {"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-131,"label":"undo"}},
        {"$":"insert_key","data":{"$":"text_key","code":-132,"label":"redo"}},
        {"$":"insert_key","data":{"$":"text_key","type":"navigation","code":-23,"label":"arrow_up"}},
        {"$":"insert_key","data":{"$":"text_key","type":"character","code":97,"label":"a"}}],
        "hiddenActions":[
        {"$":"insert_key","data":{"$":"text_key","type":"function","code":-244,"label":"toggle_incognito_mode"}}]}
    """.trimIndent()

    test("legacy arrangement is decoded and migrated to k3 descriptors") {
        val arrangement = QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(legacyArrangementJson)
            .migrateToK3Descriptors()

        arrangement shouldBe QuickActionArrangement(
            stickyAction = QuickAction.InsertK3Descriptor(ImeActions.Settings),
            dynamicActions = listOf(
                QuickAction.InsertK3Descriptor(ImeActions.Undo),
                QuickAction.InsertK3Descriptor(ImeActions.Redo),
                QuickAction.InsertK3Descriptor(ImeActions.ArrowUp),
            ),
            hiddenActions = listOf(
                QuickAction.InsertK3Descriptor(ImeActions.TogglePersonalizedLearning),
            ),
        )
    }

    test("migrated arrangement survives an encode and decode round trip") {
        val arrangement = QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(legacyArrangementJson)
            .migrateToK3Descriptors()
        val json = QuickActionJsonConfig.encodeToString(arrangement)
        QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(json) shouldBe arrangement
    }
})
