// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** L'état du mod ouvert sur un dossier à lui : le fichier invalide au démarrage et au rechargement (cahier des charges, chapitre 2.7.3). */
class ModRuntimeTest {

    companion object {

        private const val VERSION = "0.0.0+test"

        @JvmStatic
        @BeforeAll
        fun bootstrapTheGame() = GameBootstrap.ensure()
    }

    @TempDir
    lateinit var directory: Path

    private val settingsPath: Path get() = directory.resolve(GlobalSettings.FILE_NAME)

    private fun start(): ModRuntime = ModRuntime.start(directory, VERSION)

    @Test
    fun `l'ouverture crée le fichier de réglages et applique les réglages livrés`() {
        val runtime = start()

        assertTrue(settingsPath.exists())
        assertEquals(GlobalSettings(), runtime.settings)
        assertEquals(GlobalSettings(), runtime.startupSettings)
        assertFalse(runtime.hasShippedSettingsInPlace)
        assertEquals(emptyList<Any>(), runtime.invalidSettingsReport())
    }

    @Test
    fun `l'ouverture lit un fichier déjà là`() {
        settingsPath.writeText("""{ "language": "fr", "mmc-alias": false }""")

        val runtime = start()

        assertEquals(GlobalSettings(language = LanguageSetting.FRENCH, mmcAlias = false), runtime.startupSettings)
    }

    @Test
    fun `invalide au démarrage, le fichier laisse les réglages livrés en place et le journal le dit`() {
        settingsPath.writeText("{\n  \"language\": \"de\"\n}")

        val runtime = start()

        assertEquals(GlobalSettings(), runtime.settings)
        assertTrue(runtime.hasShippedSettingsInPlace)
        val report = listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key, ModMessages.STARTUP_INVALID_SETTINGS.key)
        assertEquals(report, runtime.invalidSettingsReport().map { it.key })
        assertEquals(ModMessages.STATUS_FILE_REFUSED.key, runtime.status().last().key)
    }

    @Test
    fun `corrigé puis rechargé, le fichier s'applique et les réglages livrés s'effacent`() {
        settingsPath.writeText("{\n  \"language\": \"de\"\n}")
        val runtime = start()

        settingsPath.writeText("{\n  \"language\": \"fr\"\n}")
        val answer = runtime.reload()

        assertEquals(listOf(ModMessages.RELOAD_FILE_APPLIED.key), answer.map { it.key })
        assertEquals(LanguageSetting.FRENCH, runtime.settings.language)
        assertFalse(runtime.hasShippedSettingsInPlace)
        assertEquals(ModMessages.STATUS_FILE_VALID.key, runtime.status().last().key)
    }

    @Test
    fun `invalide au rechargement, le fichier est refusé et les réglages en vigueur restent`() {
        settingsPath.writeText("""{ "language": "fr" }""")
        val runtime = start()

        settingsPath.writeText("{\n  \"language\": \"fr\",\n  \"langage\": \"en\"\n}")
        val answer = runtime.reload()

        assertEquals(listOf(ModMessages.RELOAD_FILE_REFUSED.with(GlobalSettings.FILE_NAME).key, ModMessages.RELOAD_FINDING.key), answer.map { it.key })
        assertEquals(LanguageSetting.FRENCH, runtime.settings.language)
        assertFalse(runtime.hasShippedSettingsInPlace, "The settings in force are those of the file read at startup, not the shipped ones")
        assertEquals(ModMessages.STATUS_FILE_REFUSED.key, runtime.status().last().key)
    }

    @Test
    fun `un fichier supprimé renaît au rechargement, avec les réglages livrés`() {
        settingsPath.writeText("""{ "language": "fr" }""")
        val runtime = start()

        settingsPath.deleteExisting()
        val answer = runtime.reload()

        assertTrue(settingsPath.exists())
        assertEquals(listOf(ModMessages.RELOAD_FILE_APPLIED.key), answer.map { it.key })
        assertEquals(GlobalSettings(), runtime.settings)
    }

    @Test
    fun `le rechargement dit le réglage qui attend le redémarrage, et les réglages du démarrage ne bougent pas`() {
        val runtime = start()

        settingsPath.writeText("""{ "permission-source": "external", "mmc-alias": false }""")
        val answer = runtime.reload()

        val expected = listOf(
            ModMessages.RELOAD_FILE_APPLIED.with(GlobalSettings.FILE_NAME),
            ModMessages.RELOAD_RESTART_REQUIRED.with("permission-source"),
            ModMessages.RELOAD_RESTART_REQUIRED.with("mmc-alias"),
        )
        assertEquals(expected, answer)
        assertEquals(GlobalSettings(), runtime.startupSettings)
        assertEquals(PermissionSourceSetting.EXTERNAL, runtime.settings.permissionSource)
        assertEquals(ModMessages.STATUS_SOURCE.with("internal"), runtime.status()[1], "The status shows the permission source of the startup, the one in effect")
    }

    @Test
    fun `tant que le réglage attend, chaque rechargement le redit`() {
        val runtime = start()
        settingsPath.writeText("""{ "mmc-alias": false }""")

        runtime.reload()

        assertEquals(listOf(ModMessages.RELOAD_FILE_APPLIED.key, ModMessages.RELOAD_RESTART_REQUIRED.key), runtime.reload().map { it.key })
    }

    @Test
    fun `la langue rechargée prend effet tout de suite`() {
        settingsPath.writeText("""{ "language": "fr" }""")
        val runtime = start()
        assertEquals(Language.FRENCH, runtime.messenger.consoleLanguage)

        settingsPath.writeText("""{ "language": "en" }""")
        runtime.reload()

        assertEquals(Language.ENGLISH, runtime.messenger.consoleLanguage)
    }

    @Test
    fun `l'état donne la version, la source des droits et chaque fichier`() {
        val expected = listOf(ModMessages.STATUS_VERSION.with(VERSION), ModMessages.STATUS_SOURCE.with("internal"), ModMessages.STATUS_FILE_VALID.with(GlobalSettings.FILE_NAME))

        assertEquals(expected, start().status())
    }

    @Test
    fun `le journal reçoit le refus dans la langue de la console`() {
        settingsPath.writeText("{\n  \"language\": \"de\"\n}")
        val runtime = start()

        val lines = runtime.invalidSettingsReport().map(runtime.messenger::forJournal)

        assertEquals(3, lines.size)
        assertTrue(lines.none { it.contains("manymanycommands.mod.") || it.contains("%s") }, "Every line must be a resolved text, was $lines")
        assertTrue(lines.first().startsWith(GlobalSettings.FILE_NAME), "The first line names the file, was '${lines.first()}'")
    }
}
