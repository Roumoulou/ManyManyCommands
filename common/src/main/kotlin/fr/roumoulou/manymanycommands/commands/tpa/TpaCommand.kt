// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.tpa

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.tpa.RequestKind
import fr.roumoulou.manymanycommands.rules.tpa.TpaAnswer
import fr.roumoulou.manymanycommands.rules.tpa.TpaHoldings
import fr.roumoulou.manymanycommands.rules.tpa.TpaMessages
import fr.roumoulou.manymanycommands.rules.tpa.TpaNodes
import fr.roumoulou.manymanycommands.rules.tpa.TpaRequest
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.tpa.TpaSending
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Les six commandes des demandes de téléportation (`tpa.md` des specs, chapitre 3), chacune une commande à part entière, sans sous-commande ni
 * alias.
 *
 * Chaque commande relie : elle exige un joueur et des données disponibles, résout `<player>` parmi les joueurs connectés, applique la règle de
 * `TpaRequests`, et envoie par le `Messenger` les messages des deux joueurs. L'expiration et la déconnexion se constatent au tick du serveur,
 * par l'état (`ModRuntime.tickRequests`). L'acceptation ne lance pas encore le voyage.
 */
class TpaCommand(private val runtime: ModRuntime) {

    companion object {

        const val TPA = "tpa"
        const val TPAHERE = "tpahere"
        const val TPAACCEPT = "tpaaccept"
        const val TPADENY = "tpadeny"
        const val TPACANCEL = "tpacancel"
        const val TPATOGGLE = "tpatoggle"

        /** Les six commandes, dans l'ordre de l'arbre. */
        val ALL = listOf(TPA, TPAHERE, TPAACCEPT, TPADENY, TPACANCEL, TPATOGGLE)

        private const val PLAYER_ARGUMENT = "player"
    }

