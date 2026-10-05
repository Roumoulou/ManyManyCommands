// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.mod

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/** Les nœuds de `/manymanycommands`, réservée à l'administrateur (`arbre-des-commandes.md` des specs, section 6). */
object ModNodes {

    val STATUS = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands")

    val RELOAD = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "reload")

    val ALL = listOf(STATUS, RELOAD)
}
