// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/**
 * Les nœuds de `/warps` (cahier des charges, chapitre 2.4.2) : `teleport` et `list` ouverts à tout joueur, le reste réservé à l'administrateur,
 * l'envoi sous `teleport.others`. Un alias porte le nœud de la commande qu'il remplace.
 */
object WarpsNodes {

    val TELEPORT = PermissionNode.ofCommand(Profile.PLAYER, "warps", "teleport")

    /** L'envoi : `/warps teleport <name> <targets>`. */
    val TELEPORT_OTHERS = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "teleport", "others")

    /** `/warps` sans sous-commande prend ce nœud. */
    val LIST = PermissionNode.ofCommand(Profile.PLAYER, "warps", "list")

    val CREATE = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "create")

    val DELETE = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "delete")

    val RENAME = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "rename")

    val MOVE = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "move")

    val RESTRICT = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "restrict")

    val UNRESTRICT = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "warps", "unrestrict")

    /** Les nœuds des commandes, dans l'ordre de l'arbre : le littéral `/warps` exige l'un d'eux. */
    val ALL = listOf(TELEPORT, TELEPORT_OTHERS, LIST, CREATE, DELETE, RENAME, MOVE, RESTRICT, UNRESTRICT)

    /** Le nœud d'accès d'un warp réservé par [key] (`warps.md` des specs, chapitre 3.5) : l'administrateur les reçoit tous par défaut. */
    fun access(key: String): PermissionNode = PermissionNode(listOf("warps", "access", key), Profile.ADMINISTRATOR)
}
