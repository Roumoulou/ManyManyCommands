// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/** L'UUID d'un joueur tel qu'un fichier l'écrit (cahier des charges, chapitre 2.7.2). */
class PlayerIdsTest {

    @Test
    fun `un UUID s'écrit en cinq groupes d'hexadécimaux minuscules`() {
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), PlayerIds.parse("069a79f4-44e9-4726-a5be-fca90e38aaf5"))
    }

    @Test
    fun `ce qui n'en est pas un est refusé, les formes abrégées du JDK comprises`() {
        listOf("", "Steve", "069A79F4-44E9-4726-A5BE-FCA90E38AAF5", "069a79f444e94726a5befca90e38aaf5", "1-2-3-4-5", "{069a79f4-44e9-4726-a5be-fca90e38aaf5}").forEach { written ->
            assertNull(PlayerIds.parse(written), "'$written' must be refused")
        }
    }
}
