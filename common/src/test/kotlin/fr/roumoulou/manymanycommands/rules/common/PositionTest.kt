// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Une position d'un fichier de données (cahier des charges, chapitre 2.7.5), et ses coordonnées entières dans les messages. */
class PositionTest {

    @Test
    fun `les coordonnées entières sont celles du bloc, arrondies vers le bas`() {
        val position = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

        assertEquals(12, position.blockX)
        assertEquals(64, position.blockY)
        assertEquals(-4, position.blockZ)
    }
}
