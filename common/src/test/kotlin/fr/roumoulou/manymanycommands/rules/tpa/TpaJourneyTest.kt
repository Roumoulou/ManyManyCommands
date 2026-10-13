// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalDanger
import fr.roumoulou.manymanycommands.rules.teleport.CancellationCause
import fr.roumoulou.manymanycommands.rules.teleport.TeleportRefusal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Le voyage d'une demande acceptée en règles (`tpa.md` des specs, chapitres 3.2 et 5) : le refus de la dimension de départ, et chaque cause que l'autre joueur apprend. */
class TpaJourneyTest {

    @Test
    fun `la dimension de départ du voyageur se contrôle à l'acceptation, dans son jeu de règles`() {
        val nether = "minecraft:the_nether"
        val noNether = TpaRules.values(WrittenRules(sets = mapOf("default" to mapOf(TpaRules.forbiddenDepartureDimensions.writes(listOf(nether)))))).default

        assertEquals(TpaMessages.DEPARTURE_FORBIDDEN.with(nether), TpaJourney.departureRefusal(noNether, nether))
        assertNull(TpaJourney.departureRefusal(noNether, "minecraft:overworld"))
        assertNull(TpaJourney.departureRefusal(TpaRules.shipped.default, nether), "Shipped, every dimension is a departure")
    }

    @Test
    fun `chaque refus du déroulé a sa cause, à la troisième personne`() {
        assertEquals(TpaMessages.CAUSE_DEPARTURE_FORBIDDEN.with(), TpaJourney.cause(TeleportRefusal.OfFamily(TpaMessages.DEPARTURE_FORBIDDEN.with("minecraft:the_end"))))
        assertEquals(TpaMessages.CAUSE_PENDING.with(), TpaJourney.cause(TeleportRefusal.AlreadyPending))
        assertEquals(TpaMessages.CAUSE_COOLDOWN.with(), TpaJourney.cause(TeleportRefusal.OnCooldown(5)))
        assertEquals(TpaMessages.CAUSE_OBSTRUCTED.with(), TpaJourney.cause(TeleportRefusal.DangerousArrival(ArrivalDanger.OBSTRUCTED)))
        assertEquals(TpaMessages.CAUSE_LAVA_OR_FIRE.with(), TpaJourney.cause(TeleportRefusal.DangerousArrival(ArrivalDanger.LAVA_OR_FIRE)))
        assertEquals(TpaMessages.CAUSE_VOID.with(), TpaJourney.cause(TeleportRefusal.DangerousArrival(ArrivalDanger.VOID)))
    }

    @Test
    fun `chaque annulation a sa cause, la déconnexion comprise, et le message de l'autre joueur nomme le voyageur`() {
        assertEquals(TpaMessages.CAUSE_MOVED.with(), TpaJourney.cause(CancellationCause.MOVED))
        assertEquals(TpaMessages.CAUSE_DAMAGED.with(), TpaJourney.cause(CancellationCause.DAMAGED))
        assertEquals(TpaMessages.CAUSE_DIED.with(), TpaJourney.cause(CancellationCause.DIED))
        assertEquals(TpaMessages.CAUSE_CHANGED_DIMENSION.with(), TpaJourney.cause(CancellationCause.CHANGED_DIMENSION))
        assertEquals(TpaMessages.CAUSE_DISCONNECTED.with(), TpaJourney.cause(CancellationCause.DISCONNECTED))

        val notDone = TpaJourney.notDone("Steve", TpaJourney.cause(CancellationCause.DIED))
        assertEquals(TpaMessages.NOT_DONE.with("Steve", TpaMessages.CAUSE_DIED.with()), notDone)
        assertEquals(MessageType.DISPLAY, notDone.type)
    }
}
