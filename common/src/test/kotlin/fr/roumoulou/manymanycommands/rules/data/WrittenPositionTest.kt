// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Une position telle qu'un fichier de données l'écrit : ses six clés obligatoires (cahier des charges, chapitre 2.7.5). */
class WrittenPositionTest {

    private val path = "players[steve].homes[base]"

    private val none = emptyList<FileFinding>()

    @Test
    fun `une position entière s'admet, et sa dimension se lit sous son identifiant complet`() {
        val written = WrittenPosition("the_nether", 12.5, 64.0, -3.25, 90f, 0f)

        assertEquals(none, written.findings(path))
        assertEquals(Position("minecraft:the_nether", 12.5, 64.0, -3.25, 90f, 0f), written.value())
    }

    @Test
    fun `chaque clé qui manque se constate au chemin de la position`() {
        val expected = listOf("dimension", "x", "y", "z", "yaw", "pitch").map { FileFinding(path, null, CommonMessages.FINDING_MISSING_KEY.with(it)) }

        assertEquals(expected, WrittenPosition(null, null, null, null, null, null).findings(path))
    }

    @Test
    fun `une dimension qui n'est pas un identifiant se refuse, à son chemin`() {
        val findings = WrittenPosition("The Nether", 0.0, 0.0, 0.0, 0f, 0f).findings(path)

        assertEquals(listOf(FileFinding("$path.dimension", null, CommonMessages.FINDING_NOT_ALLOWED.with("The Nether", CommonMessages.ALLOWED_DIMENSION.with()))), findings)
    }

    @Test
    fun `une position s'écrit telle qu'elle se relit`() {
        val position = Position("mymod:moon", 1.0, 2.0, 3.0, 45f, -10f)

        assertEquals(WrittenPosition("mymod:moon", 1.0, 2.0, 3.0, 45f, -10f), WrittenPosition.of(position))
        assertEquals(position, WrittenPosition.of(position).value())
    }
}
