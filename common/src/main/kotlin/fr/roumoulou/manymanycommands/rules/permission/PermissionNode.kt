// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.permission

import fr.roumoulou.manymanycommands.MOD_ID

/**
 * Le nœud de permission d'une commande (cahier des charges, chapitre 2.4.2), nommé d'après son chemin dans l'arbre.
 *
 * @property segments le chemin, sans le nom du mod : `[homes, create]`
 * @property defaultProfile le profil qui reçoit le nœud tant que la source des droits ne se prononce pas
 */
data class PermissionNode(val segments: List<String>, val defaultProfile: Profile) {

    companion object {

        private const val ROOT_COMMAND = "manymanycommands"
        private const val ROOT_COMMAND_ALONE = "status"

        /**
         * Le nœud de la commande que forment [literals], ses mots sans ses arguments : `homes`, `player`, `delete` pour
         * `/homes player <player> delete <name>`. Les commandes de `/manymanycommands` ne répètent pas son nom, et `/manymanycommands` seul
         * prend `status`. Un alias n'a pas de nœud à lui : il porte celui de la commande qu'il remplace.
         */
        fun ofCommand(defaultProfile: Profile, vararg literals: String): PermissionNode {
            require(literals.isNotEmpty()) { "A command path has at least its root literal" }
            val path = if (literals.first() == ROOT_COMMAND) literals.drop(1) else literals.toList()
            return PermissionNode(path.ifEmpty { listOf(ROOT_COMMAND_ALONE) }, defaultProfile)
        }
    }

    /** Le nom du nœud, celui des specs : `manymanycommands.homes.create`. */
    val name: String get() = "$MOD_ID.${segments.joinToString(".")}"

    /** Le nœud en identifiant du jeu, la forme que le module de permissions de la Fabric API attend : `manymanycommands:homes/create`. */
    val identifier: String get() = "$MOD_ID:${segments.joinToString("/")}"

    /**
     * La source des droits accorde le nœud ou le retire. Tant qu'elle ne se prononce pas, le défaut du profil s'applique.
     *
     * @param verdict ce que dit la source des droits, `null` quand elle ne se prononce pas
     * @param isGameMaster la source de la commande a le niveau des maîtres du jeu
     */
    fun isGranted(verdict: Boolean?, isGameMaster: Boolean): Boolean = verdict ?: (defaultProfile == Profile.PLAYER || isGameMaster)
}
