// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.back

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.TeleportListener
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.rules.back.BackMessages
import fr.roumoulou.manymanycommands.rules.back.BackNodes
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.back.BackSource
import fr.roumoulou.manymanycommands.rules.back.BackTeleport
import fr.roumoulou.manymanycommands.rules.back.ReturnRules
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.Message
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/**
 * `/back` (`back.md` des specs, chapitre 3) : le joueur rejoint sa position de retour par le déroulé commun. Les refus propres du retour se
 * jugent avant la demande, parce que le point d'arrivée en dépend ; l'arrivée se dit par la famille, et devient à son tour la position de retour
 * quand la source `back` est active.
 */
class BackCommand(private val runtime: ModRuntime) {

    companion object {

        const val NAME = "back"
    }

    /** La famille s'enregistre sur son activation du démarrage (cahier des charges, chapitre 2.10). */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        if (!runtime.startupRules(BackRules).settings[BackRules.enabled]) return
        dispatcher.register(Commands.literal(NAME).requires(runtime.rights.requires(BackNodes.BACK)).executes(::back))
    }

    /** @return 1 quand la téléportation est lancée ou faite, 0 pour un refus, de la famille ou du déroulé */
    private fun back(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        if (runtime.worldData?.back?.isAvailable != true) return refuse(source, BackMessages.UNAVAILABLE.with())
        val ruleSet = runtime.ruleSet(source, BackRules)
        val server = source.server
        val charge = runtime.teleports.quotaCharge(source, player, BackRules, ruleSet)
        val target = when (val outcome = ReturnRules.teleport(runtime.returns[player.uuid], Location.of(player).dimension, ruleSet, { Location.levelOf(server, it) != null }, charge?.quota) { runtime.teleports.quotaWaitSeconds(charge) }) {
            is BackTeleport.Refused -> return refuse(source, outcome.message)
            is BackTeleport.Target -> outcome.position
        }
        val destination = Location.of(server, target) ?: error("The dimension of the return position was checked")
        val arrived = object : TeleportListener {

            override fun onArrived(from: Location, to: Location) {
                runtime.recordReturn(player, BackSource.BACK, from)
                runtime.messenger.tell(player, BackMessages.ARRIVED.with())
            }
        }
        val request = TeleportRequest(player, source, BackRules, ruleSet, Arrival.At(destination), quotaCharge = charge, listener = arrived)
        return if (runtime.teleports.launch(request) == null) Command.SINGLE_SUCCESS else 0
    }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
