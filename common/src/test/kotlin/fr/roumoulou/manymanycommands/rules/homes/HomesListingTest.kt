// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.message.Interaction
import fr.roumoulou.manymanycommands.rules.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** La liste des homes en règles (`homes.md` des specs, chapitre 3.4) : l'en-tête, le tri, la marque du home par défaut, le clic, le survol, la liste vide. */
class HomesListingTest {

    private val overworld = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val nether = Position("minecraft:the_nether", 0.5, 70.0, 0.5, 0f, 0f)

    @Test
    fun `la liste donne son en-tête, puis chaque home dans l'ordre de la liste, le home par défaut marqué`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "Base" to nether, "atelier" to overworld), designated = "Base")

        val messages = HomesListing.messages(homes, max = 5)

        assertEquals(HomesMessages.LIST_HEADER.with(3, 5L), messages[0])
        assertEquals(listOf(HomesMessages.LIST_HOME.key, HomesMessages.LIST_DEFAULT_HOME.key, HomesMessages.LIST_HOME.key), messages.drop(1).map { it.key })
        assertEquals(listOf<Any>("atelier", "Base", "base"), messages.drop(1).map { it.text.arguments.single() })
        assertEquals(setOf(MessageType.DISPLAY), messages.map { it.type }.toSet())
    }

    @Test
    fun `chaque home se clique pour le rejoindre, et son survol donne sa dimension et ses coordonnées entières`() {
        val messages = HomesListing.messages(PlayerHomes(mapOf("base" to overworld)), max = 3)

        val expected = Interaction("/homes teleport base", HomesMessages.LIST_POSITION.with("minecraft:overworld", 12, 64, -4))
        assertEquals(expected, messages[1].interaction)
        assertEquals("/homes teleport Base", HomesListing.teleportCommand("Base"))
    }

    @Test
    fun `une liste vide le dit par un message, après l'en-tête`() {
        assertEquals(listOf(HomesMessages.LIST_HEADER.with(0, 3L), HomesMessages.LIST_EMPTY.with()), HomesListing.messages(PlayerHomes(), max = 3))
    }

    @Test
    fun `le seul home d'un joueur est marqué par défaut sans désignation`() {
        val messages = HomesListing.messages(PlayerHomes(mapOf("base" to overworld)), max = 3)

        assertEquals(HomesMessages.LIST_DEFAULT_HOME.key, messages[1].key)
    }
}
