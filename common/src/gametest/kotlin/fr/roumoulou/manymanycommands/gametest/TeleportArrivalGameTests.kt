// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.Situation
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalDanger
import fr.roumoulou.manymanycommands.rules.teleport.TeleportRefusal
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks

/**
 * L'arrivée dangereuse lue dans le monde (cahier des charges, chapitre 2.5.5) : chaque danger est bâti au bloc dans la structure du test, et le
 * joueur simulé, en mode survie, y lance une téléportation sans délai ni cooldown. Refusée, elle le laisse au départ. Admise, elle l'y mène.
 */
class TeleportArrivalGameTests {

    /** Le décor d'un test, et l'état du mod où il lance ses téléportations. */
    private class Scene(val helper: GameTestHelper, name: String) {

        val lab = Lab.open(name, clock = ManualClock())

        val stage = TeleportStage(helper)

        private val atOnce = TeleportStage.rules(delay = 0, cooldown = 0)

        /** Le joueur, reparti du départ, est refusé à [destination] pour [danger], et le refus lui dit la cause. */
        fun assertRefused(destination: Location, danger: ArrivalDanger, situation: Situation, what: String) {
            stage.place(stage.player, stage.departure)
            val replies = Harness.record(lab.runtime) {
                val refusal = lab.runtime.teleports.launch(stage.request(destination, atOnce)) ?: throw helper.assertionException("l'arrivée devait être refusée : $what")
                helper.assertValueEqual(refusal, TeleportRefusal.DangerousArrival(danger), what)
            }
            helper.assertValueEqual(replies, listOf(Delivery(stage.player, situation.with())), "le refus répondu au joueur : $what")
            stage.assertPlayerAt(stage.departure, "après le refus : $what")
        }

        /** Le joueur, reparti du départ, arrive à [destination]. */
        fun assertAllowed(destination: Location, what: String) {
            stage.place(stage.player, stage.departure)
            val refusal = lab.runtime.teleports.launch(stage.request(destination, atOnce))
            helper.assertTrue(refusal == null, "l'arrivée devait être admise : $what, refusée pour $refusal")
            stage.assertPlayerAt(destination, what)
        }
    }

    /**
     * Une arrivée obstruée est refusée : le joueur y serait pris dans la pierre. Un passage d'un bloc de haut ne l'est pas, le joueur y tenant
     * allongé.
     */
    @GameTest
    fun obstructedArrivalIsRefused(helper: GameTestHelper) {
        val scene = Scene(helper, "obstructed-arrival-is-refused")
        val stage = scene.stage
        val onTheFloor = stage.platform(5, 5)
        scene.assertAllowed(onTheFloor, "une arrivée libre, sur le sol")

        stage.set(5, 2, 5, Blocks.STONE)
        stage.set(5, 3, 5, Blocks.STONE)
        scene.assertRefused(onTheFloor, ArrivalDanger.OBSTRUCTED, CommonMessages.TELEPORT_ARRIVAL_OBSTRUCTED, "une arrivée dans la pierre")

        stage.set(5, 2, 5, Blocks.AIR)
        scene.assertAllowed(onTheFloor, "un passage d'un bloc de haut, la pierre au-dessus des pieds")
        helper.succeed()
    }

    /** Une arrivée dans la lave ou dans le feu est refusée. */
    @GameTest
    fun arrivalInLavaOrFireIsRefused(helper: GameTestHelper) {
        val scene = Scene(helper, "arrival-in-lava-or-fire-is-refused")
        val stage = scene.stage
        val inTheBasin = stage.platform(5, 5)
        // Un bassin : quatre parois autour du bloc d'arrivée, pour que la lave n'en sorte pas.
        listOf(4 to 5, 6 to 5, 5 to 4, 5 to 6).forEach { (x, z) -> stage.set(x, 2, z, Blocks.STONE) }

        stage.set(5, 2, 5, Blocks.LAVA)
        scene.assertRefused(inTheBasin, ArrivalDanger.LAVA_OR_FIRE, CommonMessages.TELEPORT_ARRIVAL_LAVA_OR_FIRE, "une arrivée dans la lave")

        stage.set(5, 2, 5, Blocks.FIRE)
        scene.assertRefused(inTheBasin, ArrivalDanger.LAVA_OR_FIRE, CommonMessages.TELEPORT_ARRIVAL_LAVA_OR_FIRE, "une arrivée dans le feu")

        stage.set(5, 2, 5, Blocks.AIR)
        scene.assertAllowed(inTheBasin, "le bassin vidé")
        helper.succeed()
    }

