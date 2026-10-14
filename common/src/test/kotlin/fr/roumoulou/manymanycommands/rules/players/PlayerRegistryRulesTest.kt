// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.players

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Le registre des joueurs en règles (cahier des charges, chapitres 2.7.2 et 2.7.5) : la connexion qui l'écrit, ses constats, ce qui s'écrit. */
class PlayerRegistryRulesTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val alex = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")

    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun `le fichier se nomme data players json`() {
        assertEquals("data/players.json", PlayerRegistryRules.fileName)
    }

    @Test
    fun `une entrée porte le nom et la dernière connexion, et un fichier valide s'écrit tel qu'il se relit`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenPlayerRecord("Steve", "2026-10-05T12:00:00Z")))

        val registry = PlayerRegistryRules.registry(written)

        assertEquals(emptyList<FileFinding>(), PlayerRegistryRules.findings(written))
        assertEquals(PlayerRecord("Steve", noon), registry[steve])
        assertEquals(written, PlayerRegistryRules.written(registry))
    }

    @Test
    fun `le nom et la dernière connexion sont obligatoires, et se constatent au chemin de l'entrée`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenPlayerRecord(null, null)))

        val expected = listOf(
            FileFinding("players[$steve]", null, CommonMessages.FINDING_MISSING_KEY.with("name")),
            FileFinding("players[$steve]", null, CommonMessages.FINDING_MISSING_KEY.with("last-seen")),
        )
        assertEquals(expected, PlayerRegistryRules.findings(written))
    }

    @Test
    fun `un nom hors de la règle des noms et une date qui n'est pas un instant UTC se constatent à leur chemin`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenPlayerRecord("Stève", "hier")))

        val expected = listOf(
            FileFinding("players[$steve].name", null, CommonMessages.FINDING_NOT_ALLOWED.with("Stève", CommonMessages.ALLOWED_NAME.with())),
            FileFinding("players[$steve].last-seen", null, CommonMessages.FINDING_NOT_ALLOWED.with("hier", CommonMessages.ALLOWED_UTC_INSTANT.with())),
        )
        assertEquals(expected, PlayerRegistryRules.findings(written))
        assertNull(PlayerRegistryRules.instantOf("2026-10-05 12:00"))
        assertEquals(noon, PlayerRegistryRules.instantOf("2026-10-05T12:00:00Z"))
    }

    @Test
    fun `la version de schéma et l'UUID de chaque joueur se jugent comme dans tout fichier de données`() {
        val written = WrittenData(schemaVersion = 2, players = mapOf("Steve" to WrittenPlayerRecord("Steve", "2026-10-05T12:00:00Z")))

        val expected = listOf(
            FileFinding("schema-version", null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1)),
            FileFinding("players[Steve]", null, CommonMessages.FINDING_NOT_ALLOWED.with("Steve", CommonMessages.ALLOWED_PLAYER_ID.with())),
        )
        assertEquals(expected, PlayerRegistryRules.findings(written))
    }

    @Test
    fun `une connexion note le joueur à l'instant, tronqué à la seconde, et remplace son entrée à sa place`() {
        val registry = PlayerRegistry().joined(steve, "Steve", noon).joined(alex, "Alex", noon.plusSeconds(1))

        val again = registry.joined(steve, "Steve2", Instant.parse("2026-10-06T08:00:00.750Z"))

        assertEquals(listOf(steve, alex), again.players.keys.toList())
        assertEquals(PlayerRecord("Steve2", Instant.parse("2026-10-06T08:00:00Z")), again[steve])
        assertEquals(PlayerRecord("Alex", noon.plusSeconds(1)), again[alex])
        assertEquals(WrittenData(players = mapOf(steve.toString() to WrittenPlayerRecord("Steve2", "2026-10-06T08:00:00Z"), alex.toString() to WrittenPlayerRecord("Alex", "2026-10-05T12:00:01Z"))), PlayerRegistryRules.written(again))
    }
}
