// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.permission.NodeCatalog
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode

/** Les verdicts du système du mod (`groups.md` des specs, chapitres 2 et 3) : ce que l'inscription d'un joueur et son groupe disent d'un nœud. */
object GroupRights {

    /**
     * Le verdict sur [node] pour un joueur : sa permission propre d'abord, son groupe ensuite, rien sinon, et le défaut du profil décide alors.
     *
     * Le nœud d'un jeu de règles se juge à part : un joueur qui a un jeu propre dans la famille, par un nœud accordé en propre, se voit retirer
     * tout autre jeu de la famille, celui que nomme son groupe compris, pour que l'ordre du fichier de règles ne fasse pas gagner le groupe ; sinon
     * le jeu que son groupe nomme pour la famille lui est accordé, et aucun autre.
     *
     * @param membership l'inscription du joueur, `null` quand il n'est inscrit nulle part ou que les inscriptions sont indisponibles
     * @param group le groupe qui vaut pour lui
     */
    fun verdict(membership: Membership?, group: GroupDefinition, node: PermissionNode): Boolean? {
        membership?.verdict(node.name)?.let { return it }
        val ruleSet = NodeCatalog.ruleSetOf(node) ?: return group.verdict(node.name)
        if (membership?.hasOwnRuleSet(ruleSet.family) == true) return false
        return if (group.ruleSets[ruleSet.family] == ruleSet.set) true else null
    }
}
