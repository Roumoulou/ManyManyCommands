// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Le contenu d'un fichier de données : l'entrée de chaque joueur, et celle qui ne porte que son nom (cahier des charges, chapitre 2.7.5). */
class FamilyDataTest {

    /** Ce qu'une famille d'essai tient d'un joueur : des marques. */
    private data class Marks(val marks: Set<String>) : Holdings {

        override val isEmpty: Boolean get() = marks.isEmpty()
    }

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val alex = UUID.fromString("7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e")

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 2)

    @Test
    fun `une entrée est vide quand elle ne porte que son nom`() {
        assertTrue(PlayerEntry("Steve", Marks(emptySet())).isEmpty)
        assertFalse(PlayerEntry("Steve", Marks(setOf("base"))).isEmpty)
        assertFalse(PlayerEntry("Steve", Marks(emptySet()), window).isEmpty, "A quota window is kept even without holdings")
    }

    @Test
    fun `un joueur sans entrée ne tient rien`() {
        assertNull(FamilyData<Marks>()[steve])
    }

    @Test
    fun `une entrée s'ajoute, se remplace à sa place, et disparaît quand elle ne porte plus que son nom`() {
        val data = FamilyData<Marks>().with(steve, PlayerEntry("Steve", Marks(setOf("base")))).with(alex, PlayerEntry("Alex", Marks(setOf("mine"))))

        val replaced = data.with(steve, PlayerEntry("Steve", Marks(setOf("base", "ferme"))))
        val emptied = replaced.with(steve, PlayerEntry("Steve", Marks(emptySet())))

        assertEquals(listOf(steve, alex), replaced.players.keys.toList(), "A replaced entry keeps its place")
        assertEquals(Marks(setOf("base", "ferme")), replaced[steve]?.holdings)
        assertEquals(listOf(alex), emptied.players.keys.toList())
        assertEquals(data, emptied.with(steve, PlayerEntry("Steve", Marks(setOf("base")))).let { FamilyData(mapOf(steve to it.players.getValue(steve), alex to it.players.getValue(alex))) })
    }
}
