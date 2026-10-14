// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.FamilyData
import fr.roumoulou.manymanycommands.rules.data.Holdings
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.permission.NodeCatalog
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/**
 * L'inscription d'un joueur (`groups.md` des specs, chapitres 3 et 4) : son groupe, `null` quand il n'est inscrit nulle part, et ses permissions
 * propres, les nœuds accordés et retirés, qui l'emportent sur son groupe.
 */
data class Membership(val group: String? = null, val allow: Set<String> = emptySet(), val deny: Set<String> = emptySet()) : Holdings {

    /** Rien n'est tenu : le joueur suit `default` sans permission propre, et son entrée ne s'écrit pas. */
    override val isEmpty: Boolean get() = group == null && allow.isEmpty() && deny.isEmpty()

    /** Le verdict propre du joueur sur le nœud [node] : accordé, retiré, ou rien. */
    fun verdict(node: String): Boolean? = when (node) {
        in allow -> true
        in deny -> false
        else -> null
    }

    /** Le joueur a un jeu propre dans la famille [family] : un nœud de jeu de règles de cette famille parmi ses nœuds accordés (chapitre 3). */
    fun hasOwnRuleSet(family: String): Boolean = allow.any { NodeCatalog.ruleSetOf(it)?.family == family }

    /** L'inscription dans [group], à la place du précédent. `default` vaut nulle part. */
    fun inGroup(group: String): Membership = copy(group = group.takeIf { it != Groups.DEFAULT_GROUP })

    /** Le nœud [node] accordé en propre, et plus retiré. */
    fun allowing(node: String): Membership = copy(allow = allow + node, deny = deny - node)

    /** Le nœud [node] retiré en propre, et plus accordé. */
    fun denying(node: String): Membership = copy(allow = allow - node, deny = deny + node)

    /** La permission propre sur [node] effacée : le nœud revient au groupe. */
    fun without(node: String): Membership = copy(allow = allow - node, deny = deny - node)
}

/** Le contenu de `data\memberships.json` : l'inscription de chaque joueur, par le mécanisme commun des fichiers de données. Un joueur sans entrée suit `default`. */
typealias Memberships = FamilyData<Membership>

/**
 * L'entrée d'un joueur dans `data\memberships.json` telle qu'elle s'écrit (cahier des charges, chapitre 2.7.5) : son nom, son groupe, ses nœuds
 * accordés et retirés. Une clé qui manque vaut `null` : aucun groupe, aucun nœud.
 */
data class WrittenMembership(val name: String?, val group: String?, val allow: List<String>?, val deny: List<String>?) {

    companion object {

        const val GROUP_KEY = "group"
        const val ALLOW_KEY = "allow"
        const val DENY_KEY = "deny"

        /** Une liste vide ne s'écrit pas. */
        fun of(entry: PlayerEntry<Membership>): WrittenMembership =
            WrittenMembership(entry.name, entry.holdings.group, entry.holdings.allow.toList().ifEmpty { null }, entry.holdings.deny.toList().ifEmpty { null })
    }

    /** Les constats de l'entrée écrite à [path], sans leur ligne : un nom de groupe invalide, un nœud que le mod ne connaît pas, un nœud à la fois accordé et retiré. */
    fun findings(path: String): List<FileFinding> = buildList {
        if (group != null && !NameRules.isLowercaseName(group)) add(notAllowed(FindingPath.property(path, GROUP_KEY), group, CommonMessages.ALLOWED_LOWERCASE_NAME.with()))
        addAll(NodeLists.findings(FindingPath.property(path, ALLOW_KEY), allow.orEmpty(), ruleSetsAllowed = true, alsoIn = emptyList()))
        addAll(NodeLists.findings(FindingPath.property(path, DENY_KEY), deny.orEmpty(), ruleSetsAllowed = true, alsoIn = allow.orEmpty()))
    }

    /** L'entrée d'une écriture que [findings] admet. Un groupe `default` écrit vaut nulle part. */
    fun value(): PlayerEntry<Membership> = PlayerEntry(name, Membership(group?.takeIf { it != Groups.DEFAULT_GROUP }, allow.orEmpty().toSet(), deny.orEmpty().toSet()))
}

/** `data\memberships.json` en règles : ses constats et son contenu, par le mécanisme commun des fichiers de données. */
object MembershipsRules {

    val fileName: String = DataRules.serverFileName("memberships")

    fun findings(written: WrittenData<WrittenMembership>): List<FileFinding> = DataRules.findings(written) { path, entry -> entry.findings(path) }

    /** Le contenu d'un fichier que [findings] juge valide. */
    fun data(written: WrittenData<WrittenMembership>): Memberships = DataRules.players(written, WrittenMembership::value)

    fun written(data: Memberships): WrittenData<WrittenMembership> = DataRules.written(data, WrittenMembership::of)

    /** Le nombre de joueurs inscrits dans chaque groupe nommé, dans l'ordre du fichier. Les joueurs qui ne sont inscrits nulle part n'y comptent pas. */
    fun countByGroup(data: Memberships): Map<String, Int> = data.players.values.mapNotNull { it.holdings.group }.groupingBy { it }.eachCount()
}
