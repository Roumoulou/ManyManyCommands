// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaUsage
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** `tpa.json` en règles : la réception fermée et la plage de chaque joueur, ses constats, ce qui s'écrit (cahier des charges, chapitre 2.7.5). */
class TpaDataRulesTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 2)

    private val writtenWindow = WrittenQuotaUsage("real", 1_759_665_600, 2)

    @Test
    fun `le fichier se nomme world tpa json`() {
        assertEquals("world/tpa.json", TpaDataRules.fileName)
    }

    @Test
    fun `une entrée porte son nom, la réception fermée et sa plage, et une plage fautive se constate à son chemin`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenTpaEntry("Steve", true, WrittenQuotaUsage("real", 1_759_665_600, -1))))

        val expected = FileFinding("players[$steve].quota.uses", null, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L)))
        assertEquals(listOf(expected), TpaDataRules.findings(written))
    }

    @Test
    fun `un fichier valide donne la réception et la plage de chaque joueur, et s'écrit tel qu'il se relit`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenTpaEntry("Steve", true, writtenWindow)))

        val data = TpaDataRules.data(written)

        assertEquals(emptyList<FileFinding>(), TpaDataRules.findings(written))
        assertEquals(PlayerEntry("Steve", TpaHoldings(requestsClosed = true), window), data[steve])
        assertEquals(written, TpaDataRules.written(data))
    }

    @Test
    fun `requests-closed à false vaut ouverte, et ne se récrit pas`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenTpaEntry("Steve", false, writtenWindow)))

        val data = TpaDataRules.data(written)

        assertEquals(PlayerEntry("Steve", TpaHoldings(), window), data[steve])
        assertEquals(WrittenData(players = mapOf(steve.toString() to WrittenTpaEntry("Steve", null, writtenWindow))), TpaDataRules.written(data))
    }

    @Test
    fun `une entrée ouverte sans plage ne tient rien, et ne s'écrit pas`() {
        val data = TpaData(mapOf(steve to PlayerEntry("Steve", TpaHoldings())))

        assertEquals(WrittenData<WrittenTpaEntry>(1, emptyMap()), TpaDataRules.written(data))
        assertEquals(PlayerEntry("Steve", TpaHoldings()), WrittenTpaEntry("Steve", null, null).value())
        assertTrue(TpaHoldings().isEmpty)
        assertFalse(TpaHoldings(requestsClosed = true).isEmpty, "A closed reception is kept")
    }

    @Test
    fun `tpatoggle ferme la réception ouverte, et rouvre la fermée`() {
        assertEquals(TpaHoldings(requestsClosed = true), TpaHoldings().toggled())
        assertEquals(TpaHoldings(), TpaHoldings(requestsClosed = true).toggled())
    }
}
