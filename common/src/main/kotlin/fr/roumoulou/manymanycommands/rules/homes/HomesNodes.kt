// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/** Les nœuds des commandes du joueur de `/homes` (cahier des charges, chapitre 2.4.2), ouvertes à tout joueur. Un alias porte le nœud de la commande qu'il remplace. */
object HomesNodes {

    val CREATE = PermissionNode.ofCommand(Profile.PLAYER, "homes", "create")

    val TELEPORT = PermissionNode.ofCommand(Profile.PLAYER, "homes", "teleport")

    val DELETE = PermissionNode.ofCommand(Profile.PLAYER, "homes", "delete")

    /** `/homes` sans sous-commande prend ce nœud. */
    val LIST = PermissionNode.ofCommand(Profile.PLAYER, "homes", "list")

    val RENAME = PermissionNode.ofCommand(Profile.PLAYER, "homes", "rename")

    val MOVE = PermissionNode.ofCommand(Profile.PLAYER, "homes", "move")

    val DEFAULT = PermissionNode.ofCommand(Profile.PLAYER, "homes", "default")

    /** Les nœuds des commandes du joueur, dans l'ordre de l'arbre : le littéral `/homes` exige l'un d'eux. */
    val PLAYER_COMMANDS = listOf(CREATE, TELEPORT, DELETE, LIST, RENAME, MOVE, DEFAULT)
}
