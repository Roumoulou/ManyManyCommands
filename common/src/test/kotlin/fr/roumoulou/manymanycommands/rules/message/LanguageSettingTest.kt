// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** La langue d'un destinataire (cahier des charges, chapitre 2.6.1). */
class LanguageSettingTest {

    @Test
    fun `auto donne le français au joueur dont le client est en français, quel que soit le pays`() {
        assertEquals(Language.FRENCH, LanguageSetting.AUTO.forPlayer("fr_fr"))
        assertEquals(Language.FRENCH, LanguageSetting.AUTO.forPlayer("fr_ca"))
        assertEquals(Language.FRENCH, LanguageSetting.AUTO.forPlayer("FR_FR"))
    }

    @Test
    fun `auto donne l'anglais au joueur de toute autre langue`() {
        assertEquals(Language.ENGLISH, LanguageSetting.AUTO.forPlayer("en_us"))
        assertEquals(Language.ENGLISH, LanguageSetting.AUTO.forPlayer("de_de"))
        assertEquals(Language.ENGLISH, LanguageSetting.AUTO.forPlayer("fra_de"))
        assertEquals(Language.ENGLISH, LanguageSetting.AUTO.forPlayer(""))
    }

    @Test
    fun `auto donne à la console le français si la machine est en français, l'anglais sinon`() {
        assertEquals(Language.FRENCH, LanguageSetting.AUTO.forConsole("fr"))
        assertEquals(Language.ENGLISH, LanguageSetting.AUTO.forConsole("en"))
        assertEquals(Language.ENGLISH, LanguageSetting.AUTO.forConsole("de"))
    }

    @Test
    fun `fr et en imposent leur langue à tous`() {
        assertEquals(Language.FRENCH, LanguageSetting.FRENCH.forPlayer("en_us"))
        assertEquals(Language.FRENCH, LanguageSetting.FRENCH.forConsole("en"))
        assertEquals(Language.ENGLISH, LanguageSetting.ENGLISH.forPlayer("fr_fr"))
        assertEquals(Language.ENGLISH, LanguageSetting.ENGLISH.forConsole("fr"))
    }

    @Test
    fun `le réglage se lit à sa valeur exacte, sans conversion de casse`() {
        assertEquals(LanguageSetting.AUTO, LanguageSetting.of("auto"))
        assertEquals(LanguageSetting.FRENCH, LanguageSetting.of("fr"))
        assertEquals(LanguageSetting.ENGLISH, LanguageSetting.of("en"))
        assertNull(LanguageSetting.of("FR"))
        assertNull(LanguageSetting.of("de"))
    }
}
