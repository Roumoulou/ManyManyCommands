// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.warps

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.Placement
import fr.roumoulou.manymanycommands.game.teleport.Sending
import fr.roumoulou.manymanycommands.game.teleport.TeleportListener
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.rules.back.BackSource
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.warps.Warps
import fr.roumoulou.manymanycommands.rules.warps.WarpsChange
import fr.roumoulou.manymanycommands.rules.warps.WarpsData
import fr.roumoulou.manymanycommands.rules.warps.WarpsListing
import fr.roumoulou.manymanycommands.rules.warps.WarpsMessages
import fr.roumoulou.manymanycommands.rules.warps.WarpsNodes
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsTeleport
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.CompletableFuture

/**
 * `/warps`, ses sous-commandes et ses trois alias (`warps.md` des specs). Chaque commande relie : elle exige des données disponibles, lit ses
 * arguments, applique la règle de [Warps], remet les warps changés au fichier de données, qui les écrit tout de suite, et répond par le message
 * de la règle. Une commande qui modifie un warp se journalise, comme l'envoi.
 */
class WarpsCommand(private val runtime: ModRuntime) {

    companion object {

        const val NAME = "warps"

        /** Les alias, et la sous-commande que chacun remplace (`warps.md` des specs, chapitre 5). */
        const val WARP_ALIAS = "warp"
        const val SETWARP_ALIAS = "setwarp"
        const val DELWARP_ALIAS = "delwarp"

        private const val NAME_ARGUMENT = "name"
        private const val NEW_NAME_ARGUMENT = "newName"
        private const val KEY_ARGUMENT = "key"
        private const val TARGETS_ARGUMENT = "targets"
    }

