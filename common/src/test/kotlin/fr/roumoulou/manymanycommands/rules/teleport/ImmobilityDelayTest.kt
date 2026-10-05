// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.Placement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/** Le délai d'immobilité et son compte à rebours (cahier des charges, chapitres 2.5.1, étape 5, et 2.5.2). */
class ImmobilityDelayTest {

    private val start: Instant = Instant.parse("2026-10-05T12:00:00Z")

    private fun after(milliseconds: Long): Instant = start.plusMillis(milliseconds)

    @Test
    fun `le compte à rebours part du délai entier, s'affiche une fois par seconde, puis le joueur arrive`() {
        val delay = ImmobilityDelay(start, delaySeconds = 3)

        val steps = listOf(0L, 400, 999, 1_000, 1_900, 2_000, 2_999, 3_000).map { delay.step(after(it)) }

        val expected = listOf(DelayStep.Show(3), DelayStep.Wait, DelayStep.Wait, DelayStep.Show(2), DelayStep.Wait, DelayStep.Show(1), DelayStep.Wait, DelayStep.Arrive)
        assertEquals(expected, steps)
    }

    @Test
    fun `le compte à rebours est un message de la barre d'action, qui dit la durée restante`() {
        val message = DelayStep.Show(3).message

        assertEquals(CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3)), message)
        assertEquals(MessageType.COUNTDOWN, message.type)
        assertEquals(Placement.ACTION_BAR, message.type.placement)
    }

    @Test
    fun `un délai de 0 téléporte tout de suite`() {
        assertEquals(DelayStep.Arrive, ImmobilityDelay(start, delaySeconds = 0).step(start))
    }

    @Test
    fun `le joueur dispensé du délai est téléporté tout de suite`() {
        val delaySeconds = Dispensations(fromDelay = true).delaySeconds(ruleDelay = 3)

        assertEquals(0L, delaySeconds)
        assertEquals(DelayStep.Arrive, ImmobilityDelay(start, delaySeconds).step(start))
        assertEquals(3L, Dispensations.NONE.delaySeconds(ruleDelay = 3))
    }

    @Test
    fun `un instant qui saute des secondes affiche celle où il tombe`() {
        val delay = ImmobilityDelay(start, delaySeconds = 5)

        assertEquals(DelayStep.Show(5), delay.step(start))
        assertEquals(DelayStep.Show(2), delay.step(after(3_500)))
        assertEquals(DelayStep.Wait, delay.step(after(3_900)))
    }

    @Test
    fun `passé le délai, chaque instant demande l'arrivée`() {
        val delay = ImmobilityDelay(start, delaySeconds = 3)

        assertEquals(DelayStep.Arrive, delay.step(after(3_000)))
        assertEquals(DelayStep.Arrive, delay.step(after(60_000)))
    }

    @Test
    fun `le délai finit au lancement plus sa durée, celle du lancement`() {
        assertEquals(after(3_000), ImmobilityDelay(start, delaySeconds = 3).endsAt)
    }
}
