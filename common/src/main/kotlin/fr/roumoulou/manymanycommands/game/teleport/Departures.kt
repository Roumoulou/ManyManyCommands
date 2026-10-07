// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Leashable

/** Le départ d'un joueur, seul (cahier des charges, chapitre 2.5.7) : le déroulé à son arrivée, l'envoi par un administrateur. */
object Departures {

    /**
     * Fait partir [player] pour [to]. Le jeu lui fait quitter sa monture. Ses laisses tombent ici, parce que le jeu garderait en laisse un
     * animal resté à portée de l'arrivée. Ses passagers descendent ici aussi : le jeu ne laisse aucune entité monter sur un joueur, mais
     * emmènerait celle qu'un autre mod y aurait mise.
     *
     * @return `false` quand le jeu refuse, ce qu'il ne fait que pour un joueur déjà retiré du monde
     */
    fun leave(player: ServerPlayer, to: Location): Boolean {
        player.ejectPassengers()
        Leashable.leashableLeashedTo(player).forEach { it.dropLeash() }
        return player.teleportTo(to.level, to.x, to.y, to.z, emptySet(), to.yaw, to.pitch, true)
    }
}
