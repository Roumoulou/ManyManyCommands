// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import fr.roumoulou.manymanycommands.rules.permission.Profile
import fr.roumoulou.manymanycommands.rules.ruleset.Quota

/** Les nœuds des dispenses (cahier des charges, chapitre 2.4.4). Le profil administrateur les reçoit par défaut, et la source des droits peut les retirer. */
object BypassNodes {

    val DELAY = PermissionNode(listOf("bypass", "delay"), Profile.ADMINISTRATOR)

    val COOLDOWN = PermissionNode(listOf("bypass", "cooldown"), Profile.ADMINISTRATOR)

    val QUOTA = PermissionNode(listOf("bypass", "quota"), Profile.ADMINISTRATOR)
}

/**
 * Ce dont un joueur est dispensé dans le déroulé d'une téléportation (cahier des charges, chapitre 2.4.4) : le délai d'immobilité, le cooldown,
 * les quotas. Les autres règles s'appliquent à lui comme à tout joueur.
 */
data class Dispensations(val fromDelay: Boolean = false, val fromCooldown: Boolean = false, val fromQuota: Boolean = false) {

    companion object {

        val NONE = Dispensations()

        /** Les dispenses de qui a les nœuds que [hasNode] lui reconnaît. */
        fun of(hasNode: (PermissionNode) -> Boolean): Dispensations = Dispensations(hasNode(BypassNodes.DELAY), hasNode(BypassNodes.COOLDOWN), hasNode(BypassNodes.QUOTA))
    }

    /** Le délai d'immobilité du joueur : celui de son jeu de règles, ou aucun. */
    fun delaySeconds(ruleDelay: Long): Long = if (fromDelay) 0 else ruleDelay

    /** Le cooldown du joueur : celui de son jeu de règles, ou aucun. */
    fun cooldownSeconds(ruleCooldown: Long): Long = if (fromCooldown) 0 else ruleCooldown

    /** Le quota du joueur : celui de son jeu de règles, ou aucun. Un usage dispensé ne se compte pas. */
    fun quota(ruleQuota: Quota?): Quota? = if (fromQuota) null else ruleQuota
}
