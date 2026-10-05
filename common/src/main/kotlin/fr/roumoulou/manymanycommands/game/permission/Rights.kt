// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.permission

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import java.util.function.Predicate

/** Le contrôle d'un nœud de permission (cahier des charges, chapitres 2.4.1 et 2.4.2) : la source des droits d'abord, le défaut du profil ensuite. */
class Rights(private val permissionSource: PermissionSource) {

    /** Le profil administrateur est le niveau des maîtres du jeu, celui de `/tp`. La console l'a. */
    fun has(source: CommandSourceStack, node: PermissionNode): Boolean =
        node.isGranted(permissionSource.verdict(source, node), isGameMaster = Commands.LEVEL_GAMEMASTERS.check(source.permissions()))

    /**
     * L'exigence d'un littéral de commande : sans l'un de [nodes], la commande n'existe pas pour la source, ni à la complétion ni à l'exécution.
     * Un littéral qui porte des sous-commandes exige l'un des nœuds de sa branche.
     */
    fun requires(vararg nodes: PermissionNode): Predicate<CommandSourceStack> = Predicate { source -> nodes.any { has(source, it) } }
}