    /** Une arrivée sans sol ni eau dans les 3 blocs en dessous est refusée : le sol au troisième bloc l'admet, au quatrième non. */
    @GameTest
    fun arrivalWithoutGroundBelowIsRefused(helper: GameTestHelper) {
        val scene = Scene(helper, "arrival-without-ground-below-is-refused")
        val stage = scene.stage
        stage.platform(5, 5)

        scene.assertAllowed(stage.standing(5, TeleportStage.FLOOR + 3, 5), "le sol au troisième bloc sous les pieds")
        scene.assertRefused(stage.standing(5, TeleportStage.FLOOR + 4, 5), ArrivalDanger.VOID, CommonMessages.TELEPORT_ARRIVAL_VOID, "le sol au quatrième bloc sous les pieds")
        scene.assertRefused(stage.standing(5, 7, 1), ArrivalDanger.VOID, CommonMessages.TELEPORT_ARRIVAL_VOID, "une arrivée en l'air, sans rien dessous")
        helper.succeed()
    }

    /** L'eau reçoit le joueur comme le sol : sous l'arrivée dans les 3 blocs, ou là où il arrive. */
    @GameTest
    fun waterBelowTheArrivalStandsForGround(helper: GameTestHelper) {
        val scene = Scene(helper, "water-below-the-arrival-stands-for-ground")
        val stage = scene.stage
        // Un bassin d'un bloc d'eau, sans sol de pierre à l'aplomb de l'arrivée : un fond, quatre parois.
        stage.set(5, 0, 5, Blocks.STONE)
        listOf(4 to 5, 6 to 5, 5 to 4, 5 to 6).forEach { (x, z) -> stage.set(x, 1, z, Blocks.STONE) }
        stage.set(5, 1, 5, Blocks.WATER)

        scene.assertAllowed(stage.standing(5, 4, 5), "l'eau au troisième bloc sous les pieds")
        scene.assertRefused(stage.standing(5, 5, 5), ArrivalDanger.VOID, CommonMessages.TELEPORT_ARRIVAL_VOID, "l'eau au quatrième bloc sous les pieds")
        scene.assertAllowed(stage.standing(5, 1, 5), "une arrivée dans l'eau")
        stage.set(5, 1, 5, Blocks.AIR)
        helper.succeed()
    }

    /** Les joueurs en mode créatif ou spectateur sont dispensés du contrôle de l'arrivée, que le même joueur subit en mode survie. */
    @GameTest
    fun creativeAndSpectatorPlayersSkipTheArrivalCheck(helper: GameTestHelper) {
        val scene = Scene(helper, "creative-and-spectator-players-skip-the-arrival-check")
        val stage = scene.stage
        val overTheVoid = stage.standing(5, 7, 5)
        val inTheStone = stage.platform(5, 1)
        stage.set(5, 2, 1, Blocks.STONE)
        stage.set(5, 3, 1, Blocks.STONE)

        scene.assertRefused(overTheVoid, ArrivalDanger.VOID, CommonMessages.TELEPORT_ARRIVAL_VOID, "en mode survie, au-dessus du vide")
        scene.assertRefused(inTheStone, ArrivalDanger.OBSTRUCTED, CommonMessages.TELEPORT_ARRIVAL_OBSTRUCTED, "en mode survie, dans la pierre")

        stage.player.setGameMode(GameType.CREATIVE)
        scene.assertAllowed(overTheVoid, "en mode créatif, au-dessus du vide")
        scene.assertAllowed(inTheStone, "en mode créatif, dans la pierre")

        stage.player.setGameMode(GameType.SPECTATOR)
        scene.assertAllowed(overTheVoid, "en mode spectateur, au-dessus du vide")
        scene.assertAllowed(inTheStone, "en mode spectateur, dans la pierre")
        helper.succeed()
    }
}
