// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/** Les nœuds de `/wild` et de `/wild timed` (cahier des charges, chapitre 2.4.2), ouverts à tout joueur. L'alias `/rtp` porte les mêmes. */
object WildNodes {

    /** `/wild` seul : le tirage individuel. */
    val WILD = PermissionNode.ofCommand(Profile.PLAYER, "wild")

    /** `/wild timed` : le tirage commun. */
    val TIMED = PermissionNode.ofCommand(Profile.PLAYER, "wild", "timed")

    /** Les nœuds de la branche : le littéral `/wild` exige l'un d'eux. */
    val ALL = listOf(WILD, TIMED)
}
