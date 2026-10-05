// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.RuleLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le tableau « Règles et réglages » du retour (`back.md` des specs, chapitre 4) : chaque ligne, sa valeur livrée et ses valeurs admises. */
class BackRulesTest {

    private val shipped = BackRules.shipped

    @Test
    fun `le tableau porte ses lignes dans l'ordre de la fiche, sans alias`() {
        assertEquals(listOf("enabled"), BackRules.settings.map { it.key })
        assertEquals(listOf("sources", "forbidden-departure-dimensions", "delay", "cooldown", "quota"), BackRules.rules.map { it.key })
    }

    @Test
    fun `la famille est active par défaut, et s'écrit active ou inactive`() {
        assertTrue(shipped.settings[BackRules.enabled])
        RuleLines.assertAdmitsBothStates(BackRules, BackRules.enabled)
    }

    @Test
    fun `les sources sont toutes actives par défaut, sauf la mort`() {
        val expected = mapOf("homes" to true, "warps" to true, "wild" to true, "tpa" to true, "back" to true, "send" to true, "death" to false)

        assertEquals(expected, shipped.default[BackRules.sources])
        assertEquals(expected.keys.toList(), BackRules.sources.switches.toList(), "The sources are written in the order of the spec")
        assertEquals(expected.keys.toList(), BackSource.entries.map { it.key })
    }

    @Test
    fun `chaque source a son interrupteur, qui s'écrit actif ou inactif`() {
        BackSource.entries.forEach { source ->
            listOf(true, false).forEach { state ->
                assertEquals(emptyList<FileFinding>(), RuleLines.findings(BackRules, BackRules.sources, mapOf(source.key to state)), "Source ${source.key} must admit $state")
            }
        }
    }

    @Test
    fun `back se lance de toute dimension par défaut, et toute liste de dimensions s'écrit`() {
        assertEquals(emptySet<String>(), shipped.default[BackRules.forbiddenDepartureDimensions])
        RuleLines.assertAdmitsAnyDimensionList(BackRules, BackRules.forbiddenDepartureDimensions)
    }

    @Test
    fun `le délai d'immobilité vaut 3 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3L, shipped.default[BackRules.delay])
        RuleLines.assertAdmitsFrom(BackRules, BackRules.delay, min = 0)
    }

    @Test
    fun `le cooldown vaut 10 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(10L, shipped.default[BackRules.cooldown])
        RuleLines.assertAdmitsFrom(BackRules, BackRules.cooldown, min = 0)
    }

    @Test
    fun `le retour n'a pas de quota par défaut, et s'écrit sans quota ou avec 1 usage ou plus par plage de 1 seconde ou plus`() {
        assertNull(shipped.default[BackRules.quota])
        RuleLines.assertAdmitsNoneOrAQuota(BackRules, BackRules.quota)
    }
}
