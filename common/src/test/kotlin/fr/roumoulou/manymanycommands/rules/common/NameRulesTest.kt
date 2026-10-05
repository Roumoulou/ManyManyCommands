// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le nom d'un jeu de règles, d'un groupe, la clé d'accès d'un warp (cahier des charges, chapitre 2.3). */
class NameRulesTest {

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
}
