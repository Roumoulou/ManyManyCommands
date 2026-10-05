// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.ruleset.RuleLines
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le tableau « Règles et réglages » de la téléportation aléatoire (`wild.md` des specs, chapitre 6) : chaque ligne, sa valeur livrée et ses valeurs admises. */
class WildRulesTest {

    private val shipped = WildRules.shipped

    private fun distanceFindings(min: Long, max: Long): List<FileFinding> = WildRules.findings(WrittenRules(settings = mapOf(WildRules.minDistance.writes(min), WildRules.maxDistance.writes(max))))

    @Test
    fun `le tableau porte ses lignes dans l'ordre de la fiche, le quota avant le délai`() {
        assertEquals(listOf("enabled", "aliases", "min-distance", "max-distance", "timed-window"), WildRules.settings.map { it.key })
        assertEquals(listOf("forbidden-departure-dimensions", "quota", "delay", "cooldown"), WildRules.rules.map { it.key })
    }

    @Test
    fun `la famille est active par défaut, et s'écrit active ou inactive`() {
        assertTrue(shipped.settings[WildRules.enabled])
        RuleLines.assertAdmitsBothStates(WildRules, WildRules.enabled)
    }

    @Test
    fun `l'alias est actif par défaut, et s'écrit actif ou inactif`() {
        assertTrue(shipped.settings[WildRules.aliases])
        RuleLines.assertAdmitsBothStates(WildRules, WildRules.aliases)
    }

    @Test
    fun `la distance minimale vaut 4000 blocs par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(4_000L, shipped.settings[WildRules.minDistance])
        RuleLines.assertAdmitsFrom(WildRules, WildRules.minDistance, min = 0)
    }

    @Test
    fun `la distance minimale reste inférieure à la distance maximale`() {
        assertEquals(emptyList<FileFinding>(), distanceFindings(min = 9_999, max = 10_000))
        val refusal = FileFinding("min-distance", null, CommonMessages.FINDING_NOT_ALLOWED.with(10_000L, CommonMessages.ALLOWED_RANGE.with(0L, 9_999L)))
        assertEquals(listOf(refusal), distanceFindings(min = 10_000, max = 10_000))
    }

    @Test
    fun `la distance minimale se juge aussi contre la distance maximale livrée, et la maximale contre la minimale livrée`() {
        val aboveShippedMax = FileFinding("min-distance", null, CommonMessages.FINDING_NOT_ALLOWED.with(20_000L, CommonMessages.ALLOWED_RANGE.with(0L, 9_999L)))
        assertEquals(listOf(aboveShippedMax), WildRules.findings(WrittenRules(settings = mapOf(WildRules.minDistance.writes(20_000)))))

        val underShippedMin = FileFinding("min-distance", null, CommonMessages.FINDING_NOT_ALLOWED.with(4_000L, CommonMessages.ALLOWED_RANGE.with(0L, 1_999L)))
        assertEquals(listOf(underShippedMin), WildRules.findings(WrittenRules(settings = mapOf(WildRules.maxDistance.writes(2_000)))))
    }

    @Test
    fun `la distance maximale vaut 10000 blocs par défaut, et s'écrit au-dessus de la distance minimale`() {
        assertEquals(10_000L, shipped.settings[WildRules.maxDistance])
        assertEquals(emptyList<FileFinding>(), distanceFindings(min = 0, max = 1))
        assertEquals(emptyList<FileFinding>(), distanceFindings(min = 4_000, max = 4_001))
        val refusal = FileFinding("max-distance", null, CommonMessages.FINDING_NOT_ALLOWED.with(0L, CommonMessages.ALLOWED_AT_LEAST.with(1L)))
        assertEquals(listOf(refusal), distanceFindings(min = 0, max = 0))
    }

    @Test
    fun `le créneau du tirage commun dure 10 secondes par défaut, et s'écrit de 1 ou plus`() {
        assertEquals(10L, shipped.settings[WildRules.timedWindow])
        RuleLines.assertAdmitsFrom(WildRules, WildRules.timedWindow, min = 1)
    }

    @Test
    fun `wild ne se lance ni du Nether ni de l'End par défaut, et toute liste de dimensions s'écrit`() {
        assertEquals(setOf("minecraft:the_nether", "minecraft:the_end"), shipped.default[WildRules.forbiddenDepartureDimensions])
        RuleLines.assertAdmitsAnyDimensionList(WildRules, WildRules.forbiddenDepartureDimensions)
    }

    @Test
    fun `le quota vaut 3 usages par plage de 8 heures réelles par défaut, et s'écrit sans quota ou avec 1 usage ou plus par plage de 1 seconde ou plus`() {
        assertEquals(Quota(uses = 3, periodSeconds = 28_800, clock = QuotaClock.REAL), shipped.default[WildRules.quota])
        RuleLines.assertAdmitsNoneOrAQuota(WildRules, WildRules.quota)
    }

    @Test
    fun `le délai d'immobilité vaut 3 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3L, shipped.default[WildRules.delay])
        RuleLines.assertAdmitsFrom(WildRules, WildRules.delay, min = 0)
    }

    @Test
    fun `le cooldown vaut 1 heure par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3_600L, shipped.default[WildRules.cooldown])
        RuleLines.assertAdmitsFrom(WildRules, WildRules.cooldown, min = 0)
    }
}
