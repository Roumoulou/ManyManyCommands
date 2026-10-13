// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les nœuds des six commandes des demandes (cahier des charges, chapitre 2.4.2). */
class TpaNodesTest {

    @Test
    fun `les six commandes ont leur nœud, nommé d'après leur chemin, et tout joueur les reçoit par défaut`() {
        val names = listOf("manymanycommands.tpa", "manymanycommands.tpahere", "manymanycommands.tpaaccept", "manymanycommands.tpadeny", "manymanycommands.tpacancel", "manymanycommands.tpatoggle")

        assertEquals(names, TpaNodes.ALL.map { it.name })
        assertEquals(setOf(Profile.PLAYER), TpaNodes.ALL.map { it.defaultProfile }.toSet())
        assertEquals("manymanycommands:tpaaccept", TpaNodes.TPAACCEPT.identifier)
    }
}
