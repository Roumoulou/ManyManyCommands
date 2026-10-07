// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Les refus propres de `/wild` (`wild.md` des specs, chapitre 5) : la dimension interdite, puis le quota, avec l'attente consultée seulement avec un quota. */
class WildRefusalsTest {

    /** Le jeu `default` d'un fichier qui interdit l'Overworld au départ, et lui seul. */
    private val noOverworld: RuleValues = WildRules.values(WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to mapOf(WildRules.forbiddenDepartureDimensions.key to listOf("minecraft:overworld"))))).default

    private fun first(ruleSet: RuleValues = WildRules.shipped.default, departure: String = "minecraft:overworld", quota: Quota? = null, wait: Long = 0): Message? =
        WildRefusals.first(ruleSet, departure, quota) { wait }

    @Test
    fun `quand rien ne refuse, la téléportation peut se demander`() {
        assertNull(first())
        assertNull(first(quota = Quota(uses = 3, periodSeconds = 60), wait = 0))
    }

    @Test
    fun `le Nether et l'End sont interdits par défaut, et la dimension interdite passe avant le quota`() {
        val quota = Quota(uses = 1, periodSeconds = 60)

        assertEquals(WildMessages.DEPARTURE_FORBIDDEN.with("minecraft:the_nether"), first(departure = "minecraft:the_nether", quota = quota, wait = 30))
        assertEquals(WildMessages.DEPARTURE_FORBIDDEN.with("minecraft:the_end"), first(departure = "minecraft:the_end", quota = quota, wait = 30))
        assertEquals(WildMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld"), first(noOverworld, quota = quota, wait = 30))
        assertNull(first(noOverworld, departure = "minecraft:the_nether"), "The file replaces the shipped dimensions")
    }

    @Test
    fun `le quota atteint dit le nombre d'usages et l'attente`() {
        assertEquals(WildMessages.QUOTA_REACHED_MANY.with(3L, DurationWording.text(30)), first(quota = Quota(uses = 3, periodSeconds = 60), wait = 30))
        assertEquals(WildMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(30)), first(quota = Quota(uses = 1, periodSeconds = 60), wait = 30))
    }

    @Test
    fun `l'attente ne se consulte que quand le joueur a un quota`() {
        assertNull(WildRefusals.first(WildRules.shipped.default, "minecraft:overworld", quota = null) { error("No quota, no wait to measure") })
    }
}
