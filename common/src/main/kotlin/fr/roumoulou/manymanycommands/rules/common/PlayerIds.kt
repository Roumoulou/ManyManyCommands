// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import java.util.UUID

/** L'identité d'un joueur dans un fichier (cahier des charges, chapitre 2.7.2) : son UUID, sous la forme que le jeu écrit. */
object PlayerIds {

    /** Cinq groupes d'hexadécimaux en minuscules, séparés par des traits d'union : `069a79f4-44e9-4726-a5be-fca90e38aaf5`. */
    private val CANONICAL = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** L'UUID que [written] écrit, `null` quand ce n'en est pas un. Le lecteur du JDK admettrait des formes abrégées que le jeu n'écrit jamais. */
    fun parse(written: String): UUID? = if (CANONICAL.matches(written)) UUID.fromString(written) else null
}
