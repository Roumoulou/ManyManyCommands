// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.data.NoHoldings
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyData
import fr.roumoulou.manymanycommands.rules.data.WrittenPosition
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaUsage
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** `warps.json` en règles : ce qui rend le fichier invalide, et ce qu'un fichier valide donne (cahier des charges, chapitre 2.7.5). */
class WarpsDataRulesTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val base = WrittenPosition("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val none = emptyList<FileFinding>()

    private fun findings(warps: Map<String, WrittenWarp> = emptyMap(), players: Map<String, WrittenQuotaEntry> = emptyMap()): List<FileFinding> = WarpsDataRules.findings(WrittenWarpsData(warps = warps, players = players))

    @Test
    fun `le fichier se nomme world warps json`() {
        assertEquals("world/warps.json", WarpsDataRules.fileName)
    }

    @Test
    fun `un fichier vide est valide, et ne donne ni warp ni joueur`() {
        assertEquals(none, findings())
        assertEquals(WarpsData(), WarpsDataRules.data(WrittenWarpsData()))
    }

    @Test
    fun `un nom de warp qui sort de la règle des noms refuse le fichier, au chemin du warp`() {
        val findings = findings(warps = mapOf("ma maison" to WrittenWarp(base, null), "Base" to WrittenWarp(base, null)))

        assertEquals(listOf(FileFinding("warps[ma maison]", null, CommonMessages.FINDING_NOT_ALLOWED.with("ma maison", CommonMessages.ALLOWED_NAME.with()))), findings)
    }

    @Test
    fun `une clé d'accès qui sort de la règle des noms en minuscules refuse le fichier, au chemin de la clé`() {
        val findings = findings(warps = mapOf("base" to WrittenWarp(base, "Vip"), "hub" to WrittenWarp(base, "vip")))

        assertEquals(listOf(FileFinding("warps[base].key", null, CommonMessages.FINDING_NOT_ALLOWED.with("Vip", CommonMessages.ALLOWED_LOWERCASE_NAME.with()))), findings)
    }

    @Test
    fun `la version de schéma, les warps puis les joueurs se constatent dans cet ordre, chacun à son chemin`() {
        val written = WrittenWarpsData(
            schemaVersion = 2,
            warps = mapOf("base" to WrittenWarp(base.copy(x = null), null)),
            players = mapOf("Steve" to WrittenQuotaEntry("Steve", WrittenQuotaUsage("real", 10, -1))),
        )

        val expected = listOf(
            FileFinding("schema-version", null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1)),
            FileFinding("warps[base]", null, CommonMessages.FINDING_MISSING_KEY.with("x")),
            FileFinding("players[Steve]", null, CommonMessages.FINDING_NOT_ALLOWED.with("Steve", CommonMessages.ALLOWED_PLAYER_ID.with())),
            FileFinding("players[Steve].quota.uses", null, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L))),
        )
        assertEquals(expected, WarpsDataRules.findings(written))
    }

    @Test
    fun `un fichier valide donne les warps, leur clé, et la plage de chaque joueur`() {
        val written = WrittenWarpsData(
            warps = mapOf("base" to WrittenWarp(base, null), "staff" to WrittenWarp(base, "staff")),
            players = mapOf(steve.toString() to WrittenQuotaEntry("Steve", WrittenQuotaUsage("real", 1_759_665_600, 2))),
        )

        val data = WarpsDataRules.data(written)

        val position = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)
        val expected = WarpsData(
            Warps(mapOf("base" to Warp(position), "staff" to Warp(position, "staff"))),
            QuotaOnlyData(mapOf(steve to PlayerEntry("Steve", NoHoldings, QuotaUsage(QuotaClock.REAL, 1_759_665_600, 2)))),
        )
        assertEquals(expected, data)
        assertEquals(written, WarpsDataRules.written(data), "What is read writes back the same")
    }

    @Test
    fun `le fichier écrit porte sa version de schéma, et pas l'entrée d'un joueur qui ne porte que son nom`() {
        val alex = UUID.fromString("7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e")
        val data = WarpsData(Warps(mapOf("base" to Warp(base.value()))), QuotaOnlyData(mapOf(alex to PlayerEntry("Alex", NoHoldings))))

        val written = WarpsDataRules.written(data)

        assertEquals(WrittenWarpsData(1, mapOf("base" to WrittenWarp(base, null))), written)
    }
}
