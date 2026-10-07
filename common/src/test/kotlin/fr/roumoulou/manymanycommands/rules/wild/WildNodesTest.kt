// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les nœuds de `/wild` et de `/wild timed` (cahier des charges, chapitre 2.4.2). */
class WildNodesTest {

    @Test
    fun `wild et wild timed ont leur nœud, nommé d'après leur chemin, que tout joueur reçoit par défaut`() {
        assertEquals(listOf("manymanycommands.wild", "manymanycommands.wild.timed"), WildNodes.ALL.map { it.name })
        assertEquals(setOf(Profile.PLAYER), WildNodes.ALL.map { it.defaultProfile }.toSet())
    }
}
