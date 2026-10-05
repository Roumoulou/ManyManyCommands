// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

/** Les valeurs en vigueur d'un ensemble de règles : celles d'un jeu, ou les réglages du serveur d'une famille. */
data class RuleValues(private val values: Map<Rule<*, *>, Any?>) {

    /** La valeur de [rule]. Chaque valeur a le type de sa règle, parce que c'est [Rule.value] qui l'a rendue. */
    @Suppress("UNCHECKED_CAST")
    operator fun <VALUE> get(rule: Rule<*, VALUE>): VALUE {
        require(rule in values) { "Rule '${rule.key}' does not belong to these values" }
        return values[rule] as VALUE
    }
}

/**
 * Les règles en vigueur d'une famille (cahier des charges, chapitre 2.4.3) : ses réglages du serveur, et ses jeux de règles.
 *
 * @property sets les jeux, par leur nom, dans l'ordre du fichier. Le jeu `default` y est toujours, écrit ou non.
 */
data class FamilyRules(val settings: RuleValues, val sets: Map<String, RuleValues>) {

    val default: RuleValues get() = sets.getValue(RulesTable.DEFAULT_SET)
}

/**
 * Ce qu'un fichier de règles écrit, sans ce qu'il n'écrit pas : chaque carte ne porte que les clés présentes, dans l'ordre du fichier.
 *
 * @property schemaVersion la version de schéma, celle du mod quand le fichier ne l'écrit pas
 * @property settings les réglages du serveur écrits, par leur clé
 * @property sets les jeux écrits, par leur nom, et pour chacun ses règles écrites, par leur clé
 */
data class WrittenRules(val schemaVersion: Int = RulesTable.SCHEMA_VERSION, val settings: Map<String, Any?> = emptyMap(), val sets: Map<String, Map<String, Any?>> = emptyMap())
