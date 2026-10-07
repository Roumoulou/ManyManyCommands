// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

/** Les noms que le mod définit (cahier des charges, chapitre 2.3). */
object NameRules {

    private const val MAX_LENGTH = 32

    /**
     * Le nom d'un home ou d'un warp : de 1 à 32 caractères, pris parmi les lettres `A` à `Z` et `a` à `z`, les chiffres, `_` et `-`. Les
     * majuscules comptent : `base` et `Base` sont deux noms (chapitre 2.1).
     */
    fun isName(name: String): Boolean = name.length in 1..MAX_LENGTH && name.all { it in 'A'..'Z' || isLowercaseNameCharacter(it) }

    /**
     * Le nom d'un jeu de règles, celui d'un groupe et la clé d'accès d'un warp : de 1 à 32 caractères, pris parmi les lettres `a` à `z`, les
     * chiffres, `_` et `-`. Ils deviennent un segment de nœud, qui n'admet pas les majuscules.
     */
    fun isLowercaseName(name: String): Boolean = name.length in 1..MAX_LENGTH && name.all(::isLowercaseNameCharacter)

    private fun isLowercaseNameCharacter(character: Char): Boolean = character in 'a'..'z' || character in '0'..'9' || character == '_' || character == '-'
}
