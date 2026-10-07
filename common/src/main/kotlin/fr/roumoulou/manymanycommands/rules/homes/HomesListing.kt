// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.message.Message

/**
 * `/homes list` en règles (`homes.md` des specs, chapitre 3.4) : l'en-tête, puis chaque home dans l'ordre de la liste, le home par défaut marqué,
 * ou la liste vide dite par un message.
 *
 * Chaque home se clique pour le rejoindre, et son survol donne sa dimension et ses coordonnées entières ; la console, où rien ne se survole, les
 * reçoit en clair à la suite du nom.
 */
object HomesListing {

    /** La commande qu'un clic sur [name] lance. */
    fun teleportCommand(name: String): String = "/homes teleport $name"

    /**
     * @param max la limite de homes du joueur, celle de son jeu de règles
     */
    fun messages(homes: PlayerHomes, max: Long): List<Message> = buildList {
        add(HomesMessages.LIST_HEADER.with(homes.homes.size, max))
        if (homes.isEmpty) add(HomesMessages.LIST_EMPTY.with())
        homes.sortedNames().forEach { name ->
            val position = homes[name] ?: error("A sorted name is one of the homes")
            val situation = if (name == homes.defaultHome) HomesMessages.LIST_DEFAULT_HOME else HomesMessages.LIST_HOME
            add(situation.with(name).interactive(teleportCommand(name), HomesMessages.LIST_POSITION.with(position.dimension, position.blockX, position.blockY, position.blockZ)))
        }
    }
}
