// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.Situation

/**
 * Ce que le monde dit d'un point d'arrivée, lu par la couche `game`.
 *
 * @property isObstructed le joueur y serait pris dans un bloc, et étoufferait
 * @property isInLavaOrFire le joueur y toucherait la lave ou le feu
 * @property supportDepth le rang du premier bloc qui porte ou reçoit le joueur, sol ou eau : 0 pour le bloc où sont ses pieds, 1 pour celui juste
 * en dessous. `null` quand aucun n'a été trouvé jusqu'à [ArrivalRules.SUPPORT_DEPTH]
 */
data class ArrivalSurvey(val isObstructed: Boolean, val isInLavaOrFire: Boolean, val supportDepth: Int?)

/** Ce qui rend une arrivée dangereuse (cahier des charges, chapitre 2.5.5), dans l'ordre où la commande le dit. */
enum class ArrivalDanger(val situation: Situation) {

    OBSTRUCTED(CommonMessages.TELEPORT_ARRIVAL_OBSTRUCTED),

    LAVA_OR_FIRE(CommonMessages.TELEPORT_ARRIVAL_LAVA_OR_FIRE),

    /** Ni sol ni eau sous le point d'arrivée. */
    VOID(CommonMessages.TELEPORT_ARRIVAL_VOID),
}

/** La décision d'arrivée dangereuse (cahier des charges, chapitre 2.5.5). */
object ArrivalRules {

    /** Le sol ou l'eau se cherchent dans les 3 blocs sous le point d'arrivée. */
    const val SUPPORT_DEPTH = 3

    /**
     * Le danger de l'arrivée, `null` quand elle est sûre. Une arrivée qui en cumule plusieurs dit le premier.
     *
     * @param isExempt le joueur est en mode créatif ou spectateur, qui l'en dispensent
     * @param survey la lecture du monde, qui ne se fait pas pour un joueur dispensé
     */
    fun danger(isExempt: Boolean, survey: () -> ArrivalSurvey): ArrivalDanger? = if (isExempt) null else danger(survey())

    private fun danger(survey: ArrivalSurvey): ArrivalDanger? = when {
        survey.isObstructed -> ArrivalDanger.OBSTRUCTED
        survey.isInLavaOrFire -> ArrivalDanger.LAVA_OR_FIRE
        survey.supportDepth == null || survey.supportDepth > SUPPORT_DEPTH -> ArrivalDanger.VOID
        else -> null
    }
}
