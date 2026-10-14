// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.permission

import fr.roumoulou.manymanycommands.rules.groups.GroupRights
import fr.roumoulou.manymanycommands.rules.groups.Groups
import fr.roumoulou.manymanycommands.rules.groups.Memberships
import fr.roumoulou.manymanycommands.rules.permission.PermissionNode
import net.minecraft.commands.CommandSourceStack

/**
 * La source des droits du système du mod (`groups.md` des specs, chapitres 1 à 3) : pour le joueur que désigne une source, ses permissions
 * propres, puis son groupe, décident d'un nœud. Sans joueur, la console ou un bloc de commande, elle se tait, et le défaut du profil décide.
 * Inactive, sous un mod de permissions, elle se tait toujours.
 *
 * @param active le système du mod est la source des droits : `permission-source` du démarrage vaut `internal`
 * @param groups les groupes en vigueur
 * @param memberships les inscriptions, `null` quand leur fichier est indisponible : tout joueur est alors membre de `default`, sans permission propre
 */
class InternalPermissionSource(private val active: () -> Boolean, private val groups: () -> Groups, private val memberships: () -> Memberships?) : PermissionSource {

    override fun verdict(source: CommandSourceStack, node: PermissionNode): Boolean? {
        if (!active()) return null
        val player = source.player ?: return null
        val membership = memberships()?.get(player.uuid)?.holdings
        return GroupRights.verdict(membership, groups().effective(membership?.group), node)
    }
}
