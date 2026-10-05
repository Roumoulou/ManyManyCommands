// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import fr.roumoulou.manymanycommands.rules.teleport.ArrivalRules
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalSurvey
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.GameType

/** La lecture d'un point d'arrivée dans le monde (cahier des charges, chapitre 2.5.5). La décision, elle, est celle d'[ArrivalRules]. */
object ArrivalSurveyor {

    /** Deux boîtes qui se touchent ne se pénètrent pas : le joueur posé sur un bloc n'est pas dedans. */
    private const val CONTACT_MARGIN = 1.0E-7

    /**
     * Ce que le monde dit de [destination] pour [player].
     *
     * - L'arrivée est obstruée quand le joueur n'y tient dans aucune posture : c'est allongé qu'il prend le moins de place, et un bloc qui
     *   occupe cette place l'étoufferait.
     * - La lave et le feu se cherchent dans la place qu'il occupe debout.
     * - Le sol est le premier bloc qui l'arrête, l'eau celle qui le reçoit, depuis le bloc où sont ses pieds.
     *
     * Lire un bloc charge son chunk : c'est celui que la téléportation chargerait.
     */
    fun survey(player: ServerPlayer, destination: Location): ArrivalSurvey {
        val level = destination.level
        val lying = player.getDimensions(Pose.SWIMMING).makeBoundingBox(destination.x, destination.y, destination.z).deflate(CONTACT_MARGIN)
        val standing = player.getDimensions(Pose.STANDING).makeBoundingBox(destination.x, destination.y, destination.z).deflate(CONTACT_MARGIN)
        val feet = BlockPos.containing(destination.x, destination.y, destination.z)
        return ArrivalSurvey(
            isObstructed = !level.noBlockCollision(null, lying),
            isInLavaOrFire = BlockPos.betweenClosed(standing).any { level.getFluidState(it).`is`(FluidTags.LAVA) || level.getBlockState(it).`is`(BlockTags.FIRE) },
            supportDepth = (0..ArrivalRules.SUPPORT_DEPTH).firstOrNull { depth ->
                val below = feet.below(depth)
                !level.getBlockState(below).getCollisionShape(level, below).isEmpty || level.getFluidState(below).`is`(FluidTags.WATER)
            },
        )
    }

    /** Les joueurs en mode créatif ou spectateur sont dispensés du contrôle de l'arrivée. Le mode se lit là où le jeu le tient pour le joueur. */
    fun isExempt(player: ServerPlayer): Boolean = player.gameMode.gameModeForPlayer.let { it == GameType.CREATIVE || it == GameType.SPECTATOR }
}
