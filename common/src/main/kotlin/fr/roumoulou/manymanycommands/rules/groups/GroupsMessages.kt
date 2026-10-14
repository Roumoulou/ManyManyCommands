// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.message.MessageFamily

/** Les messages de `groups.md` des specs (chapitre 6). */
object GroupsMessages : MessageFamily("groups") {

    // L'état et le rechargement : le groupe absent des réglages où des joueurs restent inscrits, un et plusieurs, et le jeu nommé absent des règles.
    val STATUS_MISSING_GROUP_ONE = display("status.missing_group.one")
    val STATUS_MISSING_GROUP_MANY = display("status.missing_group.many")
    val STATUS_MISSING_RULE_SET = display("status.missing_rule_set")
}