    /** La famille s'enregistre sur son activation du démarrage (cahier des charges, chapitre 2.10) : inactive, aucune des six commandes n'existe. */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        if (!runtime.startupRules(TpaRules).settings[TpaRules.enabled]) return
        dispatcher.register(Commands.literal(TPA).requires(requires(TpaNodes.TPA)).then(connectedPlayer().executes { send(it, RequestKind.TPA) }))
        dispatcher.register(Commands.literal(TPAHERE).requires(requires(TpaNodes.TPAHERE)).then(connectedPlayer().executes { send(it, RequestKind.TPAHERE) }))
        dispatcher.register(Commands.literal(TPAACCEPT).requires(requires(TpaNodes.TPAACCEPT)).executes { accept(it, name = null) }.then(requester().executes { accept(it, name(it)) }))
        dispatcher.register(Commands.literal(TPADENY).requires(requires(TpaNodes.TPADENY)).executes { deny(it, name = null) }.then(requester().executes { deny(it, name(it)) }))
        dispatcher.register(Commands.literal(TPACANCEL).requires(requires(TpaNodes.TPACANCEL)).executes(::cancel))
        dispatcher.register(Commands.literal(TPATOGGLE).requires(requires(TpaNodes.TPATOGGLE)).executes(::toggle))
    }

    private fun requires(node: PermissionNode) = runtime.rights.requires(node)

    /** `<player>` de `/tpa` et `/tpahere` : il se complète avec les joueurs connectés. */
    private fun connectedPlayer(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(PLAYER_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(context.source.server.playerList.players.map { it.scoreboardName }, builder) }

    /** `<player>` de `/tpaaccept` et `/tpadeny` : il se complète avec les joueurs dont une demande attend. */
    private fun requester(): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(PLAYER_ARGUMENT, StringArgumentType.word())
        .suggests { context, builder ->
            val player = context.source.player
            val names = if (player == null) emptyList() else runtime.requests.received(player.uuid).mapNotNull { nameOf(context.source.server, it.requester) }
            SharedSuggestionProvider.suggest(names, builder)
        }

    private fun name(context: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(context, PLAYER_ARGUMENT)

    /** Le joueur connecté qui porte [name], sans tenir compte de la casse (cahier des charges, chapitre 2.7.2), ou `null`. */
    private fun connected(server: MinecraftServer, name: String): ServerPlayer? = server.playerList.players.firstOrNull { it.scoreboardName.equals(name, ignoreCase = true) }

    private fun nameOf(server: MinecraftServer, player: UUID): String? = server.playerList.getPlayer(player)?.scoreboardName

    /**
     * `/tpa <player>` et `/tpahere <player>` (chapitre 3.1) : la console refusée, les données disponibles, puis la règle de l'envoi. La demande
     * envoyée se confirme au demandeur et se montre au destinataire, avec ses deux mots cliquables, et la demande qu'elle annule se dit à son
     * destinataire.
     *
     * @return 1 quand la demande est envoyée, 0 pour un refus
     */
    private fun send(context: CommandContext<CommandSourceStack>, kind: RequestKind): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val data = runtime.worldData?.tpa?.content ?: return refuse(source, TpaMessages.UNAVAILABLE.with())
        val server = source.server
        val typed = name(context)
        val recipient = connected(server, typed)
        val ruleSet = runtime.ruleSet(source, TpaRules)
        val charge = runtime.teleports.quotaCharge(source, player, TpaRules, ruleSet)
        val closed = recipient != null && data[recipient.uuid]?.holdings?.requestsClosed == true
        val outcome = runtime.requests.send(
            player.uuid, player.scoreboardName, recipient?.uuid, recipient?.scoreboardName ?: typed, kind, closed, runtime.rules(TpaRules).settings, charge?.quota,
            { runtime.teleports.quotaWaitSeconds(charge) }, runtime.clock.instant(),
        )
        val sent = when (outcome) {
            is TpaSending.Refused -> return refuse(source, outcome.message)
            is TpaSending.Sent -> outcome
        }
        val target = recipient ?: error("A sent request has a connected recipient")
        sent.cancelled?.let { cancelled -> server.playerList.getPlayer(cancelled.recipient)?.let { runtime.messenger.tell(it, TpaMessages.CANCELLED_RECIPIENT.with(player.scoreboardName)) } }
        runtime.messenger.reply(source, TpaMessages.SENT.with(target.scoreboardName))
        runtime.messenger.tell(target, TpaMessages.received(sent.request.kind, player.scoreboardName, sent.request.lifetimeSeconds))
        return Command.SINGLE_SUCCESS
    }

    /** `/tpaaccept [<player>]` (chapitre 3.2) : la demande consommée, les deux joueurs prévenus. */
    private fun accept(context: CommandContext<CommandSourceStack>, name: String?): Int = answer(context, name) { _, requester, recipient ->
        runtime.messenger.reply(context.source, TpaMessages.ACCEPTED_RECIPIENT.with(requester.scoreboardName))
        runtime.messenger.tell(requester, TpaMessages.ACCEPTED_REQUESTER.with(recipient.scoreboardName))
        Command.SINGLE_SUCCESS
    }

    /** `/tpadeny [<player>]` (chapitre 3.3) : la demande consommée, les deux joueurs prévenus. */
    private fun deny(context: CommandContext<CommandSourceStack>, name: String?): Int = answer(context, name) { _, requester, recipient ->
        runtime.messenger.reply(context.source, TpaMessages.DENIED_RECIPIENT.with(requester.scoreboardName))
        runtime.messenger.tell(requester, TpaMessages.DENIED_REQUESTER.with(recipient.scoreboardName))
        Command.SINGLE_SUCCESS
    }

    /**
     * Une réponse à une demande : la console refusée, les données disponibles, la demande que `<player>` désigne, ou la dernière reçue sans nom,
     * consommée, puis ce que la réponse en fait.
     *
     * @param respond ce que la réponse fait de la demande, pour son demandeur et son destinataire, et ce qu'elle rend
     * @return ce que [respond] rend, ou 0 pour un refus
     */
    private fun answer(context: CommandContext<CommandSourceStack>, name: String?, respond: (TpaRequest, requester: ServerPlayer, recipient: ServerPlayer) -> Int): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        if (runtime.worldData?.tpa?.isAvailable != true) return refuse(source, TpaMessages.UNAVAILABLE.with())
        val server = source.server
        val request = when (val answer = runtime.requests.answer(player.uuid, name?.let { connected(server, it)?.uuid }, name)) {
            is TpaAnswer.Refused -> return refuse(source, answer.message)
            is TpaAnswer.Request -> answer.request
        }
        runtime.requests.consume(request)
        // La déconnexion du demandeur aurait fait tomber la demande : il est connecté, sauf à partir dans le même tick.
        val requester = server.playerList.getPlayer(request.requester) ?: return refuse(source, TpaMessages.NONE_PENDING.with())
        return respond(request, requester, player)
    }

    /** `/tpacancel` (chapitre 3.4) : la demande envoyée, retirée, et son destinataire prévenu. */
    private fun cancel(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        if (runtime.worldData?.tpa?.isAvailable != true) return refuse(source, TpaMessages.UNAVAILABLE.with())
        val request = runtime.requests.cancel(player.uuid) ?: return refuse(source, TpaMessages.NONE_TO_CANCEL.with())
        runtime.messenger.reply(source, TpaMessages.CANCELLED_REQUESTER.with(request.recipientName))
        source.server.playerList.getPlayer(request.recipient)?.let { runtime.messenger.tell(it, TpaMessages.CANCELLED_RECIPIENT.with(player.scoreboardName)) }
        return Command.SINGLE_SUCCESS
    }

    /** `/tpatoggle` (chapitre 3.5) : la réception fermée ou rouverte, écrite tout de suite dans `tpa.json`, avec le nom du moment du joueur. */
    private fun toggle(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        val file = runtime.worldData?.tpa
        val data = file?.content ?: return refuse(source, TpaMessages.UNAVAILABLE.with())
        val entry = data[player.uuid]
        val holdings = (entry?.holdings ?: TpaHoldings()).toggled()
        file.update(data.with(player.uuid, PlayerEntry(player.scoreboardName, holdings, entry?.quota)))
        runtime.messenger.reply(source, (if (holdings.requestsClosed) TpaMessages.RECEPTION_CLOSED else TpaMessages.RECEPTION_OPENED).with())
        return Command.SINGLE_SUCCESS
    }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
