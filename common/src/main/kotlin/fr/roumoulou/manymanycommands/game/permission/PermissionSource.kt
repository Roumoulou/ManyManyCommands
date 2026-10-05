// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.permission

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import net.minecraft.commands.CommandSourceStack

/** La source des droits (cahier des charges, chapitre 2.4.5) : ce qui accorde un nœud à la source d'une commande, ou le lui retire. */
fun interface PermissionSource {

    companion object {

        /** La source qui ne se prononce jamais : le défaut du profil décide seul. */
        val SILENT = PermissionSource { _, _ -> null }
    }

    /** `true` quand le nœud est accordé, `false` quand il est retiré, `null` quand la source ne se prononce pas. */
    fun verdict(source: CommandSourceStack, node: PermissionNode): Boolean?
}
