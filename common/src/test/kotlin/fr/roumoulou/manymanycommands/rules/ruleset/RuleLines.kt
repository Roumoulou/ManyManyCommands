// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Les valeurs admises d'une ligne du tableau « Règles et réglages » d'une fiche, éprouvées par le fichier de sa table : la valeur s'écrit là où
 * la ligne vit, dans les réglages du serveur ou dans le jeu `default`.
 */
object RuleLines {

    private val none = emptyList<FileFinding>()

    /** Les constats du fichier qui n'écrit que [written] pour [rule]. */
    fun findings(table: RulesTable, rule: Rule<*, *>, written: Any?): List<FileFinding> {
        val line = mapOf(rule.key to written)
        return table.findings(if (rule in table.settings) WrittenRules(settings = line) else WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to line)))
    }

    /** Le chemin de [rule] dans le fichier de [table]. */
    fun path(table: RulesTable, rule: Rule<*, *>): String = if (rule in table.settings) rule.key else "rules[default].${rule.key}"

    /** Actif ou inactif : les deux s'admettent. */
    fun assertAdmitsBothStates(table: RulesTable, rule: SwitchRule) {
        listOf(true, false).forEach { assertEquals(none, findings(table, rule, it), "${rule.key} must admit $it") }
    }

    /** [min] ou plus : [min] s'admet, et ce qui est juste en dessous se refuse, à son chemin, avec ses valeurs admises. */
    fun assertAdmitsFrom(table: RulesTable, rule: IntegerRule, min: Long) {
        assertEquals(none, findings(table, rule, min), "${rule.key} must admit $min")
        val refusal = FileFinding(path(table, rule), null, CommonMessages.FINDING_NOT_ALLOWED.with(min - 1, CommonMessages.ALLOWED_AT_LEAST.with(min)))
        assertEquals(listOf(refusal), findings(table, rule, min - 1), "${rule.key} must refuse ${min - 1}")
    }

    /** Toute liste de dimensions : vide, du jeu, d'un datapack. Ce qui n'est pas un identifiant se refuse, à son rang dans la liste. */
    fun assertAdmitsAnyDimensionList(table: RulesTable, rule: DimensionsRule) {
        assertEquals(none, findings(table, rule, emptyList<String>()), "${rule.key} must admit an empty list")
        assertEquals(none, findings(table, rule, listOf("minecraft:the_nether", "mymod:moon", "the_end")), "${rule.key} must admit any dimension")
        val refusal = FileFinding("${path(table, rule)}[1]", null, CommonMessages.FINDING_NOT_ALLOWED.with("The Nether", CommonMessages.ALLOWED_DIMENSION.with()))
        assertEquals(listOf(refusal), findings(table, rule, listOf("minecraft:overworld", "The Nether")), "${rule.key} must refuse what is not an identifier")
    }

    /** Aucun quota, ou 1 usage ou plus par plage de 1 seconde ou plus. */
    fun assertAdmitsNoneOrAQuota(table: RulesTable, rule: QuotaRule) {
        assertEquals(none, findings(table, rule, null), "${rule.key} must admit no quota")
        assertEquals(none, findings(table, rule, WrittenQuota(uses = 1, period = 1, clock = null)), "${rule.key} must admit 1 use per second")
        assertEquals(none, findings(table, rule, WrittenQuota(uses = 5, period = 86_400, clock = "playtime")), "${rule.key} must admit a quota on the play time")
        val atLeastOne = CommonMessages.ALLOWED_AT_LEAST.with(1L)
        val noUse = FileFinding("${path(table, rule)}.uses", null, CommonMessages.FINDING_NOT_ALLOWED.with(0L, atLeastOne))
        val noPeriod = FileFinding("${path(table, rule)}.period", null, CommonMessages.FINDING_NOT_ALLOWED.with(0L, atLeastOne))
        assertEquals(listOf(noUse, noPeriod), findings(table, rule, WrittenQuota(uses = 0, period = 0, clock = null)), "${rule.key} must refuse a quota without a use or without a period")
    }
}
