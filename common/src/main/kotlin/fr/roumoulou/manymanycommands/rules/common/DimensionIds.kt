// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

/** L'identifiant d'une dimension, tel qu'un fichier l'écrit (cahier des charges, chapitre 2.7.3) : `minecraft:the_nether`. */
object DimensionIds {

    private const val GAME_NAMESPACE = "minecraft"

    /**
     * L'identifiant complet de [written], `null` quand ce n'en est pas un. Sans espace de noms, il prend celui du jeu, comme dans ses commandes :
     * `the_nether` vaut `minecraft:the_nether`. La dimension que le serveur ne porte pas est admise : un datapack peut l'apporter.
     */
    fun normalize(written: String): String? {
        val namespace = written.substringBefore(':', GAME_NAMESPACE)
        val path = written.substringAfter(':')
        val isIdentifier = namespace.isNotEmpty() && namespace.all(::isNamespaceCharacter) && path.isNotEmpty() && path.all { isNamespaceCharacter(it) || it == '/' }
        return if (isIdentifier) "$namespace:$path" else null
    }

    private fun isNamespaceCharacter(character: Char): Boolean = character in 'a'..'z' || character in '0'..'9' || character == '_' || character == '-' || character == '.'
}
