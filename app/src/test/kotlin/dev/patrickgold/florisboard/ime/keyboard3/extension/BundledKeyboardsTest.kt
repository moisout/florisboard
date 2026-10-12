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

import dev.patrickgold.florisboard.ime.extension.ExtensionComponentName
import dev.patrickgold.florisboard.ime.extension.ExtensionController
import dev.patrickgold.florisboard.ime.extension.ExtensionManifest
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.k3lp.lib.meta.source.SourceFileRef
import org.k3lp.lib.meta.source.TextSourceFile
import org.k3lp.lib.text.WillRequireMigrationToRichErrors
import org.k3lp.model.K3CompileResult
import org.k3lp.model.K3ImpliedImports
import org.k3lp.model.K3ImportResolver
import org.k3lp.model.compileToModel

@OptIn(WillRequireMigrationToRichErrors::class)
class BundledKeyboardsTest : FunSpec({
    val foundationDir = generateSequence(FsDir("").absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(it.subDir("src/main/assets/extensions"), it.subDir("app/src/main/assets/extensions")) }
        .first { it.isDirectory }
        .subDir("org.florisboard.k3.foundation")
    val manifest = foundationDir.subFile("extension.json")
        .readJson<ExtensionManifest>(ExtensionController.ExtensionJson) as Keyboard3Extension.Manifest
    val keyboards = manifest.keyboards.associateBy { ExtensionComponentName(manifest.meta.id, it.id) }

    fun sourceFileOf(relPath: String) = TextSourceFile(
        object : SourceFileRef {
            override fun toString() = relPath
        },
        foundationDir.subFile(relPath).readText(),
    )

    test("manifest declares exactly the keyboard files which exist") {
        val keyboardFiles = foundationDir.subDir("keyboard").listFiles().orEmpty().map { it.nameWithoutExtension }
        keyboardFiles.shouldNotBeEmpty()
        manifest.keyboards.map { it.id } shouldContainExactlyInAnyOrder keyboardFiles
    }

    for (keyboard in manifest.keyboards) {
        test("keyboard ${keyboard.id} compiles without reports") {
            val importResolver = K3ImportResolver { path, _ ->
                // Mirrors the resolver in Loader.kt, without depending on android.net.Uri
                sourceFileOf("import/" + path.substringAfter("://").substringAfter("/"))
            }
            val impliedImports = K3ImpliedImports(
                displays = listOf("flex://org.florisboard.k3.foundation/displays-implied.xml"),
                keys = listOf("flex://org.florisboard.k3.foundation/keys-implied.xml"),
            )
            val result = sourceFileOf("keyboard/${keyboard.id}.xml").compileToModel(importResolver, impliedImports)
            result.reports.map { it.toString() }.shouldBeEmpty()
            result.shouldBeInstanceOf<K3CompileResult.Success>()
        }
    }

    fun resolve(languageTag: String, keyboard: ExtensionComponentName? = null): String? {
        return selectKeyboardFor(keyboards, keyboard, FlorisLocale.fromTag(languageTag))?.componentId
    }

    test("keyboard is chosen by the most specific locale match") {
        resolve("de-CH") shouldBe "swiss_german"
        resolve("de-DE") shouldBe "qwertz"
        resolve("de-AT") shouldBe "qwertz"
        resolve("en-US") shouldBe "qwerty"
        resolve("en-GB") shouldBe "qwerty"
    }

    test("locales without a matching keyboard fall back to qwerty") {
        resolve("fr-FR") shouldBe "qwerty"
    }

    test("explicitly set keyboard wins over the locale, unknown keyboards are ignored") {
        resolve("de-CH", extCoreKeyboard("qwerty")) shouldBe "qwerty"
        resolve("de-CH", extCoreKeyboard("does_not_exist")) shouldBe "swiss_german"
    }
})
