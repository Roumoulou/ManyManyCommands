// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.NodeCatalog
import fr.roumoulou.manymanycommands.rules.permission.RuleSetNode
import fr.roumoulou.manymanycommands.rules.players.PlayerRef
import fr.roumoulou.manymanycommands.rules.players.PlayerRegistry
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import java.util.UUID

/** L'issue d'une commande des groupes : un refus, un affichage, ou les inscriptions changées, avec le message qui le dit. */
sealed interface GroupsOutcome {

    data class Refused(val message: Message) : GroupsOutcome

    data class Shown(val messages: List<Message>) : GroupsOutcome

    data class Changed(val data: Memberships, val message: Message) : GroupsOutcome
}

/**
 * Les branches `group` et `permission` de `/manymanycommands` en règles (`groups.md` des specs, chapitre 5) : chaque commande reçoit les groupes,
 * les inscriptions et le registre, et rend son issue. Le joueur d'une commande arrive déjà résolu, par le registre.
 */
object GroupsAdmin {

    /**
     * `group list` : une ligne par groupe de `groups.json`, dans son ordre, avec son nombre de membres : les joueurs inscrits pour un groupe
     * nommé, et pour `default` les joueurs du registre qui le suivent, inscrits nulle part ou dans un groupe absent des réglages.
     */
    fun list(groups: Groups, memberships: Memberships, registry: PlayerRegistry?): List<Message> {
        val counts = MembershipsRules.countByGroup(memberships)
        return groups.names.map { name ->
            val count = if (name == Groups.DEFAULT_GROUP) membersOf(name, groups, memberships, registry).size else counts[name] ?: 0
            (if (count == 1) GroupsMessages.LIST_GROUP_ONE else GroupsMessages.LIST_GROUP_MANY).with(name, count)
        }
    }

    /** `group info <group>` : le jeu de chaque famille, `default` quand le groupe n'en nomme pas, les nœuds accordés, les nœuds retirés, les membres par leur nom. */
    fun info(name: String, groups: Groups, memberships: Memberships, registry: PlayerRegistry?): GroupsOutcome {
        val group = groups[name] ?: return GroupsOutcome.Refused(GroupsMessages.UNKNOWN_GROUP.with(name))
        val members = membersOf(name, groups, memberships, registry).map { nameOf(it, registry) }
        return GroupsOutcome.Shown(buildList {
            NodeCatalog.FAMILIES.forEach { family -> add(GroupsMessages.INFO_RULE_SET.with(family, group.ruleSets[family] ?: RulesTable.DEFAULT_SET)) }
            add(if (group.allow.isEmpty()) GroupsMessages.INFO_ALLOW_NONE.with() else GroupsMessages.INFO_ALLOW.with(group.allow.joinToString(", ")))
            add(if (group.deny.isEmpty()) GroupsMessages.INFO_DENY_NONE.with() else GroupsMessages.INFO_DENY.with(group.deny.joinToString(", ")))
            add(if (members.isEmpty()) GroupsMessages.INFO_MEMBERS_NONE.with() else GroupsMessages.INFO_MEMBERS.with(members.joinToString(", ")))
        })
    }

    /**
     * Les membres du groupe [name] : pour un groupe nommé, les joueurs qui y sont inscrits ; pour `default`, les joueurs du registre qui le
     * suivent, inscrits nulle part ou dans un groupe absent des réglages, et aucun quand le registre est indisponible.
     */
    fun membersOf(name: String, groups: Groups, memberships: Memberships, registry: PlayerRegistry?): List<UUID> =
        if (name == Groups.DEFAULT_GROUP) registry?.players?.keys?.filter { effectiveGroup(groups, memberships[it]?.holdings) == Groups.DEFAULT_GROUP }.orEmpty()
        else memberships.players.filterValues { it.holdings.group == name }.keys.toList()

    /** Le nom du groupe où un joueur inscrit sous [membership] est traité : le sien quand `groups.json` le porte, `default` sinon. */
    fun effectiveGroup(groups: Groups, membership: Membership?): String = membership?.group?.takeIf { groups[it] != null } ?: Groups.DEFAULT_GROUP

    /** Le nom de [player] : son dernier nom connu au registre, ou son UUID quand le registre ne le connaît pas. */
    fun nameOf(player: UUID, registry: PlayerRegistry?): String = registry?.get(player)?.name ?: player.toString()

