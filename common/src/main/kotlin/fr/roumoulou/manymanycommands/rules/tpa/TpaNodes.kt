// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/** Les nœuds des six commandes des demandes (cahier des charges, chapitre 2.4.2), ouvertes à tout joueur. Chacune est une commande à part entière, sans sous-commande ni alias. */
object TpaNodes {

    val TPA = PermissionNode.ofCommand(Profile.PLAYER, "tpa")

    val TPAHERE = PermissionNode.ofCommand(Profile.PLAYER, "tpahere")

    val TPAACCEPT = PermissionNode.ofCommand(Profile.PLAYER, "tpaaccept")

    val TPADENY = PermissionNode.ofCommand(Profile.PLAYER, "tpadeny")

    val TPACANCEL = PermissionNode.ofCommand(Profile.PLAYER, "tpacancel")

    val TPATOGGLE = PermissionNode.ofCommand(Profile.PLAYER, "tpatoggle")

    /** Les six nœuds, dans l'ordre de l'arbre. */
    val ALL = listOf(TPA, TPAHERE, TPAACCEPT, TPADENY, TPACANCEL, TPATOGGLE)
}
