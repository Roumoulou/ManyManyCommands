// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.common.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/** Les créneaux du tirage commun (`wild.md` des specs, chapitre 4) : un par dimension, ouvert pour sa durée, oublié à sa fin, ce qui en reste. */
class TimedWindowsTest {

    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    private val overworld = "minecraft:overworld"

    private val point = Position(overworld, 100.5, 64.0, -20.5, 0f, 0f)

    private val other = Position(overworld, -300.5, 70.0, 12.5, 0f, 0f)

    @Test
    fun `aucun créneau avant le premier tirage, puis celui ouvert, tant qu'il dure`() {
        val windows = TimedWindows()
        assertNull(windows.current(overworld, noon))

        val window = windows.open(overworld, point, noon, durationSeconds = 10)

        assertEquals(TimedWindow(point, noon, noon.plusSeconds(10)), window)
        assertEquals(window, windows.current(overworld, noon))
        assertEquals(window, windows.current(overworld, noon.plusSeconds(9)))
        assertNull(windows.current(overworld, noon.plusSeconds(10)), "The window is over at its end")
    }

    @Test
    fun `chaque dimension a son créneau, et un nouveau créneau remplace le précédent`() {
        val windows = TimedWindows()
        val first = windows.open(overworld, point, noon, 10)

        assertNull(windows.current("minecraft:the_nether", noon), "Another dimension has no window")
        val second = windows.open(overworld, other, noon.plusSeconds(10), 10)

        assertEquals(second, windows.current(overworld, noon.plusSeconds(15)))
        assertEquals(first.closesAt, second.opensAt)
    }

    @Test
    fun `le créneau dit sa durée et ce qui en reste, la seconde entamée comptée, et rien une fois fini`() {
        val window = TimedWindow(point, noon, noon.plusSeconds(10))

        assertEquals(10L, window.durationSeconds)
        assertEquals(10L, window.remainingSeconds(noon))
        assertEquals(6L, window.remainingSeconds(noon.plusSeconds(4)))
        assertEquals(6L, window.remainingSeconds(noon.plusMillis(4_500)))
        assertEquals(0L, window.remainingSeconds(noon.plusSeconds(10)))
        assertEquals(0L, window.remainingSeconds(noon.plusSeconds(11)))
    }
}
