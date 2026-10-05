// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.teleport.CancellationCause
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

/**
 * Ce que la commande d'une famille demande au déroulé d'une téléportation (cahier des charges, chapitre 2.5), pour un joueur.
 *
 * Tout ce qu'une fiche change au déroulé passe par ici, sans toucher au moteur : les refus propres à la famille, le jeu de règles dont elle
 * prend le délai et le cooldown, sa propre recherche du point d'arrivée, le joueur dont le quota se compte, et ce qu'elle fait à l'arrivée ou
 * à l'annulation.
 *
 * @property player le joueur qui voyage
 * @property source la source dont les droits donnent les dispenses, et qui reçoit le refus : celle de la commande, ou celle du voyageur quand un
 * autre joueur lance sa téléportation
 * @property table la famille : son cooldown est le sien
 * @property ruleSet le jeu de règles où se lisent le délai et le cooldown. Une téléportation en attente garde ses valeurs du lancement
 * @property arrival où mène la téléportation, et qui en contrôle l'arrivée
 * @property familyRefusal l'étape 1 : le premier des refus propres à la famille, dans l'ordre de sa fiche, ou `null`. La dimension de départ
 * et le quota en font partie
 * @property quotaCharge l'usage à compter à l'arrivée, `null` quand rien ne se compte
 * @property listener ce que la famille fait de l'issue
 */
class TeleportRequest(
    val player: ServerPlayer,
    val source: CommandSourceStack,
    val table: RulesTable,
    val ruleSet: RuleValues,
    val arrival: Arrival,
    val familyRefusal: () -> Message? = { null },
    val quotaCharge: QuotaCharge? = null,
    val listener: TeleportListener = TeleportListener.NONE,
)

/** Où mène une téléportation, et qui contrôle son arrivée à l'étape 4. */
sealed interface Arrival {

    /** Un point connu d'avance : le déroulé contrôle qu'il n'est pas dangereux (cahier des charges, chapitre 2.5.5). */
    data class At(val destination: Location) : Arrival

    /**
     * Un point que la famille cherche elle-même, sûr par construction : sa recherche tient lieu de contrôle de l'arrivée. Elle ne se lance que si
     * les étapes 1 à 3 passent.
     */
    class Searched(val search: () -> SearchOutcome) : Arrival
}

/** L'issue de la recherche d'un point d'arrivée par une famille. */
sealed interface SearchOutcome {

    data class Found(val destination: Location) : SearchOutcome

    /** Aucun point : le refus est celui de la famille. */
    data class Refused(val message: Message) : SearchOutcome
}

/**
 * L'usage d'un quota à compter à l'arrivée (cahier des charges, chapitre 2.5.9). La famille le contrôle à son étape 1 et le moteur le compte :
 * l'un et l'autre parlent ainsi du même joueur et du même quota.
 *
 * @property holder le joueur dont le quota se compte, qui n'est pas toujours celui qui voyage
 */
class QuotaCharge(val holder: ServerPlayer, val family: String, val quota: Quota)

/** Ce qu'une famille fait de l'issue d'une téléportation que le déroulé a acceptée. */
interface TeleportListener {

    companion object {

        val NONE = object : TeleportListener {}
    }

    /** Le joueur est arrivé : il a quitté [from] pour [to]. */
    fun onArrived(from: Location, to: Location) {}

    /** La téléportation en attente est annulée. Le joueur en a reçu le message, sauf s'il s'est déconnecté. */
    fun onCancelled(cause: CancellationCause) {}
}
