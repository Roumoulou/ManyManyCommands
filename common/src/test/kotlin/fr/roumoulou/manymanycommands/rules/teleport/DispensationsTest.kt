// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.permission.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Les dispenses du délai, du cooldown et des quotas (cahier des charges, chapitre 2.4.4). */
class DispensationsTest {

    private val nodes = listOf(BypassNodes.DELAY, BypassNodes.COOLDOWN, BypassNodes.QUOTA)

    @Test
    fun `chaque dispense a son nœud`() {
        assertEquals(listOf("manymanycommands.bypass.delay", "manymanycommands.bypass.cooldown", "manymanycommands.bypass.quota"), nodes.map { it.name })
        assertEquals(listOf("manymanycommands:bypass/delay", "manymanycommands:bypass/cooldown", "manymanycommands:bypass/quota"), nodes.map { it.identifier })
    }

    @Test
    fun `le profil administrateur reçoit les trois nœuds par défaut, le profil joueur aucun`() {
        assertEquals(setOf(Profile.ADMINISTRATOR), nodes.map { it.defaultProfile }.toSet())
        assertEquals(Dispensations(fromDelay = true, fromCooldown = true, fromQuota = true), Dispensations.of { it.isGranted(verdict = null, isGameMaster = true) })
        assertEquals(Dispensations.NONE, Dispensations.of { it.isGranted(verdict = null, isGameMaster = false) })
    }

    @Test
    fun `la source des droits retire une dispense à un administrateur, et en accorde une à un joueur`() {
        val withoutCooldown = Dispensations.of { it.isGranted(verdict = if (it == BypassNodes.COOLDOWN) false else null, isGameMaster = true) }
        val withDelay = Dispensations.of { it.isGranted(verdict = if (it == BypassNodes.DELAY) true else null, isGameMaster = false) }

        assertEquals(Dispensations(fromDelay = true, fromCooldown = false, fromQuota = true), withoutCooldown)
        assertEquals(Dispensations(fromDelay = true), withDelay)
    }

    @Test
    fun `sans dispense, le délai, le cooldown et le quota sont ceux du jeu de règles`() {
        assertEquals(3L, Dispensations.NONE.delaySeconds(ruleDelay = 3))
        assertEquals(10L, Dispensations.NONE.cooldownSeconds(ruleCooldown = 10))
        assertEquals(0L, Dispensations(fromDelay = true, fromCooldown = true).delaySeconds(ruleDelay = 3))
        assertEquals(0L, Dispensations(fromDelay = true, fromCooldown = true).cooldownSeconds(ruleCooldown = 10))
    }
}
