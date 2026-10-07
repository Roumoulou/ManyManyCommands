// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Le retour en valeurs (`back.md` des specs, chapitres 2 et 3) : les sources qui enregistrent, l'ordre des refus de `/back`, les positions en mémoire. */
class ReturnRulesTest {

    private val overworld = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val moon = Position("mymod:moon", 0.0, 64.0, 0.0, 0f, 0f)

    /** Le jeu `default` d'un fichier qui n'écrit que les sources données et les dimensions d'où `/back` ne se lance pas. */
    private fun rules(sources: Map<String, Boolean> = emptyMap(), forbiddenDeparture: List<String> = emptyList()): RuleValues {
        val set = mapOf(BackRules.sources.key to sources, BackRules.forbiddenDepartureDimensions.key to forbiddenDeparture)
        return BackRules.values(WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to set))).default
    }

    private fun refused(teleport: BackTeleport) = assertInstanceOf(BackTeleport.Refused::class.java, teleport).message

    /** `/back` pour un joueur dans l'Overworld dont le serveur porte toutes les dimensions sauf la lune. */
    private fun teleport(position: Position?, ruleSet: RuleValues = rules(), quota: Quota? = null, wait: Long = 0): BackTeleport =
        ReturnRules.teleport(position, "minecraft:overworld", ruleSet, { it != "mymod:moon" }, quota) { wait }

    // ── Ce qui enregistre (chapitre 2) ──

    @Test
    fun `par défaut, chaque source enregistre, sauf la mort`() {
        BackSource.entries.forEach { source -> assertEquals(source != BackSource.DEATH, ReturnRules.records(BackRules.shipped.default, source), "Source ${source.key}") }
    }

    @Test
    fun `le jeu de règles du joueur active ou désactive une source, les autres gardant leur valeur du jeu default`() {
        val ruleSet = rules(sources = mapOf("homes" to false, "death" to true))

        assertFalse(ReturnRules.records(ruleSet, BackSource.HOMES))
        assertTrue(ReturnRules.records(ruleSet, BackSource.DEATH))
        assertTrue(ReturnRules.records(ruleSet, BackSource.BACK))
    }

    @Test
    fun `un joueur n'a qu'une position de retour, la dernière enregistrée, et elle suit son UUID`() {
        val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
        val alex = UUID.fromString("7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e")
        val positions = ReturnPositions()

        assertNull(positions[steve])
        positions.record(steve, overworld)
        positions.record(steve, moon)

        assertEquals(moon, positions[steve])
        assertNull(positions[alex])
    }

    // ── La commande (chapitre 3) ──

    @Test
    fun `back refuse dans l'ordre de sa fiche, l'absence de position, la dimension de départ, la dimension introuvable, puis le quota`() {
        val noDeparture = rules(forbiddenDeparture = listOf("minecraft:overworld"))
        val quota = Quota(uses = 1, periodSeconds = 60)

        assertEquals(BackMessages.NO_POSITION.with(), refused(teleport(null, noDeparture, quota, wait = 30)))
        assertEquals(BackMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld"), refused(teleport(moon, noDeparture, quota, wait = 30)))
        assertEquals(BackMessages.DIMENSION_MISSING.with("mymod:moon"), refused(teleport(moon, quota = quota, wait = 30)))
        assertEquals(BackMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(30)), refused(teleport(overworld, quota = quota, wait = 30)))
        assertEquals(BackTeleport.Target(overworld), teleport(overworld, quota = quota, wait = 0))
    }

    @Test
    fun `le quota atteint se dit au singulier pour 1 usage, au pluriel sinon`() {
        val wait = DurationWording.text(90)

        assertEquals(BackMessages.QUOTA_REACHED_ONE.with(1L, wait), BackMessages.quotaReached(1, wait))
        assertEquals(BackMessages.QUOTA_REACHED_MANY.with(3L, wait), BackMessages.quotaReached(3, wait))
    }
}
