// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** L'identifiant d'une dimension dans un fichier (cahier des charges, chapitre 2.7.3). */
class DimensionIdsTest {

    @Test
    fun `une dimension s'écrit par son identifiant`() {
        assertEquals("minecraft:the_nether", DimensionIds.normalize("minecraft:the_nether"))
        assertEquals("minecraft:overworld", DimensionIds.normalize("minecraft:overworld"))
    }

    @Test
    fun `la dimension d'un datapack ou d'un mod est admise, que le serveur la porte ou non`() {
        assertEquals("mymod:moon", DimensionIds.normalize("mymod:moon"))
        assertEquals("my-pack.v2:worlds/the_moon", DimensionIds.normalize("my-pack.v2:worlds/the_moon"))
    }

    @Test
    fun `sans espace de noms, l'identifiant prend celui du jeu`() {
        assertEquals("minecraft:the_end", DimensionIds.normalize("the_end"))
    }

    @Test
    fun `ce qui n'est pas un identifiant est refusé`() {
        listOf("", "The Nether", "minecraft:The_Nether", "Minecraft:the_nether", "minecraft:", ":the_nether", "a:b:c", "minecraft:the nether", "nether!", "my/mod:moon").forEach { written ->
            assertNull(DimensionIds.normalize(written), "'$written' must be refused")
        }
    }
}
