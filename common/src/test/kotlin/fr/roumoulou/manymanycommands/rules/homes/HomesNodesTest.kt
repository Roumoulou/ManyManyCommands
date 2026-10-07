// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les nœuds des commandes du joueur de `/homes` (cahier des charges, chapitre 2.4.2). */
class HomesNodesTest {

    @Test
    fun `chaque commande du joueur a son nœud, nommé d'après son chemin, et tout joueur le reçoit par défaut`() {
        val names = listOf("create", "teleport", "delete", "list", "rename", "move", "default").map { "manymanycommands.homes.$it" }

        assertEquals(names, HomesNodes.PLAYER_COMMANDS.map { it.name })
        assertEquals(setOf(Profile.PLAYER), HomesNodes.PLAYER_COMMANDS.map { it.defaultProfile }.toSet())
    }

    @Test
    fun `homes sans sous-commande prend le nœud de list`() {
        assertEquals("manymanycommands.homes.list", HomesNodes.LIST.name)
    }
}
