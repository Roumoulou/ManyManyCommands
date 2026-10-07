// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le nom d'un home ou d'un warp, celui, en minuscules, d'un jeu de règles, d'un groupe ou d'une clé d'accès (cahier des charges, chapitre 2.3), et l'ordre d'une liste de noms. */
class NameRulesTest {

    @Test
    fun `un nom compte de 1 à 32 caractères, lettres des deux casses, chiffres, tiret bas et trait d'union`() {
        assertTrue(NameRules.isName("Base"))
        assertTrue(NameRules.isName("a".repeat(32)))
        assertTrue(NameRules.isName("Mine_2-Gold"))
        assertTrue(NameRules.isName("0"))
        assertFalse(NameRules.isName(""))
        assertFalse(NameRules.isName("a".repeat(33)))
    }

    @Test
    fun `un nom refuse les accents, les espaces et les autres signes`() {
        listOf("été", "ma maison", "base.2", "base/2", "base:2", " base", "base\n").forEach { name ->
            assertFalse(NameRules.isName(name), "'$name' must be refused")
        }
    }

    @Test
    fun `un nom en minuscules compte de 1 à 32 caractères`() {
        assertTrue(NameRules.isLowercaseName("a"))
        assertTrue(NameRules.isLowercaseName("a".repeat(32)))
        assertFalse(NameRules.isLowercaseName(""))
        assertFalse(NameRules.isLowercaseName("a".repeat(33)))
    }

    @Test
    fun `il admet les lettres minuscules, les chiffres, le tiret bas et le trait d'union`() {
        assertTrue(NameRules.isLowercaseName("default"))
        assertTrue(NameRules.isLowercaseName("vip_2-gold"))
        assertTrue(NameRules.isLowercaseName("0"))
    }

    @Test
    fun `il refuse les majuscules, les accents, les espaces et les autres signes`() {
        listOf("Vip", "VIP", "invité", "v i p", "vip.gold", "vip/gold", "vip:gold", " vip").forEach { name ->
            assertFalse(NameRules.isLowercaseName(name), "'$name' must be refused")
        }
    }

    @Test
    fun `une liste de noms se trie sans tenir compte de la casse, la majuscule d'abord à égalité`() {
        assertEquals(listOf("atelier", "Base", "base", "Zoo"), listOf("base", "Zoo", "Base", "atelier").sortedWith(NameRules.LISTING_ORDER))
    }
}
