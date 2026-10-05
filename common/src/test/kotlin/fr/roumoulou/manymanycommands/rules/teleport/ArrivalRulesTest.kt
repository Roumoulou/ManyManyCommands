// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** La décision d'arrivée dangereuse (cahier des charges, chapitres 2.5.1, étape 4, et 2.5.5). */
class ArrivalRulesTest {

    /** Une arrivée sûre : libre, hors de la lave et du feu, le sol juste sous les pieds. */
    private val safe = ArrivalSurvey(isObstructed = false, isInLavaOrFire = false, supportDepth = 1)

    private fun danger(survey: ArrivalSurvey): ArrivalDanger? = ArrivalRules.danger(isExempt = false) { survey }

    @Test
    fun `une arrivée libre, hors de la lave et du feu, sur le sol, est sûre`() {
        assertNull(danger(safe))
    }

    @Test
    fun `une arrivée obstruée est dangereuse`() {
        assertEquals(ArrivalDanger.OBSTRUCTED, danger(safe.copy(isObstructed = true)))
        assertEquals(CommonMessages.TELEPORT_ARRIVAL_OBSTRUCTED, ArrivalDanger.OBSTRUCTED.situation)
    }

    @Test
    fun `une arrivée dans la lave ou le feu est dangereuse`() {
        assertEquals(ArrivalDanger.LAVA_OR_FIRE, danger(safe.copy(isInLavaOrFire = true)))
        assertEquals(CommonMessages.TELEPORT_ARRIVAL_LAVA_OR_FIRE, ArrivalDanger.LAVA_OR_FIRE.situation)
    }

    @Test
    fun `une arrivée sans sol ni eau dans les 3 blocs en dessous est dangereuse`() {
        assertEquals(3, ArrivalRules.SUPPORT_DEPTH)
        assertEquals(ArrivalDanger.VOID, danger(safe.copy(supportDepth = null)))
        assertEquals(ArrivalDanger.VOID, danger(safe.copy(supportDepth = 4)))
        assertEquals(CommonMessages.TELEPORT_ARRIVAL_VOID, ArrivalDanger.VOID.situation)
    }

    @Test
    fun `le sol ou l'eau à 3 blocs ou moins sous l'arrivée la rendent sûre, et l'eau où le joueur arrive aussi`() {
        (0..3).forEach { depth -> assertNull(danger(safe.copy(supportDepth = depth)), "A support $depth block(s) below must be safe") }
    }

    @Test
    fun `une arrivée qui cumule les dangers dit le premier, dans l'ordre du cahier des charges`() {
        assertEquals(ArrivalDanger.OBSTRUCTED, danger(ArrivalSurvey(isObstructed = true, isInLavaOrFire = true, supportDepth = null)))
        assertEquals(ArrivalDanger.LAVA_OR_FIRE, danger(ArrivalSurvey(isObstructed = false, isInLavaOrFire = true, supportDepth = null)))
        assertEquals(listOf(ArrivalDanger.OBSTRUCTED, ArrivalDanger.LAVA_OR_FIRE, ArrivalDanger.VOID), ArrivalDanger.entries.toList())
    }

    @Test
    fun `le joueur en mode créatif ou spectateur est dispensé du contrôle de l'arrivée`() {
        listOf(safe.copy(isObstructed = true), safe.copy(isInLavaOrFire = true), safe.copy(supportDepth = null)).forEach { survey ->
            assertNull(ArrivalRules.danger(isExempt = true) { survey }, "An exempt player must not be refused for $survey")
        }
    }

    @Test
    fun `le monde n'est pas lu pour un joueur dispensé`() {
        var surveys = 0

        ArrivalRules.danger(isExempt = true) { safe.also { surveys++ } }
        assertEquals(0, surveys)

        ArrivalRules.danger(isExempt = false) { safe.also { surveys++ } }
        assertEquals(1, surveys)
    }
}
