// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.groups

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.rules.groups.GroupsAdmin
import fr.roumoulou.manymanycommands.rules.groups.GroupsMessages
import fr.roumoulou.manymanycommands.rules.groups.GroupsNodes
import fr.roumoulou.manymanycommands.rules.groups.GroupsOutcome
import fr.roumoulou.manymanycommands.rules.groups.Memberships
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.NodeCatalog
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.players.PlayerRef
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider

/**
 * Les branches `group` et `permission` de `/manymanycommands` (`groups.md` des specs, chapitre 5), réservées à l'administrateur, que la console
 * lance aussi.
 *
 * Chaque commande relie : elle exige le système du mod en source des droits et des inscriptions disponibles, résout `<player>` par le registre,
 * connecté ou non, applique la règle de [GroupsAdmin], remet les inscriptions changées au fichier de données, qui les écrit tout de suite, et
 * répond par le message de la règle, journalisé quand la commande modifie.
 */
class GroupsCommand(private val runtime: ModRuntime) {

    companion object {

        const val GROUP = "group"
        const val PERMISSION = "permission"

        private const val PLAYER_ARGUMENT = "player"
        private const val GROUP_ARGUMENT = "group"
        private const val NODE_ARGUMENT = "node"
    }

    /** La branche `group`. Le littéral s'ouvre à qui a l'un de ses nœuds, et chaque sous-commande exige le sien. */
    fun groupBranch(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(GROUP)
        .requires(runtime.rights.requires(*GroupsNodes.GROUP.toTypedArray()))
        .then(Commands.literal("list").requires(requires(GroupsNodes.GROUP_LIST)).executes(::list))
        .then(Commands.literal("info").requires(requires(GroupsNodes.GROUP_INFO)).then(group().executes(::info)))
        .then(Commands.literal("set").requires(requires(GroupsNodes.GROUP_SET)).then(player().then(group().executes(::set))))
        .then(Commands.literal("reset").requires(requires(GroupsNodes.GROUP_RESET)).then(player().executes(::reset)))
        .then(Commands.literal("of").requires(requires(GroupsNodes.GROUP_OF)).then(player().executes(::of)))

    /** La branche `permission`, bâtie de même. */
    fun permissionBranch(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(PERMISSION)
        .requires(runtime.rights.requires(*GroupsNodes.PERMISSION.toTypedArray()))
        .then(Commands.literal("allow").requires(requires(GroupsNodes.PERMISSION_ALLOW)).then(player().then(node().executes(::allow))))
        .then(Commands.literal("deny").requires(requires(GroupsNodes.PERMISSION_DENY)).then(player().then(node().executes(::deny))))
        .then(Commands.literal("unset").requires(requires(GroupsNodes.PERMISSION_UNSET)).then(player().then(node().executes(::unset))))
        .then(Commands.literal("list").requires(requires(GroupsNodes.PERMISSION_LIST)).then(player().executes(::permissions)))

    private fun requires(node: PermissionNode) = runtime.rights.requires(node)

    /** `<player>` : tout joueur du registre, connecté ou non ; il se complète avec les joueurs connectés. */
    private fun player(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(PLAYER_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(context.source.server.playerList.players.map { it.scoreboardName }, builder) }

    /** `<group>` se complète avec les groupes de `groups.json`. */
    private fun group(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(GROUP_ARGUMENT, StringArgumentType.word())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(runtime.groups.names, builder) }

    /** `<node>` se complète avec les nœuds du mod : ceux des commandes et des dispenses, puis ceux des jeux de règles en vigueur. */
    private fun node(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(NODE_ARGUMENT, StringArgumentType.word())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(NodeCatalog.FIXED.map { it.name } + runtime.ruleSetNodeNames(), builder) }

    /** Rend le nombre de groupes. */
    private fun list(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        GroupsAdmin.list(runtime.groups, data, runtime.serverData.players.content).forEach { runtime.messenger.reply(context.source, it) }
        runtime.groups.names.size
    }

    private fun info(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        answer(context.source, GroupsAdmin.info(StringArgumentType.getString(context, GROUP_ARGUMENT), runtime.groups, data, runtime.serverData.players.content))
    }

    private fun set(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player -> answer(context.source, GroupsAdmin.set(player, StringArgumentType.getString(context, GROUP_ARGUMENT), runtime.groups, data)) }
    }

    private fun reset(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player -> answer(context.source, GroupsAdmin.reset(player, data)) }
    }

    private fun of(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player ->
            runtime.messenger.reply(context.source, GroupsAdmin.of(player, runtime.groups, data))
            Command.SINGLE_SUCCESS
        }
    }

    private fun allow(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player -> answer(context.source, GroupsAdmin.allow(player, node(context), data, runtime::hasRuleSet)) }
    }

    private fun deny(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player -> answer(context.source, GroupsAdmin.deny(player, node(context), data, runtime::hasRuleSet)) }
    }

    private fun unset(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player -> answer(context.source, GroupsAdmin.unset(player, node(context), data)) }
    }

    /** Rend le nombre de permissions propres du joueur. */
    private fun permissions(context: CommandContext<CommandSourceStack>): Int = withMemberships(context.source) { data ->
        withPlayer(context) { player ->
            GroupsAdmin.permissions(player, data).forEach { runtime.messenger.reply(context.source, it) }
            data[player.id]?.holdings?.let { it.allow.size + it.deny.size } ?: 0
        }
    }

    private fun node(context: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(context, NODE_ARGUMENT)

    /** Ce que toute commande des groupes exige d'abord : le système du mod en source des droits (chapitre 1), puis des inscriptions disponibles (chapitre 4). */
    private inline fun withMemberships(source: CommandSourceStack, block: (Memberships) -> Int): Int {
        if (!runtime.usesInternalRights) return refuse(source, GroupsMessages.INACTIVE.with())
        val data = runtime.serverData.memberships.content ?: return refuse(source, GroupsMessages.UNAVAILABLE.with())
        return block(data)
    }

    /** Le joueur que `<player>` désigne, par le registre, ou le refus « joueur inconnu », avec le nom tapé. */
    private inline fun withPlayer(context: CommandContext<CommandSourceStack>, block: (PlayerRef) -> Int): Int {
        val typed = StringArgumentType.getString(context, PLAYER_ARGUMENT)
        val player = runtime.resolvePlayer(context.source.server, typed) ?: return refuse(context.source, GroupsMessages.UNKNOWN_PLAYER.with(typed))
        return block(player)
    }

    /**
     * Répond l'issue d'une commande : le refus, l'affichage, ou le changement, écrit tout de suite dans `data\memberships.json` et répondu
     * journalisé, comme une commande d'opérateur (cahier des charges, chapitre 2.11).
     *
     * @return 1 pour un affichage ou un changement, 0 pour un refus
     */
    private fun answer(source: CommandSourceStack, outcome: GroupsOutcome): Int = when (outcome) {
        is GroupsOutcome.Refused -> refuse(source, outcome.message)
        is GroupsOutcome.Shown -> {
            outcome.messages.forEach { runtime.messenger.reply(source, it) }
            Command.SINGLE_SUCCESS
        }
        is GroupsOutcome.Changed -> {
            runtime.serverData.memberships.update(outcome.data)
            runtime.messenger.reply(source, outcome.message, logged = true)
            Command.SINGLE_SUCCESS
        }
    }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
