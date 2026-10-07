// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile

/** Le nœud de `/back` (cahier des charges, chapitre 2.4.2), ouvert à tout joueur. */
object BackNodes {

    val BACK = PermissionNode.ofCommand(Profile.PLAYER, "back")
}
