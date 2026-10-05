// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Le chemin d'une valeur dans un fichier, tel qu'un constat le donne (cahier des charges, chapitre 2.7.3). */
class FindingPathTest {

    @Test
    fun `une clé de la racine s'écrit seule, une clé imbriquée après un point`() {
        assertEquals("enabled", FindingPath.property("", "enabled"))
        assertEquals("quota.uses", FindingPath.property("quota", "uses"))
    }

    @Test
    fun `une clé qui porte un point ou un crochet s'écrit entre crochets et guillemets`() {
        assertEquals("rules[\"a.b\"]", FindingPath.property("rules", "a.b"))
        assertEquals("[\"a[0]\"]", FindingPath.property("", "a[0]"))
    }

    @Test
    fun `l'entrée d'une carte s'écrit entre crochets, et un index aussi`() {
        assertEquals("rules[vip]", FindingPath.entry("rules", "vip"))
        assertEquals("rules[vip].forbidden-dimensions[2]", FindingPath.index(FindingPath.property(FindingPath.entry("rules", "vip"), "forbidden-dimensions"), 2))
    }

    @Test
    fun `une entrée vide ou faite de chiffres prend des guillemets, pour ne pas se lire comme un index`() {
        assertEquals("rules[\"123\"]", FindingPath.entry("rules", "123"))
        assertEquals("rules[\"\"]", FindingPath.entry("rules", ""))
        assertEquals("rules[vip2]", FindingPath.entry("rules", "vip2"))
    }
}
