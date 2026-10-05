// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.mod

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les réponses de la commande, une par situation du tableau des messages (`manymanycommands.md` des specs, chapitre 5). */
class ModReportsTest {

    private val unknownKey = CommonMessages.FINDING_UNKNOWN_KEY.with()

    /** Un fichier déjà lu, dont la lecture a rendu [reading]. */
    private fun loaded(name: String, reading: FileReading<String>): SettingsFile<String> = SettingsFile(name, shipped = "livré") { reading }.also { it.load() }

    private fun refused(vararg findings: FileFinding): FileReading<String> = FileReading.Refused(findings.toList())

    @Test
    fun `l'état donne la version, la source des droits, puis une ligne par fichier`() {
        val files = listOf(loaded("settings.json", FileReading.Applied("lu")), loaded("groups.json", refused(FileFinding("default", 4, unknownKey))))

        val status = ModReports.status("0.1.0+26.3", PermissionSourceSetting.INTERNAL, files)

        val expected = listOf(
            ModMessages.STATUS_VERSION.with("0.1.0+26.3"),
            ModMessages.STATUS_SOURCE.with("internal"),
            ModMessages.STATUS_FILE_VALID.with("settings.json"),
            ModMessages.STATUS_FILE_REFUSED.with("groups.json"),
        )
        assertEquals(expected, status)
        assertEquals(setOf(MessageType.DISPLAY), status.map { it.type }.toSet())
    }

    @Test
    fun `le rechargement répond une ligne de succès par fichier appliqué`() {
        val reload = ModReports.reload(listOf(loaded("settings.json", FileReading.Applied("lu"))), awaitingRestart = emptyList())

        assertEquals(listOf(ModMessages.RELOAD_FILE_APPLIED.with("settings.json")), reload)
        assertEquals(MessageType.SUCCESS, reload.single().type)
    }

    @Test
    fun `un fichier refusé répond son nom, puis une ligne par constat, en refus`() {
        val file = loaded("settings.json", refused(FileFinding("language", 3, unknownKey), FileFinding("mmc-alias", 5, unknownKey)))

        val reload = ModReports.reload(listOf(file), awaitingRestart = emptyList())

        val expected = listOf(
            ModMessages.RELOAD_FILE_REFUSED.with("settings.json"),
            ModMessages.RELOAD_FINDING.with("language", 3, unknownKey),
            ModMessages.RELOAD_FINDING.with("mmc-alias", 5, unknownKey),
        )
        assertEquals(expected, reload)
        assertEquals(setOf(MessageType.REFUSAL), reload.map { it.type }.toSet())
    }

    @Test
    fun `un constat ne donne de son chemin et de sa ligne que ce qui est connu`() {
        val file = loaded("settings.json", refused(FileFinding("language", null, unknownKey), FileFinding(null, 7, unknownKey), FileFinding(null, null, unknownKey)))

        val expected = listOf(
            ModMessages.RELOAD_FILE_REFUSED.with("settings.json"),
            ModMessages.RELOAD_FINDING_WITHOUT_LINE.with("language", unknownKey),
            ModMessages.RELOAD_FINDING_WITHOUT_PATH.with(7, unknownKey),
            ModMessages.RELOAD_FINDING_ALONE.with(unknownKey),
        )
        assertEquals(expected, ModReports.refusal(file))
    }

    @Test
    fun `un fichier refusé n'empêche pas les autres de s'appliquer`() {
        val files = listOf(loaded("settings.json", refused(FileFinding("language", 3, unknownKey))), loaded("groups.json", FileReading.Applied("lu")))

        val keys = ModReports.reload(files, awaitingRestart = emptyList()).map { it.key }

        assertEquals(listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key, ModMessages.RELOAD_FILE_APPLIED.key), keys)
    }

    @Test
    fun `le rechargement dit chaque réglage qui attend le redémarrage, après les fichiers`() {
        val reload = ModReports.reload(listOf(loaded("settings.json", FileReading.Applied("lu"))), awaitingRestart = listOf("permission-source", "mmc-alias"))

        val expected = listOf(
            ModMessages.RELOAD_FILE_APPLIED.with("settings.json"),
            ModMessages.RELOAD_RESTART_REQUIRED.with("permission-source"),
            ModMessages.RELOAD_RESTART_REQUIRED.with("mmc-alias"),
        )
        assertEquals(expected, reload)
        assertEquals(MessageType.DISPLAY, reload.last().type)
    }

    @Test
    fun `un fichier valide n'a pas de refus à dire`() {
        assertEquals(emptyList<Any>(), ModReports.refusal(loaded("settings.json", FileReading.Applied("lu"))))
    }
}
