// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands.wild

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.TeleportListener
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.game.wild.SafePointSearcher
import fr.roumoulou.manymanycommands.rules.back.BackSource
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.wild.WildMessages
import fr.roumoulou.manymanycommands.rules.wild.WildNodes
import fr.roumoulou.manymanycommands.rules.wild.WildRefusals
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import kotlin.random.Random

/**
 * `/wild`, `/wild timed` et l'alias `/rtp` (`wild.md` des specs) : le joueur rejoint un point tiré au hasard par le déroulé commun, où la
 * recherche du point sûr tient lieu de contrôle de l'arrivée. Les refus propres de la famille sont l'étape 1 de la demande : rien n'y dépend du
 * point, qui n'est pas encore tiré.
 */
class WildCommand(private val runtime: ModRuntime) {

    companion object {

        const val NAME = "wild"

        /** L'alias, qui reçoit tout l'arbre (`wild.md` des specs, chapitre 7). */
        const val RTP_ALIAS = "rtp"

        private const val TIMED = "timed"
    }

    /** La famille s'enregistre sur son activation du démarrage (cahier des charges, chapitre 2.10), et son alias sur son propre interrupteur. */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val startup = runtime.startupRules(WildRules).settings
        if (!startup[WildRules.enabled]) return
        dispatcher.register(tree(NAME))
        if (startup[WildRules.aliases]) dispatcher.register(tree(RTP_ALIAS))
    }

    /** L'arbre sous le littéral [name] : il s'ouvre à qui a l'un des deux nœuds, `timed` exige le sien, et le littéral seul contrôle celui de `wild`. */
    private fun tree(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .requires(runtime.rights.requires(*WildNodes.ALL.toTypedArray()))
        .executes(::individual)
        .then(Commands.literal(TIMED).requires(runtime.rights.requires(WildNodes.TIMED)).executes { teleport(it, timed = true) })

    /** `/wild` seul, sous le nœud de `wild` : sans lui, la commande seule n'existe pas. */
    private fun individual(context: CommandContext<CommandSourceStack>): Int {
        if (!runtime.rights.has(context.source, WildNodes.WILD)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()
        return teleport(context, timed = false)
    }

    /**
     * Le tirage individuel, ou le tirage commun (`wild.md` des specs, chapitres 4 et 5). Le joueur qui rejoint un créneau ouvert reçoit son
     * point, que le déroulé contrôle comme une arrivée connue ; sinon le déroulé cherche un point sûr à son étape 4. Les étapes passées, et
     * avant le délai, `timed` ouvre le créneau de la dimension sur le point trouvé, ou dit celui qu'il rejoint : une commande refusée n'ouvre
     * rien. À l'arrivée, l'endroit quitté devient la position de retour, selon la source `wild` du joueur.
     *
     * @return 1 quand la téléportation est lancée ou faite, 0 pour un refus, de la famille ou du déroulé
     */
    private fun teleport(context: CommandContext<CommandSourceStack>, timed: Boolean): Int {
        val source = context.source
        val player = source.player ?: return refuse(source, CommonMessages.IN_GAME_ONLY.with())
        if (runtime.worldData?.wild?.isAvailable != true) return refuse(source, WildMessages.UNAVAILABLE.with())
        val ruleSet = runtime.ruleSet(source, WildRules)
        val settings = runtime.rules(WildRules).settings
        val charge = runtime.teleports.quotaCharge(source, player, WildRules, ruleSet)
        val dimension = Location.of(player).dimension
        val now = runtime.clock.instant()
        val window = if (timed) runtime.wildWindows.current(dimension, now) else null
        val arrival = if (window != null) {
            Arrival.At(Location.of(source.server, window.position) ?: error("A window opens in the dimension of the player, that the server carries"))
        } else {
            Arrival.Searched { SafePointSearcher.search(player, SafePointSearcher.zone(source.server, settings), Random.Default) }
        }
        val listener = object : TeleportListener {

            override fun onLaunched(destination: Location) {
                if (!timed) return
                if (window != null) {
                    runtime.messenger.tell(player, WildMessages.TIMED_JOINED.with(DurationWording.text(window.remainingSeconds(now))))
                } else {
                    val duration = settings[WildRules.timedWindow]
                    runtime.wildWindows.open(dimension, destination.toPosition(), now, duration)
                    runtime.messenger.tell(player, WildMessages.TIMED_OPENED.with(DurationWording.text(duration)))
                }
            }

            override fun onArrived(from: Location, to: Location) {
                runtime.recordReturn(player, BackSource.WILD, from)
                val point = to.toPosition()
                runtime.messenger.tell(player, WildMessages.ARRIVED.with(point.blockX, point.blockY, point.blockZ))
            }
        }
        val request = TeleportRequest(
            player, source, WildRules, ruleSet, arrival,
            familyRefusal = { WildRefusals.first(ruleSet, dimension, charge?.quota) { runtime.teleports.quotaWaitSeconds(charge) } },
            quotaCharge = charge,
            listener = listener,
        )
        return if (runtime.teleports.launch(request) == null) Command.SINGLE_SUCCESS else 0
    }

    private fun refuse(source: CommandSourceStack, message: Message): Int {
        runtime.messenger.reply(source, message)
        return 0
    }
}
