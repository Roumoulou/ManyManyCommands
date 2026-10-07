// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.message.Message

/**
 * `/warps list` en règles (`warps.md` des specs, chapitre 2.2) : l'en-tête, puis chaque warp auquel le lecteur a accès dans l'ordre de la
 * liste, ou la liste vide dite par un message. Pour qui a le nœud de `restrict`, un warp réservé porte sa clé.
 *
 * Chaque warp se clique pour le rejoindre, et son survol donne sa dimension et ses coordonnées entières ; la console, où rien ne se survole, les
 * reçoit en clair à la suite du nom.
 */
object WarpsListing {

    /** La commande qu'un clic sur [name] lance. */
    fun teleportCommand(name: String): String = "/warps teleport $name"

    /**
     * @param hasAccess le lecteur a le nœud d'une clé
     * @param showKeys le lecteur a le nœud de `restrict`
     */
    fun messages(warps: Warps, hasAccess: (key: String) -> Boolean, showKeys: Boolean): List<Message> = buildList {
        val names = warps.accessible(hasAccess)
        add(WarpsMessages.LIST_HEADER.with(names.size))
        if (names.isEmpty()) add(WarpsMessages.LIST_EMPTY.with())
        names.forEach { name ->
            val warp = warps[name] ?: error("An accessible name is one of the warps")
            val position = warp.position
            val line = if (showKeys && warp.key != null) WarpsMessages.LIST_RESERVED.with(name, warp.key) else WarpsMessages.LIST_WARP.with(name)
            add(line.interactive(teleportCommand(name), WarpsMessages.LIST_POSITION.with(position.dimension, position.blockX, position.blockY, position.blockZ)))
        }
    }
}