    /** La famille s'enregistre sur son activation du démarrage, et ses alias sur leur propre interrupteur, distinct de celui des homes. */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val startup = runtime.startupRules(WarpsRules).settings
        if (!startup[WarpsRules.enabled]) return
        dispatcher.register(tree(NAME))
        if (startup[WarpsRules.aliases]) {
            dispatcher.register(teleportBranch(Commands.literal(WARP_ALIAS)))
            dispatcher.register(createBranch(Commands.literal(SETWARP_ALIAS)))
            dispatcher.register(deleteBranch(Commands.literal(DELWARP_ALIAS)))
        }
    }

    /** L'arbre de la commande sous le littéral [name]. Le littéral s'ouvre à qui a l'un des nœuds de la branche, et chaque sous-commande exige le sien. */
    private fun tree(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .requires(runtime.rights.requires(*WarpsNodes.ALL.toTypedArray()))
        .executes(::listWithoutSubcommand)
        .then(teleportBranch(Commands.literal("teleport")))
        .then(Commands.literal("list").requires(requires(WarpsNodes.LIST)).executes(::list))
        .then(createBranch(Commands.literal("create")))
        .then(deleteBranch(Commands.literal("delete")))
        .then(Commands.literal("rename").requires(requires(WarpsNodes.RENAME)).then(anyWarp().then(Commands.argument(NEW_NAME_ARGUMENT, StringArgumentType.word()).executes(::rename))))
        .then(Commands.literal("move").requires(requires(WarpsNodes.MOVE)).then(Placement.attach(anyWarp(), ::move)))
        .then(Commands.literal("restrict").requires(requires(WarpsNodes.RESTRICT)).then(anyWarp().then(Commands.argument(KEY_ARGUMENT, StringArgumentType.word()).suggests(::suggestKeys).executes(::restrict))))
        .then(Commands.literal("unrestrict").requires(requires(WarpsNodes.UNRESTRICT)).then(anyWarp().executes(::unrestrict)))

    // Les branches qu'un alias reçoit comme la sous-commande qu'il remplace, bâties par la même fonction, avec le même nœud.

    /** `teleport <name> [<targets>]` : le joueur rejoint le warp, ou l'administrateur y envoie les joueurs désignés. Le littéral s'ouvre à l'un des deux nœuds. */
    private fun teleportBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> = literal
        .requires(runtime.rights.requires(WarpsNodes.TELEPORT, WarpsNodes.TELEPORT_OTHERS))
        .then(accessibleWarp().executes(::teleport).then(Commands.argument(TARGETS_ARGUMENT, EntityArgument.players()).requires(requires(WarpsNodes.TELEPORT_OTHERS)).executes(::send)))

    /** `create <name> [<pos> [<rotation> [<dimension>]]]` : un nom se tape sans proposition. */
    private fun createBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(WarpsNodes.CREATE)).then(Placement.attach(Commands.argument(NAME_ARGUMENT, StringArgumentType.word()), ::create))

    private fun deleteBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(WarpsNodes.DELETE)).then(anyWarp().executes(::delete))

    private fun requires(node: PermissionNode) = runtime.rights.requires(node)

    /** L'argument `<name>` de `teleport` : il se complète avec les warps auxquels la source a accès. */
    private fun accessibleWarp(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(NAME_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(dataOf()?.warps?.accessible(accessOf(context.source)).orEmpty(), builder) }

    /** L'argument `<name>` d'une commande de l'administrateur : il se complète avec tous les warps. */
    private fun anyWarp(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(NAME_ARGUMENT, StringArgumentType.word())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(dataOf()?.warps?.sortedNames().orEmpty(), builder) }

    /** `<key>` se complète avec les clés que portent déjà d'autres warps. */
    @Suppress("UNUSED_PARAMETER")
    private fun suggestKeys(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(dataOf()?.warps?.keys().orEmpty(), builder)

    /** `/warps` sans sous-commande affiche la liste, sous le nœud de `list` : sans lui, la commande seule n'existe pas. */
    private fun listWithoutSubcommand(context: CommandContext<CommandSourceStack>): Int {
        if (!runtime.rights.has(context.source, WarpsNodes.LIST)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()
        return list(context)
    }

    /** La liste des warps auxquels la source a accès, la console comprise ; rend leur nombre. */
    private fun list(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val data = dataOf() ?: return refuse(source, WarpsMessages.UNAVAILABLE.with())
        val hasAccess = accessOf(source)
        WarpsListing.messages(data.warps, hasAccess, showKeys = runtime.rights.has(source, WarpsNodes.RESTRICT)).forEach { runtime.messenger.reply(source, it) }
        return data.warps.accessible(hasAccess).size
    }

    /**
     * `/warps teleport <name>` (`warps.md` des specs, chapitre 2.1). Les refus propres des warps se jugent avant la demande, parce que le point
     * d'arrivée en dépend ; le déroulé commun vient ensuite. À l'arrivée, l'endroit quitté devient la position de retour, selon la source
     * `warps` du joueur, et la famille dit l'arrivée.
     *
     * Le littéral `teleport` s'ouvre aussi à qui n'a que le nœud de l'envoi : sans celui de `teleport`, rejoindre un warp n'existe pas.
     *
     * @return 1 quand la téléportation est lancée ou faite, 0 pour un refus, de la famille ou du déroulé
     */
    private fun teleport(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        if (!runtime.rights.has(source, WarpsNodes.TELEPORT)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val data = dataOf() ?: return refuse(source, WarpsMessages.UNAVAILABLE.with())
        val ruleSet = runtime.ruleSet(source, WarpsRules)
        val server = source.server
        val charge = runtime.teleports.quotaCharge(source, player, WarpsRules, ruleSet)
        val outcome = data.warps.teleport(name(context), accessOf(source), Location.of(player).dimension, ruleSet, { Location.levelOf(server, it) != null }, charge?.quota) { runtime.teleports.quotaWaitSeconds(charge) }
        val target = when (outcome) {
            is WarpsTeleport.Refused -> return refuse(source, outcome.message)
            is WarpsTeleport.Target -> outcome
        }
        val destination = Location.of(server, target.position) ?: error("The dimension of the warp '${target.name}' was checked")
        val arrived = object : TeleportListener {

            override fun onArrived(from: Location, to: Location) {
                runtime.recordReturn(player, BackSource.WARPS, from)
                runtime.messenger.tell(player, WarpsMessages.ARRIVED.with(target.name))
            }
        }
        val request = TeleportRequest(player, source, WarpsRules, ruleSet, Arrival.At(destination), quotaCharge = charge, listener = arrived)
        return if (runtime.teleports.launch(request) == null) Command.SINGLE_SUCCESS else 0
    }

    /**
     * `/warps teleport <name> <targets>` (`warps.md` des specs, chapitre 3.6) : l'envoi, sans le déroulé et sans égard pour la clé d'accès du
     * warp. Chaque joueur envoyé l'apprend, l'endroit qu'il quitte devient sa position de retour sous la source `send`, et l'administrateur
     * reçoit le compte, journalisé.
     *
     * @return le nombre de joueurs envoyés, 0 pour un refus
     */
    private fun send(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val data = dataOf() ?: return refuse(source, WarpsMessages.UNAVAILABLE.with())
        val name = name(context)
        val warp = data.warps[name] ?: return refuse(source, WarpsMessages.ABSENT.with(name))
        val destination = Location.of(source.server, warp.position) ?: return refuse(source, WarpsMessages.DIMENSION_MISSING.with(warp.position.dimension, name))
        val targets = EntityArgument.getPlayers(context, TARGETS_ARGUMENT)
        val sent = mutableListOf<ServerPlayer>()
        val danger = Sending.send(targets, destination) { player, from ->
            runtime.recordReturn(player, BackSource.SEND, from)
            runtime.messenger.tell(player, WarpsMessages.SENT_TO.with(name))
            sent += player
        }
        if (danger != null) return refuse(source, danger.situation.with())
        runtime.messenger.reply(source, WarpsMessages.sent(sent.size, name), logged = true)
        return sent.size
    }

    private fun create(context: CommandContext<CommandSourceStack>, given: Placement.Given): Int = place(context, given) { warps, location -> warps.create(name(context), location.toPosition()) { Placement.isWithinWorld(location) } }

    private fun move(context: CommandContext<CommandSourceStack>, given: Placement.Given): Int = place(context, given) { warps, location -> warps.move(name(context), location.toPosition()) { Placement.isWithinWorld(location) } }

    private fun delete(context: CommandContext<CommandSourceStack>): Int = change(context) { it.delete(name(context)) }

    private fun rename(context: CommandContext<CommandSourceStack>): Int = change(context) { it.rename(name(context), StringArgumentType.getString(context, NEW_NAME_ARGUMENT)) }

    private fun restrict(context: CommandContext<CommandSourceStack>): Int = change(context) { it.restrict(name(context), StringArgumentType.getString(context, KEY_ARGUMENT)) }

    private fun unrestrict(context: CommandContext<CommandSourceStack>): Int = change(context) { it.unrestrict(name(context)) }

    private fun name(context: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(context, NAME_ARGUMENT)

    /** Une commande qui pose un warp : au placement donné, ou à la position de la source, qui doit alors être un joueur (cahier des charges, chapitre 2.9). */
    private fun place(context: CommandContext<CommandSourceStack>, given: Placement.Given, action: (Warps, Location) -> WarpsChange): Int {
        val source = context.source
        if (!given.pos && source.player == null) return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val location = Placement.resolve(context, given)
        return change(context) { action(it, location) }
    }

    /**
     * Une commande qui modifie les warps : les données indisponibles sont refusées, puis la règle décide. Un changement appliqué remet les warps
     * au fichier, qui les écrit tout de suite, et se journalise.
     *
     * @return 1 pour un succès, 0 pour un refus
     */
    private fun change(context: CommandContext<CommandSourceStack>, action: (Warps) -> WarpsChange): Int {
        val source = context.source
        val file = runtime.worldData?.warps
        val data = file?.content ?: return refuse(source, WarpsMessages.UNAVAILABLE.with())
        return when (val change = action(data.warps)) {
            is WarpsChange.Refused -> refuse(source, change.message)
            is WarpsChange.Applied -> {
                file.update(data.copy(warps = change.warps))
                runtime.messenger.reply(source, change.message, logged = true)
                Command.SINGLE_SUCCESS
            }
        }
    }

    /** Les données des warps, `null` quand elles sont indisponibles. */
    private fun dataOf(): WarpsData? = runtime.worldData?.warps?.content

    /** Ce que [source] a comme accès : le nœud d'une clé. */
    private fun accessOf(source: CommandSourceStack): (String) -> Boolean = { key -> runtime.rights.has(source, WarpsNodes.access(key)) }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
