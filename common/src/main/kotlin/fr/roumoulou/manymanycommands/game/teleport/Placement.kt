// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.BlockPos

/**
 * Le placement d'une commande d'administration (cahier des charges, chapitre 2.9) : `[<pos> [<rotation> [<dimension>]]]`, les arguments du jeu,
 * avec leur complétion, et ce qu'ils valent quand ils manquent : la position et l'orientation de la source, sa dimension. `~` y désigne la
 * position ou l'orientation de la source, celle du point d'apparition du monde depuis la console, et un `x` ou un `z` entier vise le centre du
 * bloc, un `y` entier restant au niveau du bloc, comme pour `/tp`.
 */
object Placement {

    const val POS = "pos"
    const val ROTATION = "rotation"
    const val DIMENSION = "dimension"

    /** Ce que la commande a écrit du placement. */
    data class Given(val pos: Boolean = false, val rotation: Boolean = false, val dimension: Boolean = false)

    /** Pose les trois arguments à la suite de [parent], chacun facultatif, dans l'ordre : [command] reçoit le contexte et ce qui est écrit. */
    fun <BUILDER : ArgumentBuilder<CommandSourceStack, BUILDER>> attach(parent: BUILDER, command: (CommandContext<CommandSourceStack>, Given) -> Int): BUILDER = parent
        .executes { command(it, Given()) }
        .then(
            Commands.argument(POS, Vec3Argument.vec3()).executes { command(it, Given(pos = true)) }
                .then(
                    Commands.argument(ROTATION, RotationArgument.rotation()).executes { command(it, Given(pos = true, rotation = true)) }
                        .then(Commands.argument(DIMENSION, DimensionArgument.dimension()).executes { command(it, Given(pos = true, rotation = true, dimension = true)) }),
                ),
        )

    /** L'endroit que le placement désigne : ce qui est écrit, et la source pour le reste. Une dimension inconnue est refusée par l'argument du jeu. */
    fun resolve(context: CommandContext<CommandSourceStack>, given: Given): Location {
        val source = context.source
        val level = if (given.dimension) DimensionArgument.getDimension(context, DIMENSION) else source.level
        val position = if (given.pos) Vec3Argument.getVec3(context, POS) else source.position
        // Le jeu range l'orientation dans un Vec2 : son x est le pitch, son y le yaw.
        val rotation = if (given.rotation) RotationArgument.getRotation(context, ROTATION).getRotation(source) else source.rotation
        return Location(level, position.x, position.y, position.z, rotation.y, rotation.x)
    }

    /** Le point est dans la bordure du monde et dans la hauteur de sa dimension (cahier des charges, chapitre 2.9). */
    fun isWithinWorld(location: Location): Boolean =
        location.level.worldBorder.isWithinBounds(location.x, location.z) && !location.level.isOutsideBuildHeight(BlockPos.containing(location.x, location.y, location.z))
}
