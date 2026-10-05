// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable

/**
 * Ce qui refuse une téléportation avant son délai d'immobilité (cahier des charges, chapitre 2.5.1), avec le message que le joueur en reçoit. La
 * famille qui prévient un autre joueur y lit la cause.
 */
sealed interface TeleportRefusal {

    val message: Message

    /** Un refus propre à la famille : c'est elle qui en donne le message. */
    data class OfFamily(override val message: Message) : TeleportRefusal

    /** Une téléportation du joueur est déjà en attente, quelle que soit sa famille. */
    data object AlreadyPending : TeleportRefusal {

        override val message: Message get() = CommonMessages.TELEPORT_PENDING.with()
    }

    /** Le cooldown de la famille n'est pas écoulé. */
    data class OnCooldown(val remainingSeconds: Long) : TeleportRefusal {

        override val message: Message get() = CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(remainingSeconds))
    }

    /** L'arrivée est dangereuse. */
    data class DangerousArrival(val danger: ArrivalDanger) : TeleportRefusal {

        override val message: Message get() = danger.situation.with()
    }
}

/** Les étapes d'une téléportation qui précèdent son délai d'immobilité (cahier des charges, chapitres 2.5.1 et 2.5.6). */
object TeleportSteps {

    /**
     * Le premier refus, dans l'ordre des étapes 1 à 4, ou `null` quand rien ne refuse : le délai d'immobilité peut alors s'écouler.
     *
     * Une étape n'est consultée que si les précédentes passent. Lire l'arrivée dans le monde, ou tirer un point pour la téléportation aléatoire,
     * ne se fait donc pas pour une commande que son cooldown refuse.
     *
     * @param familyRefusal l'étape 1 : le premier des refus propres à la famille, dans l'ordre de sa fiche, dimension de départ et quota compris
     * @param hasPendingTeleport l'étape 2 : une téléportation du joueur est déjà en attente
     * @param cooldownRemainingSeconds l'étape 3 : ce qui reste du cooldown de la famille, 0 quand il est écoulé
     * @param arrivalRefusal l'étape 4 : le danger de l'arrivée, ou le refus de la famille dont la recherche d'un point sûr en tient lieu
     */
    fun firstRefusal(familyRefusal: () -> Message?, hasPendingTeleport: () -> Boolean, cooldownRemainingSeconds: () -> Long, arrivalRefusal: () -> TeleportRefusal?): TeleportRefusal? {
        familyRefusal()?.let { return TeleportRefusal.OfFamily(it) }
        if (hasPendingTeleport()) return TeleportRefusal.AlreadyPending
        val remaining = cooldownRemainingSeconds()
        if (remaining > 0) return TeleportRefusal.OnCooldown(remaining)
        return arrivalRefusal()
    }

    /** La commande de téléportation de la famille ne se lance pas depuis [dimension], que le jeu de règles du joueur interdit au départ. */
    fun isDepartureForbidden(table: RulesTable, ruleSet: RuleValues, dimension: String): Boolean = dimension in ruleSet[table.forbiddenDepartureDimensions]
}
