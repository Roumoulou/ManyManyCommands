// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Le cooldown, par famille et par joueur (cahier des charges, chapitres 2.5.1, étapes 3 et 6, et 2.5.4). */
class CooldownsTest {

    private val arrival: Instant = Instant.parse("2026-10-05T12:00:00Z")

    private val steve: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val alex: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val cooldowns = Cooldowns()

    private fun after(milliseconds: Long): Instant = arrival.plusMillis(milliseconds)

    @Test
    fun `tant que le joueur n'est pas arrivé, aucun cooldown ne court`() {
        assertEquals(0L, cooldowns.remainingSeconds(steve, "homes", cooldownSeconds = 10, now = arrival))
    }

    @Test
    fun `le cooldown démarre à l'arrivée et dure le temps réglé, son reste arrondi à la seconde au-dessus`() {
        cooldowns.start(steve, "homes", arrival)

        assertEquals(10L, cooldowns.remainingSeconds(steve, "homes", 10, arrival))
        assertEquals(7L, cooldowns.remainingSeconds(steve, "homes", 10, after(3_200)))
        assertEquals(1L, cooldowns.remainingSeconds(steve, "homes", 10, after(9_999)))
        assertEquals(0L, cooldowns.remainingSeconds(steve, "homes", 10, after(10_000)))
        assertEquals(0L, cooldowns.remainingSeconds(steve, "homes", 10, after(3_600_000)))
    }

    @Test
    fun `chaque famille tient le sien, rejoindre un home ne retarde pas un warp`() {
        cooldowns.start(steve, "homes", arrival)

        assertEquals(10L, cooldowns.remainingSeconds(steve, "homes", 10, arrival))
        assertEquals(0L, cooldowns.remainingSeconds(steve, "warps", 10, arrival))
    }

    @Test
    fun `chaque joueur a le sien`() {
        cooldowns.start(steve, "homes", arrival)

        assertEquals(0L, cooldowns.remainingSeconds(alex, "homes", 10, arrival))
    }

    @Test
    fun `il suit le joueur et non sa session, sous le même UUID après une reconnexion`() {
        cooldowns.start(steve, "homes", arrival)
        val reconnected = UUID.fromString(steve.toString())

        assertEquals(6L, cooldowns.remainingSeconds(reconnected, "homes", 10, after(4_000)))
    }

    @Test
    fun `un cooldown en cours se mesure aux nouvelles valeurs`() {
        cooldowns.start(steve, "homes", arrival)

        assertEquals(5L, cooldowns.remainingSeconds(steve, "homes", cooldownSeconds = 10, now = after(5_000)))
        assertEquals(0L, cooldowns.remainingSeconds(steve, "homes", cooldownSeconds = 3, now = after(5_000)), "A shorter cooldown has already elapsed")
        assertEquals(55L, cooldowns.remainingSeconds(steve, "homes", cooldownSeconds = 60, now = after(5_000)), "A longer cooldown still runs")
    }

    @Test
    fun `un cooldown de 0 n'attend jamais, et le joueur dispensé du cooldown non plus`() {
        cooldowns.start(steve, "homes", arrival)

        assertEquals(0L, cooldowns.remainingSeconds(steve, "homes", cooldownSeconds = 0, now = arrival))
        assertEquals(0L, cooldowns.remainingSeconds(steve, "homes", Dispensations(fromCooldown = true).cooldownSeconds(ruleCooldown = 10), arrival))
        assertEquals(10L, cooldowns.remainingSeconds(steve, "homes", Dispensations.NONE.cooldownSeconds(ruleCooldown = 10), arrival))
    }

    @Test
    fun `une nouvelle arrivée relance le cooldown`() {
        cooldowns.start(steve, "homes", arrival)
        cooldowns.start(steve, "homes", after(60_000))

        assertEquals(10L, cooldowns.remainingSeconds(steve, "homes", 10, after(60_000)))
    }
}
