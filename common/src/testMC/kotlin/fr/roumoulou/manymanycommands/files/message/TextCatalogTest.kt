// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.message

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.MessageFamily
import fr.roumoulou.manymanycommands.rules.message.TextPattern
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les deux fichiers de langue du jar, complets, et le texte que le mod résout lui-même (cahier des charges, chapitre 2.6.5). */
class TextCatalogTest {

    /** Toutes les familles du mod : une famille oubliée ici laisse ses clés orphelines dans les fichiers, et le test qui les compte échoue. */
    private val families: List<MessageFamily> = listOf(CommonMessages, ModMessages)

    private val texts = TextCatalog.load()

    private val declaredKeys = families.flatMap { it.keys }.toSet()

    @Test
    fun `chaque fichier de langue porte toutes les clés déclarées, et aucune autre`() {
        Language.entries.forEach { language ->
            val fileKeys = texts.keys(language)
            assertEquals(emptySet<String>(), declaredKeys - fileKeys, "Keys missing from ${language.fileName}.json")
            assertEquals(emptySet<String>(), fileKeys - declaredKeys, "Keys of ${language.fileName}.json that no message family declares")
        }
    }

    @Test
    fun `un texte attend les mêmes arguments dans les deux langues`() {
        declaredKeys.forEach { key ->
            val french = TextPattern.argumentCount(texts.pattern(Language.FRENCH, key))
            val english = TextPattern.argumentCount(texts.pattern(Language.ENGLISH, key))
            assertEquals(french, english, "Key '$key' expects $french argument(s) in French and $english in English")
        }
    }

    @Test
    fun `aucun texte n'est vide`() {
        Language.entries.forEach { language ->
            declaredKeys.forEach { key -> assertTrue(texts.pattern(language, key).isNotBlank(), "Key '$key' is blank in ${language.fileName}.json") }
        }
    }

    @Test
    fun `le mod résout un texte dans la langue demandée, arguments en place`() {
        val applied = ModMessages.RELOAD_FILE_APPLIED.with("settings.json").text

        assertEquals("settings.json : appliqué.", texts.resolve(Language.FRENCH, applied))
        assertEquals("settings.json: applied.", texts.resolve(Language.ENGLISH, applied))
    }

    @Test
    fun `un texte imbriqué se résout à son tour, dans la même langue`() {
        assertEquals("3 minutes 20 secondes", texts.resolve(Language.FRENCH, DurationWording.text(200)))
        assertEquals("3 minutes 20 seconds", texts.resolve(Language.ENGLISH, DurationWording.text(200)))
        assertEquals("1 heure", texts.resolve(Language.FRENCH, DurationWording.text(3_600)))
        assertEquals("45 secondes", texts.resolve(Language.FRENCH, DurationWording.text(45)))
    }

    @Test
    fun `une clé que les fichiers ne portent pas rend la clé elle-même`() {
        assertEquals("manymanycommands.mod.absente", texts.pattern(Language.FRENCH, "manymanycommands.mod.absente"))
    }
}
