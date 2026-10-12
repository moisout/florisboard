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

package dev.patrickgold.florisboard.app.settings.advanced

import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeJsonConfig
import dev.patrickgold.florisboard.ime.extension.ExtensionController
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.florisboard.ime.extension.ExtensionManifest
import dev.patrickgold.florisboard.ime.io.ZipUtils
import dev.patrickgold.florisboard.ime.keyboard3.extension.Keyboard3Extension
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.snygg.SnyggMissingSchemaException
import org.florisboard.lib.snygg.SnyggStylesheet

class RestoreTest : FunSpec({
    fun themeManifest(id: String) = """
        {
          "$": "ime.extension.theme",
          "meta": {
            "id": "$id",
            "version": "1.0.0",
            "title": "Test themes",
            "maintainers": [ "someone" ],
            "license": "MIT"
          },
          "themes": [
            { "id": "test_night", "label": "Test Night", "authors": [ "someone" ], "isNight": true }
          ]
        }
    """.trimIndent()

    fun FsDir.writeThemeExtension(id: String) {
        mkdirs()
        subFile("extension.json").writeText(themeManifest(id))
        subDir("stylesheets").mkdirs()
        subFile("stylesheets/test_night.json").writeText("{}")
    }

    fun FsDir.installedExtensionIds() = listFiles().orEmpty().map { it.name }

    test("legacy theme flex archives are unpacked into the extensions directory") {
        val root = tempdir()
        val legacyThemeDir = root.subDir("backup/files/ime/theme").also { it.mkdirs() }
        val extensionsDir = root.subDir("extensions").also { it.mkdirs() }
        val unpacked = root.subDir("unpacked/com.example.themes").also { it.writeThemeExtension("com.example.themes") }
        ZipUtils.zip(unpacked, legacyThemeDir.subFile("com.example.themes.flex"))

        Restore.restoreLegacyThemeExtensions(legacyThemeDir, extensionsDir, root.subDir("tmp"))

        extensionsDir.installedExtensionIds() shouldContainExactlyInAnyOrder listOf("com.example.themes")
        val extDir = extensionsDir.subDir("com.example.themes")
        extDir.subFile("extension.json").readJson<ExtensionManifest>(ExtensionController.ExtensionJson)
            .shouldBeInstanceOf<ThemeExtension.Manifest>()
        extDir.subFile("stylesheets/test_night.json").exists() shouldBe true
    }

    test("extension id escaping the extensions directory is rejected") {
        val root = tempdir()
        val legacyThemeDir = root.subDir("backup/files/ime/theme").also { it.mkdirs() }
        val extensionsDir = root.subDir("extensions").also { it.mkdirs() }
        legacyThemeDir.subDir("evil").writeThemeExtension("../evil")

        shouldThrow<IllegalArgumentException> {
            Restore.restoreLegacyThemeExtensions(legacyThemeDir, extensionsDir, root.subDir("tmp"))
        }
        extensionsDir.installedExtensionIds().shouldBeEmpty()
        root.subDir("evil").exists() shouldBe false
    }

    test("only extensions of the selected types are restored and deleted") {
        val root = tempdir()
        val backupExtensionsDir = root.subDir("backup/files/extensions")
        val extensionsDir = root.subDir("extensions").also { it.mkdirs() }
        backupExtensionsDir.subDir("com.example.themes").writeThemeExtension("com.example.themes")

        Restore.restoreExtensions(backupExtensionsDir, extensionsDir, setOf(Keyboard3Extension.SERIAL_TYPE))
        extensionsDir.installedExtensionIds().shouldBeEmpty()

        Restore.restoreExtensions(backupExtensionsDir, extensionsDir, setOf(ThemeExtension.SERIAL_TYPE))
        extensionsDir.installedExtensionIds() shouldContainExactlyInAnyOrder listOf("com.example.themes")

        Restore.deleteExtensions(extensionsDir, setOf(Keyboard3Extension.SERIAL_TYPE))
        extensionsDir.installedExtensionIds() shouldContainExactlyInAnyOrder listOf("com.example.themes")

        Restore.deleteExtensions(extensionsDir, setOf(ThemeExtension.SERIAL_TYPE))
        extensionsDir.installedExtensionIds().shouldBeEmpty()
    }

    // Verifies the migration against real-world backups, if any are placed in the gitignored research-data folder
    val researchBackups = generateSequence(FsDir("").absoluteFile) { it.parentFile }
        .map { it.subDir("research-data") }
        .firstOrNull { it.isDirectory }
        ?.listFiles { file -> file.extension == "zip" }
        .orEmpty()
    for (backupZip in researchBackups) {
        test("preferences of research backup ${backupZip.name} are imported") {
            val backupDir = tempdir().subDir("backup")
            ZipUtils.unzip(backupZip, backupDir)
            val prefsFile = backupDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
            val datastore = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val prefs by datastore

            datastore.import(ImportStrategy.Erase, FileBasedStorage(prefsFile.path)).getOrThrow()

            // The following must hold for any backup: everything can be decoded by the current version
            val subtypes = SubtypeJsonConfig.decodeFromString<List<Subtype>>(prefs.localization.subtypes.get())
            println("subtypes -> ${subtypes.map { "${it.primaryLocale.languageTag()}/${it.keyboard}" }}")
            val arrangement = prefs.smartbar.actionArrangement.get()
            arrangement.forEach { it.shouldBeInstanceOf<QuickAction.InsertK3Descriptor>() }
            println("quick actions -> ${arrangement.dynamicActions.size} dynamic, sticky=${arrangement.stickyAction}")
            println("themes -> day=${prefs.theme.dayThemeId.get()} night=${prefs.theme.nightThemeId.get()}")
            println("fn key -> enabled=${prefs.keyboard.fnKeyEnabled.get()} ${prefs.keyboard.fnKeyArrangement.get()}")
        }

        test("themes of research backup ${backupZip.name} are migrated and loadable") {
            val root = tempdir()
            val backupDir = root.subDir("backup")
            val extensionsDir = root.subDir("extensions").also { it.mkdirs() }
            ZipUtils.unzip(backupZip, backupDir)

            Restore.restoreLegacyThemeExtensions(
                legacyThemeDir = backupDir.subDir("files").subDir(Restore.LEGACY_IME_THEME_PATH),
                extensionsDir = extensionsDir,
                tempDir = root.subDir("tmp"),
            )

            val failures = mutableListOf<String>()
            for (extDir in extensionsDir.listFiles().orEmpty()) {
                val manifest = extDir.subFile("extension.json")
                    .readJson<ExtensionManifest>(ExtensionController.ExtensionJson)
                manifest.shouldBeInstanceOf<ThemeExtension.Manifest>()
                manifest.meta.id shouldBe extDir.name
                for (theme in manifest.themes) {
                    val stylesheetFile = extDir.subFile(theme.stylesheetPath())
                    val result = runCatching { SnyggStylesheet.fromJson(stylesheetFile.readText()).getOrThrow() }
                    // Stylesheets without schema reference are Snygg v1 and were already unsupported before the rework
                    val error = result.exceptionOrNull()?.takeUnless { it is SnyggMissingSchemaException }
                    if (error != null) {
                        failures += "${manifest.meta.id}:${theme.id}: ${error.message?.take(300)}"
                    }
                }
            }
            failures.shouldBeEmpty()
        }
    }
})
