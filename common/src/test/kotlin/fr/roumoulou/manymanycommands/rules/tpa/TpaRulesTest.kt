// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.ruleset.RuleLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le tableau « Règles et réglages » des demandes de téléportation (`tpa.md` des specs, chapitre 4) : chaque ligne, sa valeur livrée et ses valeurs admises. */
class TpaRulesTest {

    private val shipped = TpaRules.shipped

    @Test
    fun `le tableau porte ses lignes dans l'ordre de la fiche, sans alias`() {
        assertEquals(listOf("enabled", "request-lifetime", "request-interval"), TpaRules.settings.map { it.key })
        assertEquals(listOf("forbidden-departure-dimensions", "delay", "cooldown", "quota"), TpaRules.rules.map { it.key })
    }

    @Test
    fun `la famille est active par défaut, et s'écrit active ou inactive`() {
        assertTrue(shipped.settings[TpaRules.enabled])
        RuleLines.assertAdmitsBothStates(TpaRules, TpaRules.enabled)
    }

    @Test
    fun `une demande vit 60 secondes par défaut, et sa durée de vie s'écrit de 1 ou plus`() {
        assertEquals(60L, shipped.settings[TpaRules.requestLifetime])
        RuleLines.assertAdmitsFrom(TpaRules, TpaRules.requestLifetime, min = 1)
    }

    @Test
    fun `l'intervalle entre deux demandes au même joueur vaut 60 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(60L, shipped.settings[TpaRules.requestInterval])
        RuleLines.assertAdmitsFrom(TpaRules, TpaRules.requestInterval, min = 0)
    }

    @Test
    fun `le voyageur part de toute dimension par défaut, et toute liste de dimensions s'écrit`() {
        assertEquals(emptySet<String>(), shipped.default[TpaRules.forbiddenDepartureDimensions])
        RuleLines.assertAdmitsAnyDimensionList(TpaRules, TpaRules.forbiddenDepartureDimensions)
    }

    @Test
    fun `le délai d'immobilité vaut 3 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3L, shipped.default[TpaRules.delay])
        RuleLines.assertAdmitsFrom(TpaRules, TpaRules.delay, min = 0)
    }

    @Test
    fun `le cooldown vaut 10 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(10L, shipped.default[TpaRules.cooldown])
        RuleLines.assertAdmitsFrom(TpaRules, TpaRules.cooldown, min = 0)
    }

    @Test
    fun `les demandes n'ont pas de quota par défaut, et s'écrivent sans quota ou avec 1 usage ou plus par plage de 1 seconde ou plus`() {
        assertNull(shipped.default[TpaRules.quota])
        RuleLines.assertAdmitsNoneOrAQuota(TpaRules, TpaRules.quota)
    }
}
