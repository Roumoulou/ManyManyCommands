// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.message.Message

/** Ce que l'état et le rechargement disent des groupes (`groups.md` des specs, chapitres 2, 4 et 6), après les fichiers. */
object GroupsReport {

    /**
     * Les lignes des groupes : chaque groupe absent de [groups] où des joueurs de [memberships] restent inscrits, avec leur nombre, dans l'ordre
     * du fichier des inscriptions ; puis chaque jeu qu'un groupe nomme et que les règles de sa famille ne portent pas, dans l'ordre du fichier des
     * groupes. Vide quand rien ne manque.
     *
     * @param memberships les inscriptions, `null` quand leur fichier est indisponible
     * @param ruleSetsOf les jeux de règles en vigueur d'une famille, par sa commande principale
     */
    fun lines(groups: Groups, memberships: Memberships?, ruleSetsOf: (family: String) -> Set<String>): List<Message> = buildList {
        val counts = memberships?.let(MembershipsRules::countByGroup).orEmpty()
        counts.forEach { (group, count) ->
            if (groups[group] == null) add((if (count == 1) GroupsMessages.STATUS_MISSING_GROUP_ONE else GroupsMessages.STATUS_MISSING_GROUP_MANY).with(count, group))
        }
        groups.groups.forEach { (name, group) ->
            group.ruleSets.forEach { (family, set) -> if (set !in ruleSetsOf(family)) add(GroupsMessages.STATUS_MISSING_RULE_SET.with(name, set, family)) }
        }
    }
}
