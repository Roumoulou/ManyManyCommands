// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.teleport.Spot
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/** Un endroit du jeu : sa dimension, ses coordonnées exactes et l'orientation du regard. */
data class Location(val level: ServerLevel, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {

    companion object {

        /** L'endroit où se tient [player]. */
        fun of(player: ServerPlayer): Location = Location(player.level(), player.x, player.y, player.z, player.yRot, player.xRot)

        /** L'endroit qu'un fichier de données écrit, sur [server] : `null` quand le serveur ne porte pas sa dimension. */
        fun of(server: MinecraftServer, position: Position): Location? = levelOf(server, position.dimension)?.let { Location(it, position.x, position.y, position.z, position.yaw, position.pitch) }

        /** La dimension que [dimension] identifie sur [server], `null` quand il ne la porte pas : un home peut nommer une dimension qui n'existe plus. */
        fun levelOf(server: MinecraftServer, dimension: String): ServerLevel? = Identifier.tryParse(dimension)?.let { server.getLevel(ResourceKey.create(Registries.DIMENSION, it)) }
    }

    /** L'identifiant de la dimension, celui qu'un fichier de règles écrit : `minecraft:the_nether`. */
    val dimension: String get() = level.dimension().identifier().toString()

    /** Le point, en valeurs, pour les règles du déroulé. */
    fun spot(): Spot = Spot(dimension, x, y, z)

    /** L'endroit, en valeurs, tel qu'un fichier de données l'écrit : un home, un warp. */
    fun toPosition(): Position = Position(dimension, x, y, z, yaw, pitch)
}
