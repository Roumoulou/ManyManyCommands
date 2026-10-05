// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Le remplissage d'un texte de fichier de langue, celui que le mod fait lui-même pour la console (cahier des charges, chapitre 2.6.5). */
class TextPatternTest {

    @Test
    fun `chaque emplacement prend l'argument suivant`() {
        assertEquals("settings.json, ligne 3 : clé inconnue", TextPattern.fill("%s, ligne %s : %s", listOf("settings.json", "3", "clé inconnue")))
    }

    @Test
    fun `un emplacement numéroté prend l'argument de son rang, dans l'ordre que la langue veut`() {
        assertEquals("b puis a", TextPattern.fill("%2\$s puis %1\$s", listOf("a", "b")))
    }

    @Test
    fun `un double pourcent écrit un pourcent et ne consomme aucun argument`() {
        assertEquals("100 % de base", TextPattern.fill("100 %% de %s", listOf("base")))
    }

    @Test
    fun `un emplacement sans argument reste tel quel`() {
        assertEquals("a et %s", TextPattern.fill("%s et %s", listOf("a")))
        assertEquals("%3\$s", TextPattern.fill("%3\$s", listOf("a")))
    }

    @Test
    fun `un texte sans emplacement sort inchangé`() {
        assertEquals("clé inconnue", TextPattern.fill("clé inconnue", listOf("ignoré")))
    }

    @Test
    fun `le nombre d'arguments attendus compte les emplacements, pas les pourcents écrits`() {
        assertEquals(0, TextPattern.argumentCount("clé inconnue"))
        assertEquals(3, TextPattern.argumentCount("%s, ligne %s : %s"))
        assertEquals(2, TextPattern.argumentCount("%2\$s puis %1\$s"))
        assertEquals(1, TextPattern.argumentCount("100 %% de %s"))
    }
}
