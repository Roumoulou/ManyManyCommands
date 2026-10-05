// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le jeu de règles d'un joueur dans une famille (cahier des charges, chapitre 2.4.3). */
class PlayerRuleSetTest {

    /** Les homes d'un fichier qui écrit, dans cet ordre, les jeux `vip`, `default` et `staff`, chacun avec sa limite. */
    private val rules = HomesRules.values(
        WrittenRules(sets = mapOf("vip" to mapOf(HomesRules.maxHomes.writes(10)), "default" to mapOf(HomesRules.maxHomes.writes(2)), "staff" to mapOf(HomesRules.maxHomes.writes(50)))),
    )

    /** La limite de homes du joueur à qui la source des droits accorde [granted]. */
    private fun maxHomesWith(vararg granted: PermissionNode): Long = HomesRules.setFor(rules) { it in granted }[HomesRules.maxHomes]

    @Test
    fun `un jeu s'attribue par le nœud rules, suivi de la famille et de son nom`() {
        assertEquals("manymanycommands.rules.homes.vip", HomesRules.setNode("vip").name)
        assertEquals("manymanycommands:rules/homes/vip", HomesRules.setNode("vip").identifier)
        assertEquals("manymanycommands.rules.warps.default", WarpsRules.setNode("default").name)
    }

    @Test
    fun `aucun profil ne reçoit le nœud d'un jeu par défaut, pas même l'administrateur`() {
        val node = HomesRules.setNode("vip")

        assertNull(node.defaultProfile)
        assertFalse(node.isGranted(verdict = null, isGameMaster = false))
        assertFalse(node.isGranted(verdict = null, isGameMaster = true))
        assertTrue(node.isGranted(verdict = true, isGameMaster = false))
    }

    @Test
    fun `un joueur sans nœud de jeu a le jeu default`() {
        assertEquals(2L, maxHomesWith())
    }

    @Test
    fun `un joueur qui reçoit le nœud d'un jeu a ce jeu`() {
        assertEquals(10L, maxHomesWith(HomesRules.setNode("vip")))
        assertEquals(50L, maxHomesWith(HomesRules.setNode("staff")))
    }

    @Test
    fun `un joueur qui en reçoit plusieurs prend le premier dans l'ordre du fichier`() {
        assertEquals(10L, maxHomesWith(HomesRules.setNode("staff"), HomesRules.setNode("vip")))
        assertEquals(2L, maxHomesWith(HomesRules.setNode("staff"), HomesRules.setNode("default")), "The default set is written before the staff set")
    }

    @Test
    fun `le nœud d'un jeu ne vaut que pour sa famille`() {
        assertEquals(2L, maxHomesWith(WarpsRules.setNode("vip")))
    }

    @Test
    fun `le nœud d'un jeu que le fichier ne porte pas laisse le jeu default`() {
        assertEquals(2L, maxHomesWith(HomesRules.setNode("gold")))
    }
}
