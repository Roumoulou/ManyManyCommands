// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.teleport.ArrivalSurvey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** La décision de point sûr (`wild.md` des specs, chapitre 3) : à la surface, sur un bloc solide, hors de l'eau et de la lave, et pour tout joueur. */
class SafePointRulesTest {

    /** Un point que le déroulé admettrait : libre, sans lave, le sol juste sous les pieds. */
    private val standing = ArrivalSurvey(isObstructed = false, isInLavaOrFire = false, supportDepth = 1)

    private fun survey(isBelowLogicalHeight: Boolean = true, isGroundSolid: Boolean = true, isInWater: Boolean = false, arrival: ArrivalSurvey = standing) =
        SurfaceSurvey(isBelowLogicalHeight, isGroundSolid, isInWater, arrival)

    @Test
    fun `un point à la surface, sur un bloc solide, hors de l'eau et de la lave, est sûr`() {
        assertTrue(SafePointRules.isSafe(survey()))
    }

    @Test
    fun `le toit du Nether, un sol qui ne porte pas, l'eau, la lave, un passage obstrué et le vide ne le sont pas`() {
        assertFalse(SafePointRules.isSafe(survey(isBelowLogicalHeight = false)), "Above the logical height of the dimension")
        assertFalse(SafePointRules.isSafe(survey(isGroundSolid = false)), "No solid ground")
        assertFalse(SafePointRules.isSafe(survey(isInWater = true)), "In water")
        assertFalse(SafePointRules.isSafe(survey(arrival = standing.copy(isInLavaOrFire = true))), "In lava or fire")
        assertFalse(SafePointRules.isSafe(survey(arrival = standing.copy(isObstructed = true))), "Obstructed")
        assertFalse(SafePointRules.isSafe(survey(arrival = standing.copy(supportDepth = null))), "Over the void")
    }

    @Test
    fun `dix colonnes se tirent avant de renoncer`() {
        assertEquals(10, SafePointRules.TRIES)
    }
}
