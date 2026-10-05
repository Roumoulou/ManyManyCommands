// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.permission

import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le nœud d'une commande et le défaut du profil (cahier des charges, chapitres 2.4.1 et 2.4.2). */
class PermissionNodeTest {

    @Test
    fun `le nœud d'une commande se nomme d'après son chemin`() {
        assertEquals("manymanycommands.homes.create", PermissionNode.ofCommand(Profile.PLAYER, "homes", "create").name)
        assertEquals("manymanycommands.homes.player.delete", PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "delete").name)
    }

    @Test
    fun `les commandes de manymanycommands ne répètent pas son nom`() {
        assertEquals("manymanycommands.reload", PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "reload").name)
        assertEquals("manymanycommands.group.set", PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands", "group", "set").name)
    }

    @Test
    fun `manymanycommands seul prend le nœud status`() {
        assertEquals("manymanycommands.status", PermissionNode.ofCommand(Profile.ADMINISTRATOR, "manymanycommands").name)
    }

    @Test
    fun `un nœud s'écrit en identifiant, le mod en espace de noms et le chemin à barres obliques`() {
        assertEquals("manymanycommands:homes/create", PermissionNode.ofCommand(Profile.PLAYER, "homes", "create").identifier)
        assertEquals("manymanycommands:homes/player/delete", PermissionNode.ofCommand(Profile.ADMINISTRATOR, "homes", "player", "delete").identifier)
        assertEquals("manymanycommands:status", ModNodes.STATUS.identifier)
    }

    @Test
    fun `un chemin de commande a au moins un mot`() {
        assertThrows(IllegalArgumentException::class.java) { PermissionNode.ofCommand(Profile.PLAYER) }
    }

    @Test
    fun `tant que la source des droits ne se prononce pas, le profil joueur ouvre le nœud à tous`() {
        val node = PermissionNode.ofCommand(Profile.PLAYER, "homes", "create")

        assertTrue(node.isGranted(verdict = null, isGameMaster = false))
        assertTrue(node.isGranted(verdict = null, isGameMaster = true))
    }

    @Test
    fun `tant que la source des droits ne se prononce pas, le profil administrateur réserve le nœud aux maîtres du jeu`() {
        assertFalse(ModNodes.RELOAD.isGranted(verdict = null, isGameMaster = false))
        assertTrue(ModNodes.RELOAD.isGranted(verdict = null, isGameMaster = true))
    }

    @Test
    fun `la source des droits accorde un nœud que le profil ne donne pas`() {
        assertTrue(ModNodes.RELOAD.isGranted(verdict = true, isGameMaster = false))
    }

    @Test
    fun `la source des droits retire un nœud que le profil donne`() {
        assertFalse(ModNodes.RELOAD.isGranted(verdict = false, isGameMaster = true))
        assertFalse(PermissionNode.ofCommand(Profile.PLAYER, "homes", "create").isGranted(verdict = false, isGameMaster = false))
    }

    @Test
    fun `les deux commandes de manymanycommands sont réservées à l'administrateur`() {
        assertEquals(listOf(Profile.ADMINISTRATOR, Profile.ADMINISTRATOR), ModNodes.ALL.map { it.defaultProfile })
        assertEquals(listOf("manymanycommands.status", "manymanycommands.reload"), ModNodes.ALL.map { it.name })
    }
}
