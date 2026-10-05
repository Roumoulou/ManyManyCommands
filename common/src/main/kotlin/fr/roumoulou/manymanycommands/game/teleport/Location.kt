// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import fr.roumoulou.manymanycommands.rules.teleport.Spot
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/** Un endroit du jeu : sa dimension, ses coordonnées exactes et l'orientation du regard. */
data class Location(val level: ServerLevel, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {

    companion object {

        /** L'endroit où se tient [player]. */
        fun of(player: ServerPlayer): Location = Location(player.level(), player.x, player.y, player.z, player.yRot, player.xRot)
    }

    /** L'identifiant de la dimension, celui qu'un fichier de règles écrit : `minecraft:the_nether`. */
    val dimension: String get() = level.dimension().identifier().toString()

    /** Le point, en valeurs, pour les règles du déroulé. */
    fun spot(): Spot = Spot(dimension, x, y, z)
}
