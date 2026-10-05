// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.Situation

/**
 * Ce qui annule une téléportation en attente (cahier des charges, chapitre 2.5.3).
 *
 * @property situation le message qui en dit la cause au joueur, `null` quand il n'est plus là pour le lire
 */
enum class CancellationCause(val situation: Situation?) {

    /** Le joueur s'est éloigné de plus d'un bloc de son point de départ. */
    MOVED(CommonMessages.TELEPORT_CANCELLED_MOVED),

    DAMAGED(CommonMessages.TELEPORT_CANCELLED_DAMAGED),

    DIED(CommonMessages.TELEPORT_CANCELLED_DIED),

    CHANGED_DIMENSION(CommonMessages.TELEPORT_CANCELLED_CHANGED_DIMENSION),

    DISCONNECTED(null);

    companion object {

        /** Le joueur peut s'écarter d'un bloc de son point de départ, en ligne droite, sans rien annuler. */
        private const val ALLOWED_DRIFT = 1.0

        /**
         * Ce que la position du joueur annule, `null` quand elle n'annule rien. Seule la position compte : tourner la tête n'annule rien. La
         * dimension se juge d'abord, parce que des coordonnées ne se comparent que dans une même dimension.
         *
         * @param departure le point où le joueur a lancé sa commande
         * @param current le point où il se trouve
         */
        fun ofPosition(departure: Spot, current: Spot): CancellationCause? = when {
            current.dimension != departure.dimension -> CHANGED_DIMENSION
            current.distanceSquaredTo(departure) > ALLOWED_DRIFT * ALLOWED_DRIFT -> MOVED
            else -> null
        }
    }
}
