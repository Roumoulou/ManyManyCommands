// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Le nœud de `/back` (cahier des charges, chapitre 2.4.2). */
class BackNodesTest {

    @Test
    fun `back a son nœud, nommé d'après son chemin, et tout joueur le reçoit par défaut`() {
        assertEquals("manymanycommands.back", BackNodes.BACK.name)
        assertEquals(Profile.PLAYER, BackNodes.BACK.defaultProfile)
    }
}
