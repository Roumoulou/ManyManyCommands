// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.wild

import fr.roumoulou.manymanycommands.game.teleport.ArrivalSurveyor
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.SearchOutcome
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.wild.Bounds
import fr.roumoulou.manymanycommands.rules.wild.Column
import fr.roumoulou.manymanycommands.rules.wild.SafePointRules
import fr.roumoulou.manymanycommands.rules.wild.SurfaceSurvey
import fr.roumoulou.manymanycommands.rules.wild.WildMessages
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import fr.roumoulou.manymanycommands.rules.wild.WildZone
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.ceil
import kotlin.random.Random

/** La recherche d'un point sûr dans le monde (`wild.md` des specs, chapitres 2 et 3). La décision, elle, est celle de [SafePointRules]. */
object SafePointSearcher {

    /** La zone du tirage : la couronne autour du point d'apparition du monde, aux distances des réglages de [settings]. */
    fun zone(server: MinecraftServer, settings: RuleValues): WildZone {
        val spawn = server.overworld().respawnData.pos()
        return WildZone(spawn.x.toLong(), spawn.z.toLong(), settings[WildRules.minDistance], settings[WildRules.maxDistance])
    }

    /** Les colonnes que la bordure de [level] admet : celles dont le centre est dans la bordure, qui exclut sa borne haute. */
    fun border(level: ServerLevel): Bounds {
        val border = level.worldBorder
        return Bounds(lowest(border.minX), highest(border.maxX), lowest(border.minZ), highest(border.maxZ))
    }

    /**
     * Cherche pour [player] un point sûr dans [zone], dans sa dimension : jusqu'à [SafePointRules.TRIES] colonnes tirées, chacune lue à sa
     * surface. Lire une colonne charge son chunk.
     *
     * @return le point trouvé, le refus de la bordure qui exclut toute la zone, sans rien tirer, ou celui des tirages épuisés
     */
    fun search(player: ServerPlayer, zone: WildZone, random: Random): SearchOutcome {
        val level = player.level()
        val border = border(level)
        if (zone.within(border).isEmpty()) return SearchOutcome.Refused(WildMessages.BORDER_TOO_NARROW.with())
        repeat(SafePointRules.TRIES) {
            val column = zone.draw(border, random) ?: error("A zone that the border lets something of has a column to draw")
            val location = surface(player, level, column)
            if (SafePointRules.isSafe(survey(player, location))) return SearchOutcome.Found(location)
        }
        return SearchOutcome.Refused(WildMessages.NO_SAFE_POINT.with())
    }

    /** Le point où un joueur se tient à la surface de [column] : au centre de la colonne, les pieds sur le plus haut bloc qui arrête, feuilles exceptées, le regard inchangé. */
    private fun surface(player: ServerPlayer, level: ServerLevel, column: Column): Location {
        val x = column.x.toInt()
        val z = column.z.toInt()
        // La hauteur ne se lit que dans un chunk chargé : sans lui, le jeu rendrait le fond du monde.
        level.getChunk(x shr 4, z shr 4)
        val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        return Location(level, x + 0.5, y.toDouble(), z + 0.5, player.yRot, player.xRot)
    }

    private fun survey(player: ServerPlayer, location: Location): SurfaceSurvey {
        val level = location.level
        val feet = BlockPos.containing(location.x, location.y, location.z)
        val ground = feet.below()
        return SurfaceSurvey(
            isBelowLogicalHeight = feet.y < level.logicalHeight,
            isGroundSolid = !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty && level.getFluidState(ground).isEmpty,
            isInWater = level.getFluidState(feet).`is`(FluidTags.WATER) || level.getFluidState(ground).`is`(FluidTags.WATER),
            arrival = ArrivalSurveyor.survey(player, location),
        )
    }

    /** La plus basse colonne dont le centre atteint [bound], une borne basse de la bordure. */
    private fun lowest(bound: Double): Long = ceil(bound - 0.5).toLong()

    /** La plus haute colonne dont le centre reste sous [bound], une borne haute de la bordure, qu'elle exclut. */
    private fun highest(bound: Double): Long = ceil(bound - 0.5).toLong() - 1
}
