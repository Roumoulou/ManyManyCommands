// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Les causes d'annulation d'une téléportation en attente (cahier des charges, chapitre 2.5.3), et leurs messages du chapitre 2.5.8. */
class CancellationCauseTest {

    private val departure = Spot("minecraft:overworld", 100.5, 64.0, -20.5)

    private fun movedBy(dx: Double = 0.0, dy: Double = 0.0, dz: Double = 0.0): Spot = departure.copy(x = departure.x + dx, y = departure.y + dy, z = departure.z + dz)

    @Test
    fun `s'éloigner de plus d'un bloc du point de départ annule, en ligne droite dans tous les sens`() {
        assertEquals(CancellationCause.MOVED, CancellationCause.ofPosition(departure, movedBy(dx = 1.01)))
        assertEquals(CancellationCause.MOVED, CancellationCause.ofPosition(departure, movedBy(dz = -1.5)))
        assertEquals(CancellationCause.MOVED, CancellationCause.ofPosition(departure, movedBy(dy = 1.2)), "Height counts: the distance is a straight line")
        assertEquals(CancellationCause.MOVED, CancellationCause.ofPosition(departure, movedBy(dx = 0.8, dz = 0.8)), "0.8 on two axes is more than one block away")
    }

    @Test
    fun `rester à un bloc ou moins du point de départ n'annule rien`() {
        assertNull(CancellationCause.ofPosition(departure, movedBy(dx = 1.0)))
        assertNull(CancellationCause.ofPosition(departure, movedBy(dx = 0.7, dz = 0.7)))
        assertNull(CancellationCause.ofPosition(departure, movedBy(dx = -0.3, dy = 0.4, dz = 0.2)))
    }

    @Test
    fun `tourner la tête n'annule rien, seule la position compte`() {
        assertNull(CancellationCause.ofPosition(departure, departure.copy()))
    }

    @Test
    fun `changer de dimension annule, aux mêmes coordonnées comme ailleurs`() {
        assertEquals(CancellationCause.CHANGED_DIMENSION, CancellationCause.ofPosition(departure, departure.copy(dimension = "minecraft:the_nether")))
        assertEquals(CancellationCause.CHANGED_DIMENSION, CancellationCause.ofPosition(departure, Spot("minecraft:the_end", 0.0, 80.0, 0.0)), "The dimension is judged before the distance")
    }

    @Test
    fun `chaque cause a son message, qui la dit au joueur`() {
        assertEquals(CommonMessages.TELEPORT_CANCELLED_MOVED, CancellationCause.MOVED.situation)
        assertEquals(CommonMessages.TELEPORT_CANCELLED_DAMAGED, CancellationCause.DAMAGED.situation)
        assertEquals(CommonMessages.TELEPORT_CANCELLED_DIED, CancellationCause.DIED.situation)
        assertEquals(CommonMessages.TELEPORT_CANCELLED_CHANGED_DIMENSION, CancellationCause.CHANGED_DIMENSION.situation)
        assertEquals(setOf(MessageType.REFUSAL), CancellationCause.entries.mapNotNull { it.situation?.type }.toSet())
    }

    @Test
    fun `la déconnexion annule sans message, le joueur n'étant plus là pour le lire`() {
        assertNull(CancellationCause.DISCONNECTED.situation)
        assertEquals(4, CancellationCause.entries.count { it.situation != null })
    }
}
