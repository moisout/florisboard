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

package dev.patrickgold.florisboard.ime.extension

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

class BundledExtensionsTest : FunSpec({
    val bundledExtensionsDir = generateSequence(FsDir("").absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(it.subDir("src/main/assets/extensions"), it.subDir("app/src/main/assets/extensions")) }
        .first { it.isDirectory }
    val bundledExtensionDirs = bundledExtensionsDir.listFiles().orEmpty().filter { it.isDirectory }

    test("bundled extensions are found") {
        bundledExtensionDirs.shouldNotBeEmpty()
    }

    // A single invalid bundled manifest prevents the extension index from loading any user-installed extension
    for (extDir in bundledExtensionDirs) {
        test("manifest of bundled extension ${extDir.name} is valid") {
            val manifest = extDir.subFile(ExtensionDefaults.MANIFEST_FILE_NAME)
                .readJson<ExtensionManifest>(ExtensionController.ExtensionJson)
            manifest.meta.id shouldBe extDir.name
        }
    }
})
