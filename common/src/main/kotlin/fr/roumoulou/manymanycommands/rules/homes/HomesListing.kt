// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.message.Message

/**
 * `/homes list` en règles (`homes.md` des specs, chapitre 3.4) : l'en-tête, puis chaque home dans l'ordre de la liste, le home par défaut marqué,
 * ou la liste vide dite par un message. La liste d'un joueur nommé, pour l'administrateur (chapitre 4), s'en distingue par son en-tête, sans
 * limite, et par le clic de chaque home, qui lance la commande de la branche.
 *
 * Chaque home se clique pour le rejoindre, et son survol donne sa dimension et ses coordonnées entières ; la console, où rien ne se survole, les
 * reçoit en clair à la suite du nom.
 */
object HomesListing {

    /** La commande qu'un clic sur [name] lance. */
    fun teleportCommand(name: String): String = "/homes teleport $name"

    /** La commande qu'un clic sur [name], dans la liste des homes de [owner], lance pour l'administrateur. */
    fun teleportCommand(owner: String, name: String): String = "/homes player $owner teleport $name"

    /**
     * La liste des homes (`homes.md` des specs, chapitre 3.4) : l'en-tête, puis une ligne par home dans l'ordre de la liste, ou la liste vide.
     *
     * @param max la limite de homes du joueur, celle de son jeu de règles
     */
    fun messages(homes: PlayerHomes, max: Long): List<Message> = lines(homes, HomesMessages.LIST_HEADER.with(homes.homes.size, max), ::teleportCommand)

    /** La liste des homes de [owner], pour l'administrateur (chapitre 4) : l'en-tête nomme le propriétaire et compte, sans limite. */
    fun messagesOf(owner: String, homes: PlayerHomes): List<Message> = lines(homes, HomesMessages.LIST_HEADER_OF_PLAYER.with(owner, homes.homes.size)) { teleportCommand(owner, it) }

    private fun lines(homes: PlayerHomes, header: Message, click: (String) -> String): List<Message> = buildList {
        add(header)
        if (homes.isEmpty) add(HomesMessages.LIST_EMPTY.with())
        homes.sortedNames().forEach { name ->
            val position = homes[name] ?: error("A sorted name is one of the homes")
            val situation = if (name == homes.defaultHome) HomesMessages.LIST_DEFAULT_HOME else HomesMessages.LIST_HOME
            add(situation.with(name).interactive(click(name), HomesMessages.LIST_POSITION.with(position.dimension, position.blockX, position.blockY, position.blockZ)))
        }
    }
}
