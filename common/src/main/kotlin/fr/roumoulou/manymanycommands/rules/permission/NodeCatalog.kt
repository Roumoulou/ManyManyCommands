// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.permission

import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.rules.back.BackNodes
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.groups.GroupsNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import fr.roumoulou.manymanycommands.rules.teleport.BypassNodes
import fr.roumoulou.manymanycommands.rules.tpa.TpaNodes
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsNodes
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.WildNodes
import fr.roumoulou.manymanycommands.rules.wild.WildRules

/** Le jeu de règles que nomme un nœud `manymanycommands.rules.<famille>.<nom>` : la commande principale de la famille, et le nom du jeu. */
data class RuleSetNode(val family: String, val set: String)

/**
 * Les nœuds que le mod connaît (cahier des charges, chapitres 2.4.2 à 2.4.4) : ceux de chaque commande de l'arbre, les trois dispenses, la clé
 * d'accès d'un warp pour toute clé bien écrite, et le jeu d'une famille pour tout nom de jeu bien écrit. C'est lui qui juge un nœud écrit dans
 * `groups.json` ou dans `data\memberships.json`, et un nœud tapé dans `permission allow` ou `permission deny`.
 */
object NodeCatalog {

    private const val RULES_SEGMENT = "rules"
    private const val WARPS_SEGMENT = "warps"
    private const val ACCESS_SEGMENT = "access"

    /** Les nœuds des commandes, dans l'ordre de l'arbre, puis les trois dispenses. */
    val FIXED: List<PermissionNode> = HomesNodes.ALL + WarpsNodes.ALL + BackNodes.BACK + WildNodes.ALL + TpaNodes.ALL + ModNodes.ALL + GroupsNodes.ALL + listOf(BypassNodes.DELAY, BypassNodes.COOLDOWN, BypassNodes.QUOTA)

    /** Les familles qui ont des jeux de règles, par leur commande principale, dans l'ordre du cahier des charges. */
    val FAMILIES: List<String> = listOf(HomesRules, WarpsRules, BackRules, WildRules, TpaRules).map { it.family }

    private val fixedByName: Map<String, PermissionNode> = FIXED.associateBy { it.name }

    /** Le nœud que nomme [name], ou `null` quand le mod ne le connaît pas. */
    fun parse(name: String): PermissionNode? {
        fixedByName[name]?.let { return it }
        val prefix = "$MOD_ID."
        if (!name.startsWith(prefix)) return null
        val segments = name.removePrefix(prefix).split('.')
        if (segments.size != 3 || !NameRules.isLowercaseName(segments[2])) return null
        return when {
            segments[0] == WARPS_SEGMENT && segments[1] == ACCESS_SEGMENT -> WarpsNodes.access(segments[2])
            segments[0] == RULES_SEGMENT && segments[1] in FAMILIES -> PermissionNode(segments, defaultProfile = null)
            else -> null
        }
    }

    /** La famille et le jeu que nomme [node], `null` quand ce n'est pas le nœud d'un jeu de règles. */
    fun ruleSetOf(node: PermissionNode): RuleSetNode? = node.segments.takeIf { it.size == 3 && it[0] == RULES_SEGMENT }?.let { RuleSetNode(it[1], it[2]) }

    /** La famille et le jeu que nomme le nœud [name], `null` quand ce n'est pas celui d'un jeu de règles que le mod connaît. */
    fun ruleSetOf(name: String): RuleSetNode? = parse(name)?.let(::ruleSetOf)
}
