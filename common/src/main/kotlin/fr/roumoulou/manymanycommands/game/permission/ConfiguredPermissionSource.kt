// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.permission

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import net.minecraft.commands.CommandSourceStack

/**
 * La source des droits que choisit `permission-source` (cahier des charges, chapitre 2.4.5) : le système du mod sous `internal`, un mod de
 * permissions sous `external`. Le choix se lit au verdict, sur le réglage du démarrage, parce que l'état lit ses réglages après avoir bâti ses
 * droits.
 *
 * @param usesInternal le système du mod est la source des droits
 */
class ConfiguredPermissionSource(private val usesInternal: () -> Boolean, private val internal: PermissionSource, private val external: PermissionSource) : PermissionSource {

    override fun verdict(source: CommandSourceStack, node: PermissionNode): Boolean? = (if (usesInternal()) internal else external).verdict(source, node)
}
