// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Les refus qu'une téléportation vers un endroit connu partage d'une famille à l'autre : la dimension de départ, la dimension de l'arrivée, le quota, dans cet ordre, avec les messages de la famille. */
class DestinationRefusalsTest {

    private fun text(name: String, vararg arguments: Any) = Message(MessageType.REFUSAL, TranslatableText("test.$name", arguments.toList()))

    private val texts = DestinationTexts({ text("departure", it) }, { text("missing", it) }, { uses, wait -> text("quota", uses, wait) })

    private val noDeparture: RuleValues = HomesRules.values(WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to mapOf(HomesRules.forbiddenDepartureDimensions.key to listOf("minecraft:overworld"))))).default

    private fun first(ruleSet: RuleValues = HomesRules.shipped.default, destination: String = "minecraft:the_nether", known: Boolean = true, quota: Quota? = null, wait: Long = 0): Message? =
        DestinationRefusals.first(HomesRules, ruleSet, "minecraft:overworld", destination, { known }, quota, { wait }, texts)

    @Test
    fun `quand rien ne refuse, la téléportation peut se demander`() {
        assertNull(first())
        assertNull(first(quota = Quota(uses = 2, periodSeconds = 60), wait = 0))
    }

    @Test
    fun `la dimension de départ interdite passe avant la dimension introuvable et le quota`() {
        assertEquals(text("departure", "minecraft:overworld"), first(noDeparture, known = false, quota = Quota(1, 60), wait = 30))
    }

    @Test
    fun `la dimension de l'arrivée introuvable passe avant le quota, et nomme la dimension`() {
        assertEquals(text("missing", "mymod:moon"), first(destination = "mymod:moon", known = false, quota = Quota(1, 60), wait = 30))
    }

    @Test
    fun `le quota atteint dit le nombre d'usages et l'attente`() {
        assertEquals(text("quota", 3L, DurationWording.text(30)), first(quota = Quota(uses = 3, periodSeconds = 60), wait = 30))
    }

    @Test
    fun `sans dimension de départ à contrôler, la dimension interdite ne refuse pas, et le reste se juge`() {
        assertNull(DestinationRefusals.first(HomesRules, noDeparture, departure = null, destination = "minecraft:the_nether", isDimensionKnown = { true }, quota = null, quotaWaitSeconds = { 0 }, texts = texts))
        assertEquals(text("missing", "mymod:moon"), DestinationRefusals.first(HomesRules, noDeparture, null, "mymod:moon", { false }, null, { 0 }, texts))
    }

    @Test
    fun `l'attente ne se consulte que quand le joueur a un quota`() {
        assertNull(DestinationRefusals.first(HomesRules, HomesRules.shipped.default, "minecraft:overworld", "minecraft:the_nether", { true }, quota = null, quotaWaitSeconds = { error("No quota, no wait to measure") }, texts = texts))
    }
}
