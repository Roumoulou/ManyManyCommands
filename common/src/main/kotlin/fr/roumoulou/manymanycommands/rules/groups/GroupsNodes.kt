// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/**
 * Les nœuds des branches `group` et `permission` de `/manymanycommands` (`groups.md` des specs, chapitre 5), réservées à l'administrateur. Elles
 * ne répètent pas le nom de la commande : `manymanycommands.group.set`.
 */
object GroupsNodes {

    val GROUP_LIST = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "group", "list")

    val GROUP_INFO = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "group", "info")

    val GROUP_SET = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "group", "set")

    val GROUP_RESET = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "group", "reset")

    val GROUP_OF = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "group", "of")

    val PERMISSION_ALLOW = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "permission", "allow")

    val PERMISSION_DENY = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "permission", "deny")

    val PERMISSION_UNSET = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "permission", "unset")

    val PERMISSION_LIST = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "permission", "list")

    /** Les nœuds de la branche `group`, dans l'ordre de l'arbre : son littéral exige l'un d'eux. */
    val GROUP = listOf(GROUP_LIST, GROUP_INFO, GROUP_SET, GROUP_RESET, GROUP_OF)

    /** Les nœuds de la branche `permission`, dans l'ordre de l'arbre : son littéral exige l'un d'eux. */
    val PERMISSION = listOf(PERMISSION_ALLOW, PERMISSION_DENY, PERMISSION_UNSET, PERMISSION_LIST)

    /** Les neuf nœuds, dans l'ordre de l'arbre. */
    val ALL = GROUP + PERMISSION
}
