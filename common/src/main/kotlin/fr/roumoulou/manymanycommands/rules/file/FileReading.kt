// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

/** Le résultat de la lecture d'un fichier : son contenu, ou les constats qui le refusent. */
sealed interface FileReading<out CONTENT> {

    data class Applied<CONTENT>(val content: CONTENT) : FileReading<CONTENT>

    data class Refused(val findings: List<FileFinding>) : FileReading<Nothing>
}
