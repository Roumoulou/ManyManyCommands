// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.homes

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.Placement
import fr.roumoulou.manymanycommands.game.teleport.Sending
import fr.roumoulou.manymanycommands.game.teleport.TeleportListener
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.rules.back.BackSource
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.homes.HomesActor
import fr.roumoulou.manymanycommands.rules.homes.HomesChange
import fr.roumoulou.manymanycommands.rules.homes.HomesListing
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.HomesTeleport
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.players.PlayerRef
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * `/homes`, ses sous-commandes du joueur, ses trois alias et sa branche `player` (`homes.md` des specs, chapitres 3, 4 et 6).
 *
 * Chaque commande relie : elle exige des données disponibles et, pour une commande du joueur, un joueur, lit ses arguments, applique la règle de
 * [PlayerHomes], remet l'entrée changée au fichier de données, qui l'écrit tout de suite, et répond par le message de la règle. `teleport` remet
 * une demande au déroulé commun. La branche `player` agit sur les homes du joueur nommé, résolu par le registre, sous l'[HomesActor] de
 * l'administrateur, et chacune de ses commandes se journalise.
 */
class HomesCommand(private val runtime: ModRuntime) {

    companion object {

        const val NAME = "homes"

        /** Les alias, et la sous-commande que chacun remplace (`homes.md` des specs, chapitre 6). */
        const val HOME_ALIAS = "home"
        const val SETHOME_ALIAS = "sethome"
        const val DELHOME_ALIAS = "delhome"

        private const val NAME_ARGUMENT = "name"
        private const val NEW_NAME_ARGUMENT = "newName"
        private const val PLAYER_ARGUMENT = "player"
        private const val TARGETS_ARGUMENT = "targets"
    }

    /**
     * La famille s'enregistre sur son activation du démarrage (cahier des charges, chapitre 2.10) : inactive, elle n'a ni commandes ni alias. Les
     * alias suivent leur propre interrupteur, pour le serveur où un autre mod porte déjà `/home`.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val startup = runtime.startupRules(HomesRules).settings
        if (!startup[HomesRules.enabled]) return
        dispatcher.register(tree(NAME))
        if (startup[HomesRules.aliases]) {
            dispatcher.register(teleportBranch(Commands.literal(HOME_ALIAS)))
            dispatcher.register(createBranch(Commands.literal(SETHOME_ALIAS)))
            dispatcher.register(deleteBranch(Commands.literal(DELHOME_ALIAS)))
        }
    }

    /** L'arbre de la commande sous le littéral [name]. Le littéral s'ouvre à qui a l'un des nœuds de l'arbre, et chaque sous-commande exige le sien. */
    private fun tree(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .requires(runtime.rights.requires(*HomesNodes.ALL.toTypedArray()))
        .executes(::listWithoutSubcommand)
        .then(createBranch(Commands.literal("create")))
        .then(teleportBranch(Commands.literal("teleport")))
        .then(deleteBranch(Commands.literal("delete")))
        .then(Commands.literal("list").requires(requires(HomesNodes.LIST)).executes(::list))
        .then(Commands.literal("rename").requires(requires(HomesNodes.RENAME)).then(homeName().then(Commands.argument(NEW_NAME_ARGUMENT, StringArgumentType.word()).executes(::rename))))
        .then(Commands.literal("move").requires(requires(HomesNodes.MOVE)).then(homeName().executes(::move)))
        .then(Commands.literal("default").requires(requires(HomesNodes.DEFAULT)).then(homeName().executes(::designate)))
        .then(playerBranch())

    // Les branches qu'un alias reçoit comme la sous-commande qu'il remplace, bâties par la même fonction, avec le même nœud.

    /** `create <name>` : un nom se tape sans proposition. */
    private fun createBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(HomesNodes.CREATE)).then(Commands.argument(NAME_ARGUMENT, StringArgumentType.word()).executes(::create))

