// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.players

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Qui `<player>` désigne (cahier des charges, chapitre 2.7.2) : le connecté d'abord, le registre ensuite, sans la casse, le dernier connecté à égalité. */
class PlayerLookupTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val alex = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")

    private val formerAlex = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6")

    private val stranger = UUID.fromString("f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2")

    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    /** Deux joueurs ont porté le nom Alex : celui qui le porte encore s'est connecté le dernier. */
    private val registry = PlayerRegistry().joined(steve, "Steve", noon).joined(formerAlex, "Alex", noon.minusSeconds(3600)).joined(alex, "Alex", noon)

    @Test
    fun `le joueur connecté qui porte le nom, sans la casse, passe avant le registre, sous son nom du moment`() {
        val connected = listOf(PlayerRef(stranger, "Steve"))

        assertEquals(PlayerRef(stranger, "Steve"), PlayerLookup.resolve("steve", connected, registry))
    }

    @Test
    fun `à défaut, le joueur du registre dont c'est le dernier nom connu, sous ce nom`() {
        assertEquals(PlayerRef(steve, "Steve"), PlayerLookup.resolve("STEVE", emptyList(), registry))
    }

    @Test
    fun `quand plusieurs joueurs du registre ont porté le nom, le dernier connecté l'emporte`() {
        assertEquals(PlayerRef(alex, "Alex"), PlayerLookup.resolve("alex", emptyList(), registry))
    }

    @Test
    fun `un nom que personne ne porte ne désigne personne`() {
        assertNull(PlayerLookup.resolve("Herobrine", listOf(PlayerRef(steve, "Steve")), registry))
    }

    @Test
    fun `un registre indisponible ne désigne que les joueurs connectés`() {
        assertNull(PlayerLookup.resolve("Steve", emptyList(), registry = null))
        assertEquals(PlayerRef(steve, "Steve"), PlayerLookup.resolve("steve", listOf(PlayerRef(steve, "Steve")), registry = null))
    }
}
