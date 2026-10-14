// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/**
 * Les nœuds de `/homes` (cahier des charges, chapitre 2.4.2) : les commandes du joueur, ouvertes à tout joueur, et la branche `player`, réservée à
 * l'administrateur, l'envoi sous `player.teleport.others`. Un alias porte le nœud de la commande qu'il remplace.
 */
object HomesNodes {

    val CREATE = PermissionNode.ofCommand(Profile.PLAYER, "homes", "create")

    val TELEPORT = PermissionNode.ofCommand(Profile.PLAYER, "homes", "teleport")

    val DELETE = PermissionNode.ofCommand(Profile.PLAYER, "homes", "delete")

    /** `/homes` sans sous-commande prend ce nœud. */
    val LIST = PermissionNode.ofCommand(Profile.PLAYER, "homes", "list")

    val RENAME = PermissionNode.ofCommand(Profile.PLAYER, "homes", "rename")

    val MOVE = PermissionNode.ofCommand(Profile.PLAYER, "homes", "move")

    val DEFAULT = PermissionNode.ofCommand(Profile.PLAYER, "homes", "default")

    val PLAYER_CREATE = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "create")

    val PLAYER_TELEPORT = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "teleport")

    /** L'envoi : `/homes player <player> teleport <name> <targets>`. */
    val PLAYER_TELEPORT_OTHERS = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "teleport", "others")

    val PLAYER_DELETE = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "delete")

    val PLAYER_LIST = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "list")

    val PLAYER_RENAME = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "rename")

    val PLAYER_MOVE = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "move")

    val PLAYER_DEFAULT = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "default")

    /** Les nœuds des commandes du joueur, dans l'ordre de l'arbre. */
    val PLAYER_COMMANDS = listOf(CREATE, TELEPORT, DELETE, LIST, RENAME, MOVE, DEFAULT)

    /** Les nœuds de la branche `player`, dans l'ordre de l'arbre : le littéral `player` exige l'un d'eux. */
    val PLAYER_BRANCH = listOf(PLAYER_CREATE, PLAYER_TELEPORT, PLAYER_TELEPORT_OTHERS, PLAYER_DELETE, PLAYER_LIST, PLAYER_RENAME, PLAYER_MOVE, PLAYER_DEFAULT)

    /** Tous les nœuds de `/homes`, dans l'ordre de l'arbre : le littéral `homes` exige l'un d'eux. */
    val ALL = PLAYER_COMMANDS + PLAYER_BRANCH
}
