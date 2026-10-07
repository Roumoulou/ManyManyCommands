// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Un fichier de données qui ne tient que les plages de quota, en règles : `back.json`, `wild.json` (cahier des charges, chapitre 2.7.5). */
class QuotaOnlyDataRulesTest {

    private val rules = QuotaOnlyDataRules("back")

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 2)

    @Test
    fun `le fichier se nomme sous world, d'après sa famille`() {
        assertEquals("world/back.json", rules.fileName)
        assertEquals("back", rules.family)
    }

    @Test
    fun `une entrée porte son nom et sa plage, et une plage fautive se constate à son chemin`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenQuotaEntry("Steve", WrittenQuotaUsage("real", 1_759_665_600, -1))))

        val expected = FileFinding("players[$steve].quota.uses", null, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L)))
        assertEquals(listOf(expected), rules.findings(written))
    }

    @Test
    fun `un fichier valide donne la plage de chaque joueur, et s'écrit tel qu'il se relit`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenQuotaEntry("Steve", WrittenQuotaUsage("real", 1_759_665_600, 2))))

        val data = rules.data(written)

        assertEquals(emptyList<FileFinding>(), rules.findings(written))
        assertEquals(PlayerEntry("Steve", NoHoldings, window), data[steve])
        assertEquals(written, rules.written(data))
    }

    @Test
    fun `une entrée sans plage ne tient rien, et ne s'écrit pas`() {
        val data = QuotaOnlyData(mapOf(steve to PlayerEntry("Steve", NoHoldings)))

        assertEquals(WrittenData<WrittenQuotaEntry>(1, emptyMap()), rules.written(data))
        assertEquals(PlayerEntry("Steve", NoHoldings), WrittenQuotaEntry("Steve", null).value())
    }
}
