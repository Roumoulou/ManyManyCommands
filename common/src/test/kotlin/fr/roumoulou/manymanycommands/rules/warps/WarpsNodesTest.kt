// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les nœuds de `/warps` (cahier des charges, chapitre 2.4.2), et le nœud d'accès d'un warp réservé (`warps.md` des specs, chapitre 3.5). */
class WarpsNodesTest {

    @Test
    fun `chaque commande a son nœud, nommé d'après son chemin, l'envoi sous teleport others`() {
        val names = listOf("teleport", "teleport.others", "list", "create", "delete", "rename", "move", "restrict", "unrestrict").map { "manymanycommands.warps.$it" }

        assertEquals(names, WarpsNodes.ALL.map { it.name })
    }

    @Test
    fun `teleport et list vont à tout joueur par défaut, le reste à l'administrateur`() {
        assertEquals(listOf(WarpsNodes.TELEPORT, WarpsNodes.LIST), WarpsNodes.ALL.filter { it.defaultProfile == Profile.PLAYER })
        assertEquals(setOf(Profile.ADMINISTRATOR), (WarpsNodes.ALL - WarpsNodes.TELEPORT - WarpsNodes.LIST).map { it.defaultProfile }.toSet())
    }

    @Test
    fun `warps sans sous-commande prend le nœud de list`() {
        assertEquals("manymanycommands.warps.list", WarpsNodes.LIST.name)
    }

    @Test
    fun `le nœud d'accès d'un warp réservé porte sa clé, et l'administrateur le reçoit par défaut`() {
        val access = WarpsNodes.access("vip")

        assertEquals("manymanycommands.warps.access.vip", access.name)
        assertEquals("manymanycommands:warps/access/vip", access.identifier)
        assertEquals(Profile.ADMINISTRATOR, access.defaultProfile)
    }
}
