// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.permission

import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** La forme d'identifiant d'un nœud, valide pour le jeu (cahier des charges, chapitre 2.4.2). */
class PermissionNodeIdentifierTest {

    private fun assertValidIdentifier(node: PermissionNode) {
        val identifier = Identifier.tryParse(node.identifier)
        assertNotNull(identifier, "Node ${node.name} is not a valid identifier for the game: ${node.identifier}")
        assertEquals(MOD_ID, identifier!!.namespace)
        assertEquals(node.identifier, identifier.toString())
    }

    @Test
    fun `chaque nœud de manymanycommands est un identifiant valide pour le jeu`() {
        ModNodes.ALL.forEach(::assertValidIdentifier)
    }

    @Test
    fun `un nœud à plusieurs segments reste un identifiant valide, ses segments en chemin`() {
        val node = PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "delete")

        assertValidIdentifier(node)
        assertEquals("homes/player/delete", Identifier.tryParse(node.identifier)!!.path)
    }
}
