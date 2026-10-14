// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.permission

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.Identifier

/**
 * La source des droits d'un mod de permissions (cahier des charges, chapitres 2.4.2 et 2.4.5) : le module de permissions de la Fabric API,
 * interrogé sur l'identifiant du nœud, `manymanycommands:homes/create`. Le mod qui répond, LuckPerms ou un autre, accorde, retire, ou se tait,
 * et le défaut du profil décide alors. Sans mod de permissions, le module se tait toujours.
 */
object ExternalPermissionSource : PermissionSource {

    override fun verdict(source: CommandSourceStack, node: PermissionNode): Boolean? = when (source.checkPermission(Identifier.parse(node.identifier))) {
        TriState.TRUE -> true
        TriState.FALSE -> false
        TriState.DEFAULT -> null
    }
}
