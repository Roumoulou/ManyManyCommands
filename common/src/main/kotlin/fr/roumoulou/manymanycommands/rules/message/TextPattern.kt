// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

/** Un texte de fichier de langue, au format du jeu : `%s` prend l'argument suivant, `%1$s` celui de son rang, `%%` écrit un pourcent. */
object TextPattern {

    private val PLACEHOLDER = Regex("""%(?:(\d+)\$)?([s%])""")

    /** Remplit [pattern] avec [arguments]. Un emplacement sans argument reste tel quel : le texte garde la trace de ce qui manque. */
    fun fill(pattern: String, arguments: List<String>): String {
        var next = 0
        return PLACEHOLDER.replace(pattern) { match ->
            val (rank, kind) = match.destructured
            when {
                kind == "%" -> "%"
                rank.isEmpty() -> arguments.getOrNull(next++) ?: match.value
                else -> arguments.getOrNull(rank.toInt() - 1) ?: match.value
            }
        }
    }

    /** Le nombre d'arguments que [pattern] attend. */
    fun argumentCount(pattern: String): Int {
        var sequential = 0
        var highestRank = 0
        PLACEHOLDER.findAll(pattern).forEach { match ->
            val (rank, kind) = match.destructured
            when {
                kind == "%" -> {}
                rank.isEmpty() -> sequential++
                else -> highestRank = maxOf(highestRank, rank.toInt())
            }
        }
        return maxOf(sequential, highestRank)
    }
}
