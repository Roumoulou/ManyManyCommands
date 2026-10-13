// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode

/**
 * Le tableau « Règles et réglages » de la fiche d'une famille, et le fichier de règles qui en découle (cahier des charges, chapitres 2.4.3 et
 * 2.7.1) : ses réglages du serveur, puis ses jeux de règles sous `rules`.
 *
 * Une table déclare chaque ligne de son tableau une fois, dans l'ordre de la fiche, qui est aussi celui où le fichier créé l'écrit. Tout le reste
 * s'en déduit : la forme du fichier, ses valeurs livrées, ses constats, et ce qu'un jeu reprend du jeu `default`.
 *
 * @param family la commande principale de la famille : `homes`, `warps`, `back`, `wild`, `tpa`
 */
abstract class RulesTable(val family: String) {

    companion object {

        /** La version de schéma que ce mod écrit et sait lire : un fichier qui en annonce une plus récente est invalide. */
        const val SCHEMA_VERSION = 1

        const val SCHEMA_VERSION_KEY = "schema-version"
        const val RULES_KEY = "rules"

        /** Le jeu qui existe toujours et vaut pour tout joueur. */
        const val DEFAULT_SET = "default"
    }

    // Les deux listes précèdent enabled, que setting() y range à son initialisation : l'ordre de déclaration est celui de l'initialisation.
    private val declaredSettings = mutableListOf<Rule<*, *>>()

    private val declaredRules = mutableListOf<Rule<*, *>>()

    /** Le nom du fichier, tel que les messages le donnent : son chemin dans le dossier du mod. */
    val fileName: String = "rules/$family.json"

    /** Les réglages du serveur, dans l'ordre du tableau. */
    val settings: List<Rule<*, *>> get() = declaredSettings

    /** Les règles d'un jeu, dans l'ordre du tableau. */
    val rules: List<Rule<*, *>> get() = declaredRules

    /** L'interrupteur de la famille (cahier des charges, chapitre 2.10), lu au seul démarrage du serveur. */
    val enabled: SwitchRule = setting(SwitchRule("enabled", shipped = true))

    /** L'interrupteur des alias, lu au seul démarrage du serveur. `null` pour la famille qui n'a pas d'alias. */
    open val aliases: SwitchRule? get() = null

    // Les quatre règles que le déroulé d'une téléportation lit dans le jeu du joueur (cahier des charges, chapitre 2.5).

    abstract val forbiddenDepartureDimensions: DimensionsRule

    abstract val delay: IntegerRule

    abstract val cooldown: IntegerRule

    abstract val quota: QuotaRule

    /** Les règles livrées : celles d'un fichier qui n'écrit rien, et celles qui tiennent la place d'un fichier refusé au démarrage. */
    val shipped: FamilyRules get() = values(WrittenRules())

    protected fun <RULE : Rule<*, *>> setting(rule: RULE): RULE = rule.also { declaredSettings += it }

    protected fun <RULE : Rule<*, *>> rule(rule: RULE): RULE = rule.also { declaredRules += it }

    /** Le fichier que le mod écrit quand il manque : chaque réglage du serveur et le jeu `default` entier, à leurs valeurs livrées. */
    fun shippedFile(): WrittenRules = WrittenRules(SCHEMA_VERSION, shippedWritten(settings), mapOf(DEFAULT_SET to shippedWritten(rules)))

    /**
     * Les constats d'un fichier lu, sans leur ligne (cahier des charges, chapitres 2.7.3 et 2.7.4). Vide quand le fichier est valide.
     *
     * Les valeurs se jugent une à une. Ce qui tient à plusieurs valeurs ([crossFindings]) ne se juge qu'une fois chacune admise.
     */
    fun findings(written: WrittenRules): List<FileFinding> {
        val findings = buildList {
            if (written.schemaVersion > SCHEMA_VERSION) add(FileFinding(SCHEMA_VERSION_KEY, null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(written.schemaVersion, SCHEMA_VERSION)))
            addAll(findingsOf(settings, written.settings, parent = ""))
            written.sets.forEach { (name, set) ->
                val path = FindingPath.entry(RULES_KEY, name)
                if (!NameRules.isLowercaseName(name)) add(notAllowed(path, name, CommonMessages.ALLOWED_LOWERCASE_NAME.with()))
                addAll(findingsOf(rules, set, parent = path))
            }
        }
        return findings.ifEmpty { crossFindings(values(written).settings) }
    }

    /**
     * Les règles en vigueur d'un fichier que [findings] juge valide.
     *
     * Un réglage absent, comme une règle absente du jeu `default`, prend sa valeur livrée. Une règle absente d'un autre jeu prend celle du jeu
     * `default`. Le jeu `default` existe même quand le fichier ne l'écrit pas : il vient alors après les jeux écrits.
     */
    fun values(written: WrittenRules): FamilyRules {
        val default = valuesOf(rules, written.sets[DEFAULT_SET].orEmpty(), inherited = shippedValues(rules))
        val sets = LinkedHashMap<String, RuleValues>()
        written.sets.forEach { (name, set) -> sets[name] = if (name == DEFAULT_SET) default else valuesOf(rules, set, inherited = default) }
        sets.putIfAbsent(DEFAULT_SET, default)
        return FamilyRules(valuesOf(settings, written.settings, inherited = shippedValues(settings)), sets)
    }

    /** Le nœud qui attribue le jeu [name] : `manymanycommands.rules.<famille>.<nom>`. Aucun profil ne le reçoit par défaut. */
    fun setNode(name: String): PermissionNode = PermissionNode(listOf("rules", family, name), defaultProfile = null)

    /**
     * Le jeu d'un joueur dans la famille (cahier des charges, chapitre 2.4.3) : le premier, dans l'ordre du fichier, dont il a le nœud, et le jeu
     * `default` quand il n'en a aucun.
     *
     * @param hasNode ce que la source des droits répond pour le joueur
     */
    fun setFor(rules: FamilyRules, hasNode: (PermissionNode) -> Boolean): RuleValues = rules.sets.entries.firstOrNull { (name, _) -> hasNode(setNode(name)) }?.value ?: rules.default

    /**
     * Les clés des réglages lus au seul démarrage du serveur, l'activation de la famille et celle de ses alias, dont la valeur n'est plus celle du
     * démarrage (`manymanycommands.md` des specs, chapitre 2).
     */
    fun awaitingRestart(inForce: FamilyRules, startup: FamilyRules): List<String> = listOfNotNull(enabled, aliases).filter { inForce.settings[it] != startup.settings[it] }.map { it.key }

    /** Les constats qui tiennent à plusieurs réglages du serveur à la fois, chacun déjà admis. */
    protected open fun crossFindings(settings: RuleValues): List<FileFinding> = emptyList()

    private fun findingsOf(rules: List<Rule<*, *>>, written: Map<String, Any?>, parent: String): List<FileFinding> =
        rules.filter { it.key in written }.flatMap { it.findingsOfRead(FindingPath.property(parent, it.key), written[it.key]) }

    private fun valuesOf(rules: List<Rule<*, *>>, written: Map<String, Any?>, inherited: RuleValues): RuleValues =
        RuleValues(rules.associateWith { if (it.key in written) it.valueOfRead(written[it.key], inherited[it]) else inherited[it] })

    private fun shippedValues(rules: List<Rule<*, *>>): RuleValues = RuleValues(rules.associateWith { it.shipped })

    private fun shippedWritten(rules: List<Rule<*, *>>): Map<String, Any?> = rules.associate { it.key to it.shippedWritten() }
}
