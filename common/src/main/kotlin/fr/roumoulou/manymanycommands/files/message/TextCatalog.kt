// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.message

import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.TextPattern
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Les textes du mod, lus dans ses propres fichiers de langue (cahier des charges, chapitre 2.6.5) : le repli qu'un joueur reçoit dans sa langue,
 * et le texte que le mod résout lui-même pour la console.
 */
class TextCatalog private constructor(private val patterns: Map<Language, Map<String, String>>) {

    companion object {

        /** Lit les deux fichiers de langue du jar, au format de ceux du jeu. */
        fun load(): TextCatalog = TextCatalog(Language.entries.associateWith(::read))

        private fun read(language: Language): Map<String, String> {
            val resource = "assets/$MOD_ID/lang/${language.fileName}.json"
            val stream = TextCatalog::class.java.classLoader.getResourceAsStream(resource) ?: throw IllegalStateException("Language file missing from the mod: $resource")
            val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
            return Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), text)
        }
    }

    fun keys(language: Language): Set<String> = patterns.getValue(language).keys

    /** Le texte de [key] dans [language], avec ses emplacements. Une clé que le fichier ne porte pas rend la clé elle-même, comme le fait le jeu. */
    fun pattern(language: Language, key: String): String = patterns.getValue(language)[key] ?: key

    /** Le texte entier de [text] dans [language], ses arguments en place et ses textes imbriqués résolus. */
    fun resolve(language: Language, text: TranslatableText): String =
        TextPattern.fill(pattern(language, text.key), text.arguments.map { if (it is TranslatableText) resolve(language, it) else it.toString() })
}
