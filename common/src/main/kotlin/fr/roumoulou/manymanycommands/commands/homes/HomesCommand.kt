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
import fr.roumoulou.manymanycommands.game.teleport.TeleportListener
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.homes.HomesChange
import fr.roumoulou.manymanycommands.rules.homes.HomesListing
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.HomesTeleport
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer

/**
 * `/homes`, ses sous-commandes du joueur et ses trois alias (`homes.md` des specs, chapitres 3 et 6), hors la branche `player`.
 *
 * Chaque commande relie : elle exige un joueur et des données disponibles, lit ses arguments, applique la règle de [PlayerHomes], remet l'entrée
 * changée au fichier de données, qui l'écrit tout de suite, et répond par le message de la règle. `teleport` remet une demande au déroulé commun.
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

    /** L'arbre de la commande sous le littéral [name]. Le littéral s'ouvre à qui a l'un des nœuds de la branche, et chaque sous-commande exige le sien. */
    private fun tree(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .requires(runtime.rights.requires(*HomesNodes.PLAYER_COMMANDS.toTypedArray()))
        .executes(::listWithoutSubcommand)
        .then(createBranch(Commands.literal("create")))
        .then(teleportBranch(Commands.literal("teleport")))
        .then(deleteBranch(Commands.literal("delete")))
        .then(Commands.literal("list").requires(requires(HomesNodes.LIST)).executes(::list))
        .then(Commands.literal("rename").requires(requires(HomesNodes.RENAME)).then(homeName().then(Commands.argument(NEW_NAME_ARGUMENT, StringArgumentType.word()).executes(::rename))))
        .then(Commands.literal("move").requires(requires(HomesNodes.MOVE)).then(homeName().executes(::move)))
        .then(Commands.literal("default").requires(requires(HomesNodes.DEFAULT)).then(homeName().executes(::designate)))

    // Les branches qu'un alias reçoit comme la sous-commande qu'il remplace, bâties par la même fonction, avec le même nœud.

    /** `create <name>` : un nom se tape sans proposition. */
    private fun createBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(HomesNodes.CREATE)).then(Commands.argument(NAME_ARGUMENT, StringArgumentType.word()).executes(::create))

    /** `teleport [<name>]` : sans nom, le home par défaut. */
    private fun teleportBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(HomesNodes.TELEPORT)).executes { teleport(it, name = null) }.then(homeName().executes { teleport(it, name(it)) })

    private fun deleteBranch(literal: LiteralArgumentBuilder<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal.requires(requires(HomesNodes.DELETE)).then(homeName().executes(::delete))

    private fun requires(node: PermissionNode) = runtime.rights.requires(node)

    /** L'argument `<name>` d'une commande qui cible un home : il se complète avec les homes du joueur, dans l'ordre de la liste. */
    private fun homeName(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(NAME_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(homesOf(context.source)?.sortedNames().orEmpty(), builder) }

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
     * d'arrivée en dépend : l'étape 1 de la demande reste vide. Le déroulé commun vient ensuite, et l'arrivée se dit par la famille.
     *
     * @return 1 quand la téléportation est lancée ou faite, 0 pour un refus, de la famille ou du déroulé
     */
    private fun teleport(context: CommandContext<CommandSourceStack>, name: String?): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val homes = homesOf(source) ?: return refuse(source, HomesMessages.UNAVAILABLE.with())
        val ruleSet = runtime.ruleSet(source, HomesRules)
        val server = source.server
        val charge = runtime.teleports.quotaCharge(source, player, HomesRules, ruleSet)
        val target = when (val outcome = homes.teleport(name, Location.of(player).dimension, ruleSet, { Location.levelOf(server, it) != null }, charge?.quota) { runtime.teleports.quotaWaitSeconds(charge) }) {
            is HomesTeleport.Refused -> return refuse(source, outcome.message)
            is HomesTeleport.Target -> outcome
        }
        val destination = Location.of(server, target.position) ?: error("The dimension of the home '${target.name}' was checked")
        val arrived = object : TeleportListener {

            override fun onArrived(from: Location, to: Location) = runtime.messenger.tell(player, HomesMessages.ARRIVED.with(target.name))
        }
        val request = TeleportRequest(player, source, HomesRules, ruleSet, Arrival.At(destination), quotaCharge = charge, listener = arrived)
        return if (runtime.teleports.launch(request) == null) Command.SINGLE_SUCCESS else 0
    }

    private fun create(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, ruleSet, player -> homes.create(name(context), Location.of(player).toPosition(), ruleSet) }

    private fun delete(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, _, _ -> homes.delete(name(context)) }

    private fun rename(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, _, _ -> homes.rename(name(context), StringArgumentType.getString(context, NEW_NAME_ARGUMENT)) }

    private fun move(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, ruleSet, player -> homes.move(name(context), Location.of(player).toPosition(), ruleSet) }

    private fun designate(context: CommandContext<CommandSourceStack>): Int = change(context) { homes, _, _ -> homes.designate(name(context)) }

    private fun name(context: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(context, NAME_ARGUMENT)

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

    /** Les homes du joueur de [source], `null` sans joueur ou quand les données sont indisponibles. */
    private fun homesOf(source: CommandSourceStack): PlayerHomes? {
        val player = source.player ?: return null
        val data = runtime.worldData?.homes?.content ?: return null
        return data[player.uuid]?.holdings ?: PlayerHomes()
    }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
