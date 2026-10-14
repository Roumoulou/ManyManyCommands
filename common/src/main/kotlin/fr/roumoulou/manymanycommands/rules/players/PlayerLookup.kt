// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.players

/** Qui `<player>` désigne dans une commande (cahier des charges, chapitre 2.7.2). */
object PlayerLookup {

    /**
     * Le joueur que [name] désigne, sans tenir compte de la casse : le joueur connecté qui porte ce nom, sinon le joueur du registre dont c'est le
     * dernier nom connu, le dernier connecté quand plusieurs le portent. `null` quand personne ne le porte. Un registre indisponible ne désigne
     * que les joueurs connectés.
     *
     * @param connected les joueurs connectés, chacun sous son nom du moment
     * @param registry le registre, `null` quand il est indisponible
     */
    fun resolve(name: String, connected: List<PlayerRef>, registry: PlayerRegistry?): PlayerRef? = connected.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: registry?.named(name)
}
