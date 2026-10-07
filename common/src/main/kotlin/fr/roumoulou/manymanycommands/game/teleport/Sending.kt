// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import fr.roumoulou.manymanycommands.rules.teleport.ArrivalDanger
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalRules
import net.minecraft.server.level.ServerPlayer

/**
 * L'envoi par un administrateur (cahier des charges, chapitre 2.5.10) : immédiat, sans le déroulé, sans délai, cooldown, quota ni règle de
 * dimension. Il garde le refus d'une arrivée dangereuse et le départ seul.
 */
object Sending {

    /**
     * Envoie [players] à [destination]. L'arrivée se contrôle une fois pour tous, sur le premier joueur qui n'en est pas dispensé ; dangereuse,
     * personne ne part. [onSent] reçoit chaque joueur parti, avec l'endroit qu'il a quitté.
     *
     * @return le danger de l'arrivée, ou `null` quand les joueurs sont partis
     */
    fun send(players: Collection<ServerPlayer>, destination: Location, onSent: (ServerPlayer, from: Location) -> Unit): ArrivalDanger? {
        val surveyed = players.firstOrNull { !ArrivalSurveyor.isExempt(it) }
        if (surveyed != null) ArrivalRules.danger(isExempt = false) { ArrivalSurveyor.survey(surveyed, destination) }?.let { return it }
        players.forEach { player ->
            val from = Location.of(player)
            if (Departures.leave(player, destination)) onSent(player, from)
        }
        return null
    }
}