    /** `teleport [<name>]` : sans nom, le home par défaut. */
    private fun teleportBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(HomesNodes.TELEPORT)).executes { teleport(it, name = null) }.then(homeName().executes { teleport(it, name(it)) })

    private fun deleteBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(HomesNodes.DELETE)).then(homeName().executes(::delete))

    /**
     * La branche `player <player>` (chapitre 4) : les sept commandes sur les homes du joueur nommé, et l'envoi sous `teleport <name> <targets>`.
     * Le littéral s'ouvre à qui a l'un de ses nœuds, chaque sous-commande exige le sien, et `teleport` s'ouvre aussi à qui n'a que l'envoi.
     * `<player>` se complète avec les joueurs connectés, `<name>` avec les homes du joueur nommé.
     */
    private fun playerBranch(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("player")
        .requires(runtime.rights.requires(*HomesNodes.PLAYER_BRANCH.toTypedArray()))
        .then(
            Commands.argument(PLAYER_ARGUMENT, StringArgumentType.word())
                .suggests { context, builder -> SharedSuggestionProvider.suggest(context.source.server.playerList.players.map { it.scoreboardName }, builder) }
                .then(Commands.literal("create").requires(requires(HomesNodes.PLAYER_CREATE)).then(Placement.attach(Commands.argument(NAME_ARGUMENT, StringArgumentType.word()), ::createForPlayer)))
                .then(
                    Commands.literal("teleport").requires(runtime.rights.requires(HomesNodes.PLAYER_TELEPORT, HomesNodes.PLAYER_TELEPORT_OTHERS))
                        .then(ownerHome().executes(::teleportForPlayer).then(Commands.argument(TARGETS_ARGUMENT, EntityArgument.players()).requires(requires(HomesNodes.PLAYER_TELEPORT_OTHERS)).executes(::sendForPlayer))),
                )
                .then(Commands.literal("delete").requires(requires(HomesNodes.PLAYER_DELETE)).then(ownerHome().executes(::deleteForPlayer)))
                .then(Commands.literal("list").requires(requires(HomesNodes.PLAYER_LIST)).executes(::listForPlayer))
                .then(Commands.literal("rename").requires(requires(HomesNodes.PLAYER_RENAME)).then(ownerHome().then(Commands.argument(NEW_NAME_ARGUMENT, StringArgumentType.word()).executes(::renameForPlayer))))
                .then(Commands.literal("move").requires(requires(HomesNodes.PLAYER_MOVE)).then(Placement.attach(ownerHome(), ::moveForPlayer)))
                .then(Commands.literal("default").requires(requires(HomesNodes.PLAYER_DEFAULT)).then(ownerHome().executes(::designateForPlayer))),
        )

    private fun requires(node: PermissionNode) = runtime.rights.requires(node)

    /** L'argument `<name>` d'une commande qui cible un home : il se complète avec les homes du joueur, dans l'ordre de la liste. */
    private fun homeName(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(NAME_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(homesOf(context.source)?.sortedNames().orEmpty(), builder) }

    /** L'argument `<name>` de la branche `player` : il se complète avec les homes du joueur nommé. */
    private fun ownerHome(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(NAME_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(ownerOf(context)?.let { homesOfPlayer(it.id) }?.sortedNames().orEmpty(), builder) }

    /** `/homes` sans sous-commande affiche la liste, sous le nœud de `list` : sans lui, la commande seule n'existe pas. */
    private fun listWithoutSubcommand(context: CommandContext<CommandSourceStack>): Int {
        if (!runtime.rights.has(context.source, HomesNodes.LIST)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()
        return list(context)
    }

    /** Rend le nombre de homes. */
    private fun list(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val homes = homesOf(source) ?: return refuse(source, HomesMessages.UNAVAILABLE.with())
        HomesListing.messages(homes, runtime.ruleSet(source, HomesRules)[HomesRules.maxHomes]).forEach { runtime.messenger.reply(source, it) }
        return homes.homes.size
    }

    /**
     * `/homes teleport [<name>]` (`homes.md` des specs, chapitre 3.2). Les refus propres des homes se jugent avant la demande, parce que le point
     * d'arrivée en dépend : l'étape 1 de la demande reste vide. Le déroulé commun vient ensuite ; à l'arrivée, l'endroit quitté devient la position
     * de retour, selon la source `homes` du joueur, et la famille dit l'arrivée.
     *
     * @return 1 quand la téléportation est lancée ou faite, 0 pour un refus, de la famille ou du déroulé
     */
    private fun teleport(context: CommandContext<CommandSourceStack>, name: String?): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val homes = homesOf(source) ?: return refuse(source, HomesMessages.UNAVAILABLE.with())
        return travel(source, player, homes, name, HomesActor.Owner) { target -> runtime.messenger.tell(player, HomesMessages.ARRIVED.with(target.name)) }
    }

    /**
     * Le déroulé commun vers un home de [homes], pour [player], sous le jeu de règles de sa source : les refus propres des homes d'abord, selon
     * l'[actor], puis la demande ; à l'arrivée, l'endroit quitté devient la position de retour, selon la source `homes`, et [arrived] dit
     * l'arrivée.
     */
    private fun travel(source: CommandSourceStack, player: ServerPlayer, homes: PlayerHomes, name: String?, actor: HomesActor, arrived: (HomesTeleport.Target) -> Unit): Int {
        val ruleSet = runtime.ruleSet(source, HomesRules)
        val server = source.server
        val charge = runtime.teleports.quotaCharge(source, player, HomesRules, ruleSet)
        val outcome = homes.teleport(name, Location.of(player).dimension, ruleSet, { Location.levelOf(server, it) != null }, charge?.quota, actor) { runtime.teleports.quotaWaitSeconds(charge) }
        val target = when (outcome) {
            is HomesTeleport.Refused -> return refuse(source, outcome.message)
            is HomesTeleport.Target -> outcome
        }
        val destination = Location.of(server, target.position) ?: error("The dimension of the home '${target.name}' was checked")
        val listener = object : TeleportListener {

            override fun onArrived(from: Location, to: Location) {
                runtime.recordReturn(player, BackSource.HOMES, from)
                arrived(target)
            }
        }
        val request = TeleportRequest(player, source, HomesRules, ruleSet, Arrival.At(destination), quotaCharge = charge, listener = listener)
        return if (runtime.teleports.launch(request) == null) Command.SINGLE_SUCCESS else 0
    }

    private fun create(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, ruleSet, player -> homes.create(name(context), Location.of(player).toPosition(), ruleSet) }

    private fun delete(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, _, _ -> homes.delete(name(context)) }

    private fun rename(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, _, _ -> homes.rename(name(context), newName(context)) }

    private fun move(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, ruleSet, player -> homes.move(name(context), Location.of(player).toPosition(), ruleSet) }

    private fun designate(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, _, _ -> homes.designate(name(context)) }

    private fun name(context: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(context, NAME_ARGUMENT)

    private fun newName(context: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(context, NEW_NAME_ARGUMENT)

    /**
     * Une commande qui change les homes du joueur : la console est refusée, puis les données indisponibles, puis la règle décide. Un changement
     * appliqué remet l'entrée du joueur au fichier, sous son nom du moment, qui l'écrit tout de suite.
     *
     * @return 1 pour un succès, 0 pour un refus
     */
    private fun change(context: CommandContext<CommandSourceStack>, action: (homes: PlayerHomes, ruleSet: RuleValues, player: ServerPlayer) -> HomesChange): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val file = runtime.worldData?.homes
        val data = file?.content ?: return refuse(source, HomesMessages.UNAVAILABLE.with())
        val entry = data[player.uuid]
        return when (val change = action(entry?.holdings ?: PlayerHomes(), runtime.ruleSet(source, HomesRules), player)) {
            is HomesChange.Refused -> refuse(source, change.message)
            is HomesChange.Applied -> {
                file.update(data.with(player.uuid, PlayerEntry(player.scoreboardName, change.homes, entry?.quota)))
                runtime.messenger.reply(source, change.message)
                Command.SINGLE_SUCCESS
            }
        }
    }

    // La branche player (chapitre 4) : chaque commande se journalise, et le propriétaire n'est pas prévenu.

    private fun createForPlayer(context: CommandContext<CommandSourceStack>, given: Placement.Given): Int =
        placeForPlayer(context, given) { homes, actor, location -> homes.create(name(context), location.toPosition(), runtime.ruleSet(context.source, HomesRules), actor) { Placement.isWithinWorld(location) } }

    private fun moveForPlayer(context: CommandContext<CommandSourceStack>, given: Placement.Given): Int =
        placeForPlayer(context, given) { homes, actor, location -> homes.move(name(context), location.toPosition(), runtime.ruleSet(context.source, HomesRules), actor) { Placement.isWithinWorld(location) } }

    private fun deleteForPlayer(context: CommandContext<CommandSourceStack>): Int = changeForPlayer(context) { homes, actor -> homes.delete(name(context), actor) }

    private fun renameForPlayer(context: CommandContext<CommandSourceStack>): Int = changeForPlayer(context) { homes, actor -> homes.rename(name(context), newName(context), actor) }

    private fun designateForPlayer(context: CommandContext<CommandSourceStack>): Int = changeForPlayer(context) { homes, actor -> homes.designate(name(context), actor) }

    /** `list` de la branche : la liste du joueur nommé, dont l'en-tête se journalise, la trace de la consultation. Rend le nombre de homes. */
    private fun listForPlayer(context: CommandContext<CommandSourceStack>): Int = withOwner(context) { owner, homes, _ ->
        HomesListing.messagesOf(owner.name, homes).forEachIndexed { index, message -> runtime.messenger.reply(context.source, message, logged = index == 0) }
        homes.homes.size
    }

    /**
     * `teleport <name>` de la branche : l'administrateur rejoint le home du joueur nommé par le déroulé commun, sous son propre jeu de règles,
     * sans la dimension de départ. L'arrivée se dit à l'administrateur, journalisée. Le littéral s'ouvre aussi à qui n'a que l'envoi.
     */
    private fun teleportForPlayer(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        if (!runtime.rights.has(source, HomesNodes.PLAYER_TELEPORT)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        return withOwner(context) { owner, homes, actor ->
            travel(source, player, homes, name(context), actor) { target -> runtime.messenger.reply(source, HomesMessages.ARRIVED_OF_PLAYER.with(target.name, owner.name), logged = true) }
        }
    }

    /**
     * `teleport <name> <targets>` de la branche : l'envoi, sans le déroulé (cahier des charges, chapitre 2.5.10). Chaque joueur envoyé l'apprend,
     * l'endroit qu'il quitte devient sa position de retour sous la source `send`, et l'administrateur reçoit le compte, journalisé.
     *
     * @return le nombre de joueurs envoyés, 0 pour un refus
     */
    private fun sendForPlayer(context: CommandContext<CommandSourceStack>): Int = withOwner(context) { owner, homes, _ ->
        val source = context.source
        val name = name(context)
        val position = homes[name] ?: return@withOwner refuse(source, HomesMessages.ABSENT.with(name))
        val destination = Location.of(source.server, position) ?: return@withOwner refuse(source, HomesMessages.DIMENSION_MISSING.with(position.dimension, name))
        val sent = mutableListOf<ServerPlayer>()
        val danger = Sending.send(EntityArgument.getPlayers(context, TARGETS_ARGUMENT), destination) { player, from ->
            runtime.recordReturn(player, BackSource.SEND, from)
            runtime.messenger.tell(player, HomesMessages.SENT_TO.with(name, owner.name))
            sent += player
        }
        if (danger != null) return@withOwner refuse(source, danger.situation.with())
        runtime.messenger.reply(source, HomesMessages.sent(sent.size, name, owner.name), logged = true)
        sent.size
    }

    /** Une commande de la branche qui pose un home : au placement donné, ou à la position de la source, qui doit alors être un joueur (cahier des charges, chapitre 2.9). */
    private fun placeForPlayer(context: CommandContext<CommandSourceStack>, given: Placement.Given, action: (PlayerHomes, HomesActor, Location) -> HomesChange): Int {
        val source = context.source
        if (!given.pos && source.player == null) return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val location = Placement.resolve(context, given)
        return changeForPlayer(context) { homes, actor -> action(homes, actor, location) }
    }

    /**
     * Une commande de la branche qui change les homes du joueur nommé : la règle décide, sous l'[HomesActor] de l'administrateur. Un changement
     * appliqué remet l'entrée du joueur nommé au fichier, sous son nom du moment ou son dernier nom connu, qui l'écrit tout de suite, et se
     * journalise.
     *
     * @return 1 pour un succès, 0 pour un refus
     */
    private fun changeForPlayer(context: CommandContext<CommandSourceStack>, action: (PlayerHomes, HomesActor) -> HomesChange): Int = withOwner(context) { owner, homes, actor ->
        val source = context.source
        val file = runtime.worldData?.homes ?: error("The owner was resolved on available data")
        val data = file.content ?: error("The owner was resolved on available data")
        when (val change = action(homes, actor)) {
            is HomesChange.Refused -> refuse(source, change.message)
            is HomesChange.Applied -> {
                file.update(data.with(owner.id, PlayerEntry(owner.name, change.homes, data[owner.id]?.quota)))
                runtime.messenger.reply(source, change.message, logged = true)
                Command.SINGLE_SUCCESS
            }
        }
    }

    /**
     * Ce que toute commande de la branche exige : des données disponibles, puis le joueur que `<player>` désigne, par le registre, connecté ou
     * non ; le refus « joueur inconnu » reprend le nom tapé. [block] reçoit le joueur, ses homes et l'[HomesActor] de l'administrateur.
     */
    private inline fun withOwner(context: CommandContext<CommandSourceStack>, block: (PlayerRef, PlayerHomes, HomesActor) -> Int): Int {
        val source = context.source
        val data = runtime.worldData?.homes?.content ?: return refuse(source, HomesMessages.UNAVAILABLE.with())
        val typed = StringArgumentType.getString(context, PLAYER_ARGUMENT)
        val owner = runtime.resolvePlayer(source.server, typed) ?: return refuse(source, HomesMessages.UNKNOWN_PLAYER.with(typed))
        return block(owner, data[owner.id]?.holdings ?: PlayerHomes(), HomesActor.Administrator(owner.name))
    }

    /** Le joueur que `<player>` désigne dans [context], `null` quand personne. */
    private fun ownerOf(context: CommandContext<CommandSourceStack>): PlayerRef? = runtime.resolvePlayer(context.source.server, StringArgumentType.getString(context, PLAYER_ARGUMENT))

    /** Les homes du joueur [id], aucun sans entrée, `null` quand les données sont indisponibles. */
    private fun homesOfPlayer(id: UUID): PlayerHomes? = runtime.worldData?.homes?.content?.let { it[id]?.holdings ?: PlayerHomes() }

    /** Les homes du joueur de [source], `null` sans joueur ou quand les données sont indisponibles. */
    private fun homesOf(source: CommandSourceStack): PlayerHomes? = source.player?.let { homesOfPlayer(it.uuid) }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
