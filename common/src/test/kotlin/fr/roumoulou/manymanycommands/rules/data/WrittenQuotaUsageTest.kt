// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** La plage d'un quota telle qu'un fichier de données l'écrit : ses trois clés obligatoires (cahier des charges, chapitre 2.7.5). */
class WrittenQuotaUsageTest {

    private val path = "players[steve].quota"

    private val none = emptyList<FileFinding>()

    @Test
    fun `une plage entière s'admet et se lit, sur l'heure réelle comme sur le temps de jeu`() {
        val real = WrittenQuotaUsage("real", 1_759_665_600, 2)
        val playtime = WrittenQuotaUsage("playtime", 0, 0)

        assertEquals(none, real.findings(path))
        assertEquals(none, playtime.findings(path))
        assertEquals(QuotaUsage(QuotaClock.REAL, 1_759_665_600, 2), real.value())
        assertEquals(QuotaUsage(QuotaClock.PLAYTIME, 0, 0), playtime.value())
    }

    @Test
    fun `chaque clé qui manque se constate au chemin de la plage`() {
        val expected = listOf("clock", "window-start", "uses").map { FileFinding(path, null, CommonMessages.FINDING_MISSING_KEY.with(it)) }

        assertEquals(expected, WrittenQuotaUsage(null, null, null).findings(path))
    }

    @Test
    fun `une horloge inconnue et un compte négatif se refusent, chacun à son chemin`() {
        val findings = WrittenQuotaUsage("lunar", -5, -1).findings(path)

        val expected = listOf(
            FileFinding("$path.clock", null, CommonMessages.FINDING_NOT_ALLOWED.with("lunar", "real, playtime")),
            FileFinding("$path.window-start", null, CommonMessages.FINDING_NOT_ALLOWED.with(-5L, CommonMessages.ALLOWED_AT_LEAST.with(0L))),
            FileFinding("$path.uses", null, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L))),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `une plage s'écrit telle qu'elle se relit`() {
        val usage = QuotaUsage(QuotaClock.PLAYTIME, 1_200, 3)

        assertEquals(WrittenQuotaUsage("playtime", 1_200, 3), WrittenQuotaUsage.of(usage))
        assertEquals(usage, WrittenQuotaUsage.of(usage).value())
    }
}
