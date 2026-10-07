// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/** Le quota (cahier des charges, chapitre 2.5.9) : la plage et l'usage, sur l'heure réelle comme sur le temps de jeu, reçus en valeurs. */
class QuotaRulesTest {

    /** 3 usages par plage d'une heure, en heures réelles. */
    private val quota = Quota(uses = 3, periodSeconds = 3_600, clock = QuotaClock.REAL)

    /** L'instant du premier usage, en secondes sur l'horloge du quota. */
    private val first = 1_759_665_600L

    /** La plage qu'ouvre [first], après [uses] usages. */
    private fun window(uses: Long): QuotaUsage = QuotaUsage(QuotaClock.REAL, windowStart = first, uses = uses)

    // ── La plage et l'usage ──

    @Test
    fun `sans quota, la famille ne plafonne rien`() {
        assertEquals(0L, QuotaRules.waitSeconds(quota = null, usage = null, now = first))
        assertEquals(0L, QuotaRules.waitSeconds(quota = null, usage = window(uses = 99), now = first))
    }

    @Test
    fun `la plage s'ouvre au premier usage`() {
        assertEquals(0L, QuotaRules.waitSeconds(quota, usage = null, now = first))
        assertEquals(window(uses = 1), QuotaRules.afterUse(quota, usage = null, now = first))
    }

    @Test
    fun `les usages se comptent dans la plage ouverte, sans la déplacer`() {
        val second = QuotaRules.afterUse(quota, window(uses = 1), now = first + 600)
        val third = QuotaRules.afterUse(quota, second, now = first + 1_200)

        assertEquals(window(uses = 2), second)
        assertEquals(window(uses = 3), third)
    }

    @Test
    fun `tant que le nombre d'usages n'est pas atteint, le prochain est possible tout de suite`() {
        assertEquals(0L, QuotaRules.waitSeconds(quota, window(uses = 1), now = first + 10))
        assertEquals(0L, QuotaRules.waitSeconds(quota, window(uses = 2), now = first + 3_599))
    }

    @Test
    fun `le quota atteint dit dans combien de temps le prochain usage sera possible`() {
        assertEquals(3_600L, QuotaRules.waitSeconds(quota, window(uses = 3), now = first))
        assertEquals(2_700L, QuotaRules.waitSeconds(quota, window(uses = 3), now = first + 900))
        assertEquals(1L, QuotaRules.waitSeconds(quota, window(uses = 3), now = first + 3_599))
    }

    @Test
    fun `la plage dure le temps réglé, puis l'usage suivant en ouvre une nouvelle`() {
        val later = first + 3_600

        assertEquals(0L, QuotaRules.waitSeconds(quota, window(uses = 3), now = later))
        assertEquals(QuotaUsage(QuotaClock.REAL, windowStart = later, uses = 1), QuotaRules.afterUse(quota, window(uses = 3), now = later))
    }

    @Test
    fun `un usage se compte à l'arrivée, une téléportation annulée ne consomme rien`() {
        val usage = window(uses = 2)

        QuotaRules.waitSeconds(quota, usage, now = first + 5)

        assertEquals(window(uses = 2), usage, "Asking whether a use is possible counts nothing: only afterUse does, and it is called at the arrival")
        assertEquals(0L, QuotaRules.waitSeconds(quota, usage, now = first + 30))
    }

    // ── Les deux horloges ──

    @Test
    fun `sur le temps de jeu, la plage se mesure en secondes de jeu, reçues comme l'heure réelle`() {
        val playtime = Quota(uses = 1, periodSeconds = 600, clock = QuotaClock.PLAYTIME)
        val played = 7_200L

        val usage = QuotaRules.afterUse(playtime, usage = null, now = played)

        assertEquals(QuotaUsage(QuotaClock.PLAYTIME, windowStart = played, uses = 1), usage)
        assertEquals(480L, QuotaRules.waitSeconds(playtime, usage, now = played + 120), "Ten minutes of play, two of them played")
        assertEquals(0L, QuotaRules.waitSeconds(playtime, usage, now = played + 600))
    }

    @Test
    fun `une plage ouverte sur une autre horloge que celle du quota est finie`() {
        val playtime = quota.copy(clock = QuotaClock.PLAYTIME)

        assertEquals(0L, QuotaRules.waitSeconds(playtime, window(uses = 3), now = 50))
        assertEquals(QuotaUsage(QuotaClock.PLAYTIME, windowStart = 50, uses = 1), QuotaRules.afterUse(playtime, window(uses = 3), now = 50))
    }

    // ── Les règles rechargées, les dispenses ──

    @Test
    fun `une plage en cours se mesure au quota en vigueur`() {
        val now = first + 900

        assertEquals(0L, QuotaRules.waitSeconds(quota.copy(uses = 5), window(uses = 3), now), "More uses allowed: the next one is possible")
        assertEquals(2_700L, QuotaRules.waitSeconds(quota.copy(uses = 1), window(uses = 3), now), "Fewer uses allowed: the window must end first")
        assertEquals(0L, QuotaRules.waitSeconds(quota.copy(periodSeconds = 600), window(uses = 3), now), "A shorter window has already ended")
        assertEquals(6_300L, QuotaRules.waitSeconds(quota.copy(periodSeconds = 7_200), window(uses = 3), now), "A longer window still lasts")
    }

    @Test
    fun `le joueur dispensé des quotas n'attend pas, et son usage ne se compte pas`() {
        assertNull(Dispensations(fromQuota = true).quota(quota))
        assertEquals(0L, QuotaRules.waitSeconds(Dispensations(fromQuota = true).quota(quota), window(uses = 3), now = first))
        assertEquals(quota, Dispensations.NONE.quota(quota))
    }

    // ── Les plages des joueurs ──

    @Test
    fun `en mémoire, chaque famille tient ses plages, pour chaque joueur`() {
        val steve = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val alex = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val usages = QuotaUsages()
        fun count(player: UUID, now: Long) = usages.record(player, "?", "wild", QuotaRules.afterUse(quota, usages.usage(player, "wild"), now))

        count(steve, now = first)
        count(steve, now = first + 60)
        count(alex, now = first + 120)

        assertEquals(window(uses = 2), usages.usage(steve, "wild"))
        assertEquals(QuotaUsage(QuotaClock.REAL, windowStart = first + 120, uses = 1), usages.usage(alex, "wild"))
        assertNull(usages.usage(steve, "homes"))
    }
}
