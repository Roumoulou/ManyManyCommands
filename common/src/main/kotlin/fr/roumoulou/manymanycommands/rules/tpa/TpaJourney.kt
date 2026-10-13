// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalDanger
import fr.roumoulou.manymanycommands.rules.teleport.CancellationCause
import fr.roumoulou.manymanycommands.rules.teleport.TeleportRefusal
import fr.roumoulou.manymanycommands.rules.teleport.TeleportSteps

/**
 * Le voyage d'une demande acceptée (`tpa.md` des specs, chapitre 3.2), en règles : le refus propre de la famille à l'acceptation, et la cause
 * que l'autre joueur apprend quand le déroulé refuse ou que la téléportation s'annule (chapitre 5).
 */
object TpaJourney {

    /** L'étape 1 du déroulé pour le voyageur : sa dimension de départ, que son jeu de règles interdit. Le quota, lui, s'est contrôlé à l'envoi. */
    fun departureRefusal(ruleSet: RuleValues, departure: String): Message? =
        if (TeleportSteps.isDepartureForbidden(TpaRules, ruleSet, departure)) TpaMessages.DEPARTURE_FORBIDDEN.with(departure) else null

    /** La cause d'un refus du déroulé, à la troisième personne. Le seul refus propre de la famille est la dimension de départ. */
    fun cause(refusal: TeleportRefusal): TranslatableText = when (refusal) {
        is TeleportRefusal.OfFamily -> TpaMessages.CAUSE_DEPARTURE_FORBIDDEN.with()
        TeleportRefusal.AlreadyPending -> TpaMessages.CAUSE_PENDING.with()
        is TeleportRefusal.OnCooldown -> TpaMessages.CAUSE_COOLDOWN.with()
        is TeleportRefusal.DangerousArrival -> when (refusal.danger) {
            ArrivalDanger.OBSTRUCTED -> TpaMessages.CAUSE_OBSTRUCTED.with()
            ArrivalDanger.LAVA_OR_FIRE -> TpaMessages.CAUSE_LAVA_OR_FIRE.with()
            ArrivalDanger.VOID -> TpaMessages.CAUSE_VOID.with()
        }
    }

    /** La cause d'une annulation, à la troisième personne. */
    fun cause(cancellation: CancellationCause): TranslatableText = when (cancellation) {
        CancellationCause.MOVED -> TpaMessages.CAUSE_MOVED.with()
        CancellationCause.DAMAGED -> TpaMessages.CAUSE_DAMAGED.with()
        CancellationCause.DIED -> TpaMessages.CAUSE_DIED.with()
        CancellationCause.CHANGED_DIMENSION -> TpaMessages.CAUSE_CHANGED_DIMENSION.with()
        CancellationCause.DISCONNECTED -> TpaMessages.CAUSE_DISCONNECTED.with()
    }

    /** Le message de l'autre joueur : la téléportation de [travelerName] n'a pas eu lieu, pour [cause]. */
    fun notDone(travelerName: String, cause: TranslatableText): Message = TpaMessages.NOT_DONE.with(travelerName, cause)
}
