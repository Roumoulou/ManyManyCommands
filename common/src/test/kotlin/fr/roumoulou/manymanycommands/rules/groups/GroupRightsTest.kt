// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.homes.HomesNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import fr.roumoulou.manymanycommands.rules.teleport.BypassNodes
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Les verdicts du système du mod (`groups.md` des specs, chapitres 2 et 3) : la permission propre, le groupe, le silence, et le nœud d'un jeu de règles. */
class GroupRightsTest {

    private val vip = GroupDefinition(mapOf("homes" to "vip"), allow = setOf(ModNodes.RELOAD.name), deny = setOf(HomesNodes.CREATE.name))

    @Test
    fun `le groupe accorde et retire ses nœuds, et se tait sur les autres`() {
        assertEquals(true, GroupRights.verdict(Membership("vip"), vip, ModNodes.RELOAD))
        assertEquals(false, GroupRights.verdict(Membership("vip"), vip, HomesNodes.CREATE))
        assertNull(GroupRights.verdict(Membership("vip"), vip, HomesNodes.DELETE))
        assertNull(GroupRights.verdict(null, vip, HomesNodes.DELETE), "Without a membership, the group given decides alone")
        assertNull(GroupRights.verdict(null, GroupDefinition(), BypassNodes.DELAY))
    }

    @Test
    fun `la permission propre du joueur l'emporte sur son groupe`() {
        assertEquals(true, GroupRights.verdict(Membership("vip", allow = setOf(HomesNodes.CREATE.name)), vip, HomesNodes.CREATE))
        assertEquals(false, GroupRights.verdict(Membership("vip", deny = setOf(ModNodes.RELOAD.name)), vip, ModNodes.RELOAD))
        assertEquals(false, GroupRights.verdict(Membership(deny = setOf(BypassNodes.QUOTA.name)), GroupDefinition(), BypassNodes.QUOTA), "A bypass node withdrawn from an administrator")
    }

    @Test
    fun `le jeu que nomme le groupe est accordé pour sa famille, et aucun autre`() {
        assertEquals(true, GroupRights.verdict(Membership("vip"), vip, HomesRules.setNode("vip")))
        assertNull(GroupRights.verdict(Membership("vip"), vip, HomesRules.setNode("staff")))
        assertNull(GroupRights.verdict(Membership("vip"), vip, WarpsRules.setNode("vip")), "The group names no set for the warps")
        assertNull(GroupRights.verdict(null, GroupDefinition(), HomesRules.setNode("vip")))
    }

    @Test
    fun `un jeu propre du joueur remplace celui du groupe, qui lui est alors retiré, dans sa seule famille`() {
        val membership = Membership("vip", allow = setOf(HomesRules.setNode("staff").name))
        val group = GroupDefinition(mapOf("homes" to "vip", "warps" to "vip"))

        assertEquals(true, GroupRights.verdict(membership, group, HomesRules.setNode("staff")))
        assertEquals(false, GroupRights.verdict(membership, group, HomesRules.setNode("vip")), "The set named by the group is refused, so that the order of the file does not make the group win")
        assertEquals(false, GroupRights.verdict(membership, group, HomesRules.setNode("other")))
        assertEquals(true, GroupRights.verdict(membership, group, WarpsRules.setNode("vip")), "The warps follow the group")
        assertEquals(false, GroupRights.verdict(Membership("vip", deny = setOf(HomesRules.setNode("vip").name)), group, HomesRules.setNode("vip")), "A set denied in person is refused")
    }
}
