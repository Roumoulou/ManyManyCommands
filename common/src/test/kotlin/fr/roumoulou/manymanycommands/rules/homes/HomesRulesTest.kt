// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.ruleset.RuleLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le tableau « Règles et réglages » des homes (`homes.md` des specs, chapitre 5) : chaque ligne, sa valeur livrée et ses valeurs admises. */
class HomesRulesTest {

    private val shipped = HomesRules.shipped

    @Test
    fun `le tableau porte ses lignes dans l'ordre de la fiche`() {
        assertEquals(listOf("enabled", "aliases"), HomesRules.settings.map { it.key })
        assertEquals(listOf("max-homes", "forbidden-dimensions", "forbidden-departure-dimensions", "delay", "cooldown", "quota"), HomesRules.rules.map { it.key })
    }

    @Test
    fun `la famille est active par défaut, et s'écrit active ou inactive`() {
        assertTrue(shipped.settings[HomesRules.enabled])
        RuleLines.assertAdmitsBothStates(HomesRules, HomesRules.enabled)
    }

    @Test
    fun `les alias sont actifs par défaut, et s'écrivent actifs ou inactifs`() {
        assertTrue(shipped.settings[HomesRules.aliases])
        RuleLines.assertAdmitsBothStates(HomesRules, HomesRules.aliases)
    }

    @Test
    fun `la limite de homes vaut 3 par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3L, shipped.default[HomesRules.maxHomes])
        RuleLines.assertAdmitsFrom(HomesRules, HomesRules.maxHomes, min = 0)
    }

    @Test
    fun `aucune dimension n'est interdite aux homes par défaut, et toute liste de dimensions s'écrit`() {
        assertEquals(emptySet<String>(), shipped.default[HomesRules.forbiddenDimensions])
        RuleLines.assertAdmitsAnyDimensionList(HomesRules, HomesRules.forbiddenDimensions)
    }

    @Test
    fun `teleport se lance de toute dimension par défaut, et toute liste de dimensions s'écrit`() {
        assertEquals(emptySet<String>(), shipped.default[HomesRules.forbiddenDepartureDimensions])
        RuleLines.assertAdmitsAnyDimensionList(HomesRules, HomesRules.forbiddenDepartureDimensions)
    }

    @Test
    fun `le délai d'immobilité vaut 3 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(3L, shipped.default[HomesRules.delay])
        RuleLines.assertAdmitsFrom(HomesRules, HomesRules.delay, min = 0)
    }

    @Test
    fun `le cooldown vaut 10 secondes par défaut, et s'écrit de 0 ou plus`() {
        assertEquals(10L, shipped.default[HomesRules.cooldown])
        RuleLines.assertAdmitsFrom(HomesRules, HomesRules.cooldown, min = 0)
    }

    @Test
    fun `les homes n'ont pas de quota par défaut, et s'écrivent sans quota ou avec 1 usage ou plus par plage de 1 seconde ou plus`() {
        assertNull(shipped.default[HomesRules.quota])
        RuleLines.assertAdmitsNoneOrAQuota(HomesRules, HomesRules.quota)
    }
}
