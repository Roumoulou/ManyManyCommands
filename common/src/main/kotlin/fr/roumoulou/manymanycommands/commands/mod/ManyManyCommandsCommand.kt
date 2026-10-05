// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.mod

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/** `/manymanycommands`, sa sous-commande `reload` et son alias `/mmc` (`manymanycommands.md` des specs). */
class ManyManyCommandsCommand(private val runtime: ModRuntime) {

    companion object {
        const val NAME = "manymanycommands"
        const val ALIAS = "mmc"
    }

    /** L'alias s'enregistre sur le réglage `mmc-alias` du démarrage : le changer ne prend effet qu'au redémarrage du serveur. */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(tree(NAME))
        if (runtime.startupSettings.mmcAlias) dispatcher.register(tree(ALIAS))
    }

    /** L'arbre de la commande sous le littéral [name] : l'alias reçoit le même, donc toutes ses sous-commandes et leurs nœuds. */
    private fun tree(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .requires(runtime.rights.requires(*ModNodes.ALL.toTypedArray()))
        .executes(::showStatus)
        .then(Commands.literal("reload").requires(runtime.rights.requires(ModNodes.RELOAD)).executes(::reload))

    private fun showStatus(context: CommandContext<CommandSourceStack>): Int {
        // Le littéral s'ouvre à qui a l'un des nœuds de sa branche. L'état a son propre nœud : sans lui, la commande seule n'existe pas.
        if (!runtime.rights.has(context.source, ModNodes.STATUS)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()
        runtime.status().forEach { runtime.messenger.reply(context.source, it) }
        return Command.SINGLE_SUCCESS
    }

    /** Rend le nombre de fichiers appliqués. Les lignes de succès se journalisent, comme la réponse d'une commande d'opérateur du jeu. */
    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        runtime.reload().forEach { runtime.messenger.reply(context.source, it, logged = it.type == MessageType.SUCCESS) }
        return runtime.settingsFiles.count { it.isValid }
    }
}
