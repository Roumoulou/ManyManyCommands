// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.message.Interaction
import fr.roumoulou.manymanycommands.rules.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** La liste des warps en règles (`warps.md` des specs, chapitre 2.2) : l'en-tête, le tri, les warps réservés, le clic, le survol, la liste vide. */
class WarpsListingTest {

    private val overworld = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val nether = Position("minecraft:the_nether", 0.5, 70.0, 0.5, 0f, 0f)

    private val warps = Warps(mapOf("base" to Warp(overworld), "Base" to Warp(nether), "atelier" to Warp(overworld), "vip" to Warp(nether, key = "vip")))

    @Test
    fun `la liste donne son en-tête avec le nombre de warps affichés, puis chaque warp accessible dans l'ordre de la liste`() {
        val messages = WarpsListing.messages(warps, hasAccess = { false }, showKeys = false)

        assertEquals(WarpsMessages.LIST_HEADER.with(3), messages[0])
        assertEquals(listOf<Any>("atelier", "Base", "base"), messages.drop(1).map { it.text.arguments.single() })
        assertEquals(setOf(WarpsMessages.LIST_WARP.key), messages.drop(1).map { it.key }.toSet())
        assertEquals(setOf(MessageType.DISPLAY), messages.map { it.type }.toSet())
    }

    @Test
    fun `pour qui a le nœud de restrict, un warp réservé porte sa clé, et pour qui n'a que l'accès, il s'affiche comme un autre`() {
        val admin = WarpsListing.messages(warps, hasAccess = { true }, showKeys = true)
        val holder = WarpsListing.messages(warps, hasAccess = { it == "vip" }, showKeys = false)

        assertEquals(WarpsMessages.LIST_HEADER.with(4), admin[0])
        assertEquals(WarpsMessages.LIST_RESERVED.with("vip", "vip"), admin[4].copy(interaction = null))
        assertEquals(WarpsMessages.LIST_WARP.with("base"), admin[3].copy(interaction = null), "An open warp has no key to show")
        assertEquals(WarpsMessages.LIST_HEADER.with(4), holder[0])
        assertEquals(WarpsMessages.LIST_WARP.with("vip"), holder[4].copy(interaction = null))
    }

    @Test
    fun `chaque warp se clique pour le rejoindre, et son survol donne sa dimension et ses coordonnées entières`() {
        val messages = WarpsListing.messages(Warps(mapOf("base" to Warp(overworld))), hasAccess = { false }, showKeys = false)

        val expected = Interaction("/warps teleport base", WarpsMessages.LIST_POSITION.with("minecraft:overworld", 12, 64, -4))
        assertEquals(expected, messages[1].interaction)
        assertEquals("/warps teleport Base", WarpsListing.teleportCommand("Base"))
    }

    @Test
    fun `une liste vide le dit par un message, après l'en-tête, comme celle d'un lecteur sans accès aux seuls warps réservés`() {
        val empty = listOf(WarpsMessages.LIST_HEADER.with(0), WarpsMessages.LIST_EMPTY.with())

        assertEquals(empty, WarpsListing.messages(Warps(), hasAccess = { false }, showKeys = false))
        assertEquals(empty, WarpsListing.messages(Warps(mapOf("vip" to Warp(nether, "vip"))), hasAccess = { false }, showKeys = false))
    }
}
