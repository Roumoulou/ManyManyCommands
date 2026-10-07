// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Ce qu'un fichier de données a de commun à toutes les familles : sa version de schéma, l'UUID de chaque joueur, l'entrée que la famille juge (cahier des charges, chapitre 2.7.5). */
class DataRulesTest {

    /** L'entrée d'une famille d'essai telle qu'elle s'écrit : des marques, dont `!` est fautive. */
    private data class WrittenMarks(val marks: List<String>) : Holdings {

        override val isEmpty: Boolean get() = marks.isEmpty()
    }

    private val steve = "069a79f4-44e9-4726-a5be-fca90e38aaf5"

    private val none = emptyList<FileFinding>()

    private fun findings(written: WrittenData<WrittenMarks>): List<FileFinding> = DataRules.findings(written) { path, entry ->
        entry.marks.filter { it == "!" }.map { FileFinding("$path.marks", null, CommonMessages.FINDING_NOT_ALLOWED.with(it, "a letter")) }
    }

    @Test
    fun `un fichier de données se nomme sous world, quel que soit le nom du monde`() {
        assertEquals("world/homes.json", DataRules.fileName("homes"))
    }

    @Test
    fun `un fichier vide est valide`() {
        assertEquals(none, findings(WrittenData()))
    }

    @Test
    fun `un fichier plus récent que le mod est refusé`() {
        val expected = FileFinding("schema-version", null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1))

        assertEquals(listOf(expected), findings(WrittenData(schemaVersion = 2)))
        assertEquals(none, findings(WrittenData(schemaVersion = 1)))
    }

    @Test
    fun `un UUID mal formé refuse le fichier, à l'entrée du joueur, et l'entrée se juge quand même`() {
        val findings = findings(WrittenData(players = mapOf("Steve" to WrittenMarks(listOf("!")))))

        val expected = listOf(
            FileFinding("players[Steve]", null, CommonMessages.FINDING_NOT_ALLOWED.with("Steve", CommonMessages.ALLOWED_PLAYER_ID.with())),
            FileFinding("players[Steve].marks", null, CommonMessages.FINDING_NOT_ALLOWED.with("!", "a letter")),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `chaque entrée se juge à son chemin, sous l'UUID de son joueur`() {
        val findings = findings(WrittenData(players = mapOf(steve to WrittenMarks(listOf("a", "!")))))

        assertEquals(listOf(FileFinding("players[$steve].marks", null, CommonMessages.FINDING_NOT_ALLOWED.with("!", "a letter"))), findings)
    }

    @Test
    fun `un fichier valide donne l'entrée de chaque joueur, par son UUID, dans l'ordre du fichier`() {
        val alex = "7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e"
        val written = WrittenData(players = mapOf(alex to WrittenMarks(listOf("mine")), steve to WrittenMarks(listOf("base"))))

        val data = DataRules.players(written) { PlayerEntry("?", it) }

        assertEquals(listOf(UUID.fromString(alex), UUID.fromString(steve)), data.players.keys.toList())
        assertEquals(WrittenMarks(listOf("base")), data[UUID.fromString(steve)]?.holdings)
    }

    @Test
    fun `le fichier écrit porte la version du mod et les seules entrées qui tiennent autre chose qu'un nom`() {
        val data = FamilyData(mapOf(UUID.fromString(steve) to PlayerEntry("Steve", WrittenMarks(listOf("base"))), UUID.randomUUID() to PlayerEntry("Alex", WrittenMarks(emptyList()))))

        val written = DataRules.written(data) { it.holdings }

        assertEquals(WrittenData(1, mapOf(steve to WrittenMarks(listOf("base")))), written)
    }
}
