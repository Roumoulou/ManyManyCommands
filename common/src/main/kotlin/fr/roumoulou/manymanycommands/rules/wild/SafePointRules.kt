// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.teleport.ArrivalRules
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalSurvey

/**
 * Ce que le monde dit du point tiré sur une colonne, à sa surface, lu par la couche `game`.
 *
 * @property isBelowLogicalHeight la surface est sous la hauteur logique de la dimension : le toit du Nether n'en est pas une
 * @property isGroundSolid le bloc sous les pieds porte le joueur, et n'est pas un fluide
 * @property isInWater les pieds ou le bloc dessous sont dans l'eau
 * @property arrival ce que le déroulé en dirait : obstrué, dans la lave ou le feu, sans sol
 */
data class SurfaceSurvey(val isBelowLogicalHeight: Boolean, val isGroundSolid: Boolean, val isInWater: Boolean, val arrival: ArrivalSurvey)

/** La décision de point sûr (`wild.md` des specs, chapitre 3) : à la surface, sur un bloc solide, hors de l'eau et de la lave. */
object SafePointRules {

    /** Le nombre de colonnes tirées avant de renoncer. */
    const val TRIES = 10

    /** Le point convient. Le mode de jeu ne dispense de rien : le point se cherche sûr pour tout joueur. */
    fun isSafe(survey: SurfaceSurvey): Boolean =
        survey.isBelowLogicalHeight && survey.isGroundSolid && !survey.isInWater && ArrivalRules.danger(isExempt = false) { survey.arrival } == null
}
