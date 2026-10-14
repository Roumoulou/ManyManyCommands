// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.message.MessageFamily

/** Les messages de `groups.md` des specs (chapitre 6). */
object GroupsMessages : MessageFamily("groups") {

    // La liste des groupes : une ligne par groupe, avec son nombre de membres, un et plusieurs.
    val LIST_GROUP_ONE = display("list.group.one")
    val LIST_GROUP_MANY = display("list.group.many")

    // Le détail d'un groupe : le jeu de chaque famille, les nœuds accordés, les nœuds retirés, les membres, chaque liste ayant sa forme vide.
    val INFO_RULE_SET = display("info.rule_set")
    val INFO_ALLOW = display("info.allow")
    val INFO_ALLOW_NONE = display("info.allow.none")
    val INFO_DENY = display("info.deny")
    val INFO_DENY_NONE = display("info.deny.none")
    val INFO_MEMBERS = display("info.members")
    val INFO_MEMBERS_NONE = display("info.members.none")

    // Le groupe d'un joueur, et son inscription, `group set` comme `group reset`.
    val GROUP_OF = display("group_of")
    val JOINED = success("joined")

    // Les permissions propres : le nœud accordé, retiré, effacé, et la liste d'un joueur, chaque nœud ou l'absence.
    val ALLOWED = success("allowed")
    val DENIED = success("denied")
    val UNSET = success("unset")
    val PERMISSION_ALLOWED = display("permission.allowed")
    val PERMISSION_DENIED = display("permission.denied")
    val PERMISSION_NONE = display("permission.none")

    // Les refus : les inscriptions indisponibles, le groupe, le joueur et le nœud inconnus, le système inactif sous un mod de permissions.
    val UNAVAILABLE = refusal("unavailable")
    val UNKNOWN_GROUP = refusal("unknown_group")
    val UNKNOWN_PLAYER = refusal("unknown_player")
    val UNKNOWN_NODE = refusal("unknown_node")
    val INACTIVE = refusal("inactive")

    // L'état et le rechargement : le groupe absent des réglages où des joueurs restent inscrits, un et plusieurs, et le jeu nommé absent des règles.
    val STATUS_MISSING_GROUP_ONE = display("status.missing_group.one")
    val STATUS_MISSING_GROUP_MANY = display("status.missing_group.many")
    val STATUS_MISSING_RULE_SET = display("status.missing_rule_set")
}
