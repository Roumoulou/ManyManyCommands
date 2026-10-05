// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.settings

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les réglages globaux, leurs défauts et leurs valeurs admises (`manymanycommands.md` des specs, chapitres 2 et 4). */
class GlobalSettingsTest {

    @Test
    fun `les valeurs livrées sont celles du tableau des réglages`() {
        val shipped = GlobalSettings()

        assertEquals(LanguageSetting.AUTO, shipped.language)
        assertEquals(PermissionSourceSetting.INTERNAL, shipped.permissionSource)
        assertTrue(shipped.mmcAlias)
    }

    @Test
    fun `les valeurs livrées sont des valeurs admises`() {
        val shipped = GlobalSettings()

        assertEquals(emptyList<Any>(), GlobalSettingsRules.findings(GlobalSettings.SCHEMA_VERSION, shipped.language.value, shipped.permissionSource.value))
    }

    @Test
    fun `une langue hors des valeurs admises est refusée, à son chemin, avec les valeurs admises`() {
        val finding = GlobalSettingsRules.findings(1, "de", "internal").single()

        assertEquals("language", finding.path)
        assertEquals(CommonMessages.FINDING_NOT_ALLOWED.with("de", "auto, fr, en"), finding.text)
    }

    @Test
    fun `une source des droits hors des valeurs admises est refusée, à son chemin`() {
        val finding = GlobalSettingsRules.findings(1, "auto", "luckperms").single()

        assertEquals("permission-source", finding.path)
        assertEquals(CommonMessages.FINDING_NOT_ALLOWED.with("luckperms", "internal, external"), finding.text)
    }

    @Test
    fun `une valeur admise ne l'est qu'à sa casse exacte`() {
        assertEquals(listOf("language", "permission-source"), GlobalSettingsRules.findings(1, "Auto", "INTERNAL").map { it.path })
        assertNull(PermissionSourceSetting.of("Internal"))
    }

    @Test
    fun `un fichier plus récent que le mod est invalide`() {
        val finding = GlobalSettingsRules.findings(GlobalSettings.SCHEMA_VERSION + 1, "auto", "internal").single()

        assertEquals("schema-version", finding.path)
        assertEquals(CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(GlobalSettings.SCHEMA_VERSION + 1, GlobalSettings.SCHEMA_VERSION), finding.text)
    }

    @Test
    fun `chaque valeur fautive a son constat`() {
        assertEquals(listOf("schema-version", "language", "permission-source"), GlobalSettingsRules.findings(99, "de", "luckperms").map { it.path })
    }

    @Test
    fun `la source des droits et l'alias attendent le redémarrage quand ils ne valent plus ce qu'ils valaient au démarrage`() {
        val startup = GlobalSettings()

        assertEquals(emptyList<String>(), startup.awaitingRestart(startup))
        assertEquals(listOf("permission-source"), startup.copy(permissionSource = PermissionSourceSetting.EXTERNAL).awaitingRestart(startup))
        assertEquals(listOf("mmc-alias"), startup.copy(mmcAlias = false).awaitingRestart(startup))
        assertEquals(listOf("permission-source", "mmc-alias"), GlobalSettings(permissionSource = PermissionSourceSetting.EXTERNAL, mmcAlias = false).awaitingRestart(startup))
    }

    @Test
    fun `la langue prend effet au rechargement et n'attend rien`() {
        val startup = GlobalSettings()

        assertEquals(emptyList<String>(), startup.copy(language = LanguageSetting.FRENCH).awaitingRestart(startup))
    }
}
