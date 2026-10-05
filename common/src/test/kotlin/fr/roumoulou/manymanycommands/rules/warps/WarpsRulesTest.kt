// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.ruleset.RuleLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le tableau « Règles et réglages » des warps (`warps.md` des specs, chapitre 4) : chaque ligne, sa valeur livrée et ses valeurs admises. */
class WarpsRulesTest {

    private val shipped = WarpsRules.shipped

    @Test
    fun `le tableau porte ses lignes dans l'ordre de la fiche, sans limite ni dimension interdite aux warps`() {
        assertEquals(listOf("enabled", "aliases"), WarpsRules.settings.map { it.key })
        assertEquals(listOf("forbidden-departure-dimensions", "delay", "cooldown", "quota"), WarpsRules.rules.map { it.key })
    }

    @Test
    fun `la famille est active par défaut, et s'écrit active ou inactive`() {
        assertTrue(shipped.settings[WarpsRules.enabled])
        RuleLines.assertAdmitsBothStates(WarpsRules, WarpsRules.enabled)
    }

    @Test
    fun `les alias sont actifs par défaut, et s'écrivent actifs ou inactifs`() {
        assertTrue(shipped.settings[WarpsRules.aliases])
        RuleLines.assertAdmitsBothStates(WarpsRules, WarpsRules.aliases)
    }

    @Test
    fun `teleport se lance de toute dimension par défaut, et toute liste de dimensions s'écrit`() {
        assertEquals(emptySet<String>(), shipped.default[WarpsRules.forbiddenDepartureDimensions])
        RuleLines.assertAdmitsAnyDimensionList(WarpsRules, WarpsRules.forbiddenDepartureDimensions)
    }

    @Test
    fun `le délai d'immobilité vaut 3 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3L, shipped.default[WarpsRules.delay])
        RuleLines.assertAdmitsFrom(WarpsRules, WarpsRules.delay, min = 0)
    }

    @Test
    fun `le cooldown vaut 10 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(10L, shipped.default[WarpsRules.cooldown])
        RuleLines.assertAdmitsFrom(WarpsRules, WarpsRules.cooldown, min = 0)
    }

    @Test
    fun `les warps n'ont pas de quota par défaut, et s'écrivent sans quota ou avec 1 usage ou plus par plage de 1 seconde ou plus`() {
        assertNull(shipped.default[WarpsRules.quota])
        RuleLines.assertAdmitsNoneOrAQuota(WarpsRules, WarpsRules.quota)
    }
}
