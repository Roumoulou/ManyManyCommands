// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.permission

import fr.roumoulou.manymanycommands.rules.groups.GroupsNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.teleport.BypassNodes
import fr.roumoulou.manymanycommands.rules.warps.WarpsNodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Les nœuds que le mod connaît (cahier des charges, chapitres 2.4.2 à 2.4.4) : ceux des commandes, les dispenses, les clés d'accès, les jeux de règles. */
class NodeCatalogTest {

    @Test
    fun `un nœud de commande ou de dispense se retrouve par son nom, et un nom inconnu ne donne rien`() {
        assertEquals(HomesNodes.CREATE, NodeCatalog.parse("manymanycommands.homes.create"))
        assertEquals(GroupsNodes.GROUP_SET, NodeCatalog.parse("manymanycommands.group.set"))
        assertEquals(BypassNodes.COOLDOWN, NodeCatalog.parse("manymanycommands.bypass.cooldown"))
        assertNull(NodeCatalog.parse("manymanycommands.homes.fly"))
        assertNull(NodeCatalog.parse("homes.create"))
        assertNull(NodeCatalog.parse("luckperms.homes.create"))
    }

    @Test
    fun `la clé d'accès d'un warp est un nœud pour toute clé bien écrite`() {
        assertEquals(WarpsNodes.access("vip"), NodeCatalog.parse("manymanycommands.warps.access.vip"))
        assertNull(NodeCatalog.parse("manymanycommands.warps.access.VIP"))
        assertNull(NodeCatalog.parse("manymanycommands.warps.access"))
        assertNull(NodeCatalog.ruleSetOf("manymanycommands.warps.access.vip"))
    }

    @Test
    fun `le jeu d'une famille est un nœud pour toute famille qui a des jeux et tout nom bien écrit`() {
        assertEquals(HomesRules.setNode("vip"), NodeCatalog.parse("manymanycommands.rules.homes.vip"))
        assertEquals(RuleSetNode("homes", "vip"), NodeCatalog.ruleSetOf("manymanycommands.rules.homes.vip"))
        assertEquals(RuleSetNode("tpa", "staff"), NodeCatalog.ruleSetOf(NodeCatalog.parse("manymanycommands.rules.tpa.staff") ?: error("known")))
        assertNull(NodeCatalog.parse("manymanycommands.rules.groups.vip"), "The groups have no rule sets")
        assertNull(NodeCatalog.parse("manymanycommands.rules.homes.Vip"))
        assertNull(NodeCatalog.parse("manymanycommands.rules.homes.vip.extra"))
        assertNull(NodeCatalog.ruleSetOf(HomesNodes.CREATE))
        assertEquals(listOf("homes", "warps", "back", "wild", "tpa"), NodeCatalog.FAMILIES)
    }

    @Test
    fun `les nœuds fixes suivent l'ordre de l'arbre, les dispenses en dernier, chacun une fois`() {
        val names = NodeCatalog.FIXED.map { it.name }

        assertEquals(names.distinct(), names)
        assertEquals("manymanycommands.homes.create", names.first())
        assertEquals(listOf("manymanycommands.bypass.delay", "manymanycommands.bypass.cooldown", "manymanycommands.bypass.quota"), names.takeLast(3))
        assertEquals(names.indexOf("manymanycommands.tpatoggle") + 1, names.indexOf("manymanycommands.status"), "/manymanycommands comes after the five families")
    }
}
