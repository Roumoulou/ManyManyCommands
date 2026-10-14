// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les nœuds des branches `group` et `permission` (`groups.md` des specs, chapitre 5), réservés à l'administrateur, sans le nom de la commande. */
class GroupsNodesTest {

    @Test
    fun `les nœuds ne répètent pas manymanycommands, et nomment leur branche puis leur sous-commande`() {
        assertEquals("manymanycommands.group.set", GroupsNodes.GROUP_SET.name)
        assertEquals("manymanycommands.permission.allow", GroupsNodes.PERMISSION_ALLOW.name)
        assertEquals(listOf("group.list", "group.info", "group.set", "group.reset", "group.of", "permission.allow", "permission.deny", "permission.unset", "permission.list"), GroupsNodes.ALL.map { it.segments.joinToString(".") })
    }

    @Test
    fun `l'administrateur reçoit les neuf nœuds par défaut, et le joueur aucun`() {
        assertTrue(GroupsNodes.ALL.all { it.defaultProfile == Profile.ADMINISTRATOR })
        assertEquals(GroupsNodes.GROUP + GroupsNodes.PERMISSION, GroupsNodes.ALL)
    }
}