    /** `group set <player> <group>` : le joueur inscrit dans le groupe, à la place du précédent, ses permissions propres gardées ; `default` vaut nulle part. */
    fun set(player: PlayerRef, group: String, groups: Groups, memberships: Memberships): GroupsOutcome {
        if (groups[group] == null) return GroupsOutcome.Refused(GroupsMessages.UNKNOWN_GROUP.with(group))
        return changed(player, memberships, GroupsMessages.JOINED.with(player.name, group)) { it.inGroup(group) }
    }

    /** `group reset <player>` : le joueur renvoyé dans `default`, qui existe toujours. */
    fun reset(player: PlayerRef, memberships: Memberships): GroupsOutcome.Changed =
        changed(player, memberships, GroupsMessages.JOINED.with(player.name, Groups.DEFAULT_GROUP)) { it.inGroup(Groups.DEFAULT_GROUP) }

    /** `group of <player>` : le groupe où le joueur est traité. */
    fun of(player: PlayerRef, groups: Groups, memberships: Memberships): Message = GroupsMessages.GROUP_OF.with(player.name, effectiveGroup(groups, memberships[player.id]?.holdings))

    /**
     * `permission allow <player> <node>` : le nœud accordé en propre, et plus retiré. Un nœud que le mod ne connaît pas est refusé, comme le jeu
     * d'une famille que son fichier de règles ne porte pas ; la clé d'accès d'un warp s'accepte pour toute clé bien écrite.
     *
     * @param hasRuleSet le jeu que nomme un nœud de jeu de règles existe dans les règles en vigueur de sa famille
     */
    fun allow(player: PlayerRef, node: String, memberships: Memberships, hasRuleSet: (RuleSetNode) -> Boolean): GroupsOutcome =
        withKnownNode(node, hasRuleSet) { known -> changed(player, memberships, GroupsMessages.ALLOWED.with(known, player.name)) { it.allowing(known) } }

    /** `permission deny <player> <node>` : le nœud retiré en propre, et plus accordé, sous les mêmes refus que [allow]. */
    fun deny(player: PlayerRef, node: String, memberships: Memberships, hasRuleSet: (RuleSetNode) -> Boolean): GroupsOutcome =
        withKnownNode(node, hasRuleSet) { known -> changed(player, memberships, GroupsMessages.DENIED.with(known, player.name)) { it.denying(known) } }

    /** `permission unset <player> <node>` : la permission propre effacée, quel que soit le nœud, qui revient au groupe. */
    fun unset(player: PlayerRef, node: String, memberships: Memberships): GroupsOutcome.Changed =
        changed(player, memberships, GroupsMessages.UNSET.with(player.name, node)) { it.without(node) }

    /** `permission list <player>` : une ligne par nœud accordé, puis par nœud retiré, ou l'absence de permission propre. */
    fun permissions(player: PlayerRef, memberships: Memberships): List<Message> {
        val membership = memberships[player.id]?.holdings ?: Membership()
        val lines = membership.allow.map { GroupsMessages.PERMISSION_ALLOWED.with(it) } + membership.deny.map { GroupsMessages.PERMISSION_DENIED.with(it) }
        return lines.ifEmpty { listOf(GroupsMessages.PERMISSION_NONE.with(player.name)) }
    }

    private inline fun withKnownNode(node: String, hasRuleSet: (RuleSetNode) -> Boolean, block: (String) -> GroupsOutcome): GroupsOutcome {
        val known = NodeCatalog.parse(node) ?: return GroupsOutcome.Refused(GroupsMessages.UNKNOWN_NODE.with(node))
        val ruleSet = NodeCatalog.ruleSetOf(known)
        if (ruleSet != null && !hasRuleSet(ruleSet)) return GroupsOutcome.Refused(GroupsMessages.UNKNOWN_NODE.with(node))
        return block(known.name)
    }

    /** Les inscriptions où [player] a son inscription changée par [change], sous son nom du moment ; une inscription vide ne s'y écrit pas. */
    private inline fun changed(player: PlayerRef, memberships: Memberships, message: Message, change: (Membership) -> Membership): GroupsOutcome.Changed =
        GroupsOutcome.Changed(memberships.with(player.id, PlayerEntry(player.name, change(memberships[player.id]?.holdings ?: Membership()))), message)
}
