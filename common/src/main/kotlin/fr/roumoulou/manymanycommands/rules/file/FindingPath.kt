// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

/**
 * Le chemin d'une valeur dans un fichier, tel qu'un constat le donne : `rules[vip].quota.uses`.
 *
 * C'est la grammaire dans laquelle la bibliothèque des fichiers retrouve la ligne d'une valeur : un point devant la clé d'un objet, des crochets
 * autour de la clé d'une carte ou d'un index.
 */
object FindingPath {

    /** La clé [key] d'un objet, sous [parent]. Une clé qui porte un point ou un crochet s'écrit entre crochets et guillemets. */
    fun property(parent: String, key: String): String = when {
        key.any { it in ".[]" } -> "$parent[\"$key\"]"
        parent.isEmpty() -> key
        else -> "$parent.$key"
    }

    /** L'entrée [key] d'une carte, sous [parent]. Une clé vide ou faite de chiffres prend des guillemets : sans eux, elle se lirait comme un index. */
    fun entry(parent: String, key: String): String = if (key.all { it.isDigit() }) "$parent[\"$key\"]" else "$parent[$key]"

    fun index(parent: String, index: Int): String = "$parent[$index]"
}
