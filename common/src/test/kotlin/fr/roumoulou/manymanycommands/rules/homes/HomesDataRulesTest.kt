// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.data.WrittenPosition
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaUsage
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** `homes.json` en règles : ce qui rend le fichier invalide, et ce qu'un fichier valide donne (cahier des charges, chapitre 2.7.5). */
class HomesDataRulesTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val path = "players[$steve]"

    private val base = WrittenPosition("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val none = emptyList<FileFinding>()

    private fun entry(defaultHome: String? = null, homes: Map<String, WrittenPosition> = emptyMap(), quota: WrittenQuotaUsage? = null) = WrittenHomesEntry("Steve", defaultHome, homes, quota)

    private fun findings(entry: WrittenHomesEntry): List<FileFinding> = HomesDataRules.findings(WrittenData(players = mapOf(steve.toString() to entry)))

    @Test
    fun `le fichier se nomme world homes json`() {
        assertEquals("world/homes.json", HomesDataRules.fileName)
    }

    @Test
    fun `une entrée qui ne porte que son nom est valide, sans home ni plage`() {
        assertEquals(none, findings(entry()))
        assertEquals(PlayerEntry("Steve", PlayerHomes()), entry().value())
    }

    @Test
    fun `un nom de home qui sort de la règle des noms refuse le fichier, au chemin du home`() {
        val findings = findings(entry(homes = mapOf("ma maison" to base, "Base" to base)))

        assertEquals(listOf(FileFinding("$path.homes[ma maison]", null, CommonMessages.FINDING_NOT_ALLOWED.with("ma maison", CommonMessages.ALLOWED_NAME.with()))), findings)
    }

    @Test
    fun `un home par défaut qui ne nomme aucun home du joueur refuse le fichier, les homes du joueur en valeurs admises`() {
        val withHomes = findings(entry(defaultHome = "mine", homes = mapOf("base" to base, "atelier" to base)))
        val withoutHomes = findings(entry(defaultHome = "mine"))

        assertEquals(listOf(FileFinding("$path.default-home", null, CommonMessages.FINDING_NOT_ALLOWED.with("mine", "base, atelier"))), withHomes)
        assertEquals(listOf(FileFinding("$path.default-home", null, CommonMessages.FINDING_NOT_ALLOWED.with("mine", CommonMessages.ALLOWED_NONE.with()))), withoutHomes)
        assertEquals(none, findings(entry(defaultHome = "base", homes = mapOf("base" to base))))
    }

    @Test
    fun `une position incomplète et une plage fautive se constatent à leur chemin, les homes d'abord`() {
        val findings = findings(entry(homes = mapOf("base" to base.copy(x = null)), quota = WrittenQuotaUsage("real", 10, -1)))

        val expected = listOf(
            FileFinding("$path.homes[base]", null, CommonMessages.FINDING_MISSING_KEY.with("x")),
            FileFinding("$path.quota.uses", null, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L))),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `un fichier valide donne les homes, le home désigné et la plage de chaque joueur`() {
        val written = WrittenData(players = mapOf(steve.toString() to entry(defaultHome = "base", homes = mapOf("base" to base), quota = WrittenQuotaUsage("real", 1_759_665_600, 2))))

        val data = HomesDataRules.data(written)

        val expected = PlayerEntry("Steve", PlayerHomes(mapOf("base" to Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)), "base"), QuotaUsage(QuotaClock.REAL, 1_759_665_600, 2))
        assertEquals(expected, data[steve])
        assertEquals(written, HomesDataRules.written(data), "What is read writes back the same")
    }

    @Test
    fun `le fichier écrit porte le nom du joueur avec son entrée, et pas l'entrée qui ne porte que son nom`() {
        val alex = UUID.fromString("7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e")
        val data = HomesData(mapOf(steve to PlayerEntry("Steve", PlayerHomes(mapOf("base" to base.value()))), alex to PlayerEntry("Alex", PlayerHomes())))

        val written = HomesDataRules.written(data)

        assertEquals(WrittenData(1, mapOf(steve.toString() to entry(homes = mapOf("base" to base)))), written)
    }
}
