// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.gametest.TeleportStage.Outcome
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.teleport.CancellationCause
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.chat.Component

/**
 * Les causes d'annulation d'une téléportation en attente (cahier des charges, chapitre 2.5.3), chacune par son test.
 *
 * Ce que le tick constate (le joueur s'éloigne, change de dimension, se déconnecte) se joue sur un état propre au test, que le test fait tiquer.
 * Ce qu'un événement du jeu annonce (les dégâts, la mort) et le tick lui-même se jouent sur l'état du serveur : c'est le point d'entrée du mod
 * qui y branche le jeu. Le délai y est d'une heure, pour qu'aucune lenteur de la machine ne le laisse s'écouler.
 */
class TeleportCancellationGameTests {

    private companion object {

        /** Un délai que l'horloge de la machine ne laisse pas s'écouler le temps d'un test. */
        const val ONE_HOUR = 3_600L
    }

    /** Une téléportation en attente dans [runtime], pour le joueur d'un décor neuf, et ce que sa famille en apprend. */
    private class Pending(helper: GameTestHelper, val runtime: ModRuntime, delay: Long = 3) {

        val stage = TeleportStage(helper)

        val destination = stage.platform(5, 5)

        val outcome = Outcome()

        init {
            helper.assertTrue(runtime.teleports.launch(stage.request(destination, TeleportStage.rules(delay = delay), listener = outcome)) == null, "le lancement n'est pas refusé")
            helper.assertTrue(runtime.teleports.hasPending(stage.player), "la téléportation est en attente")
        }
    }

    /** L'annulation de [pending] pour [cause] : son message au joueur, en refus, la famille prévenue, et plus rien en attente. */
    private fun assertCancelled(helper: GameTestHelper, pending: Pending, cause: CancellationCause, messages: List<Delivery>) {
        val situation = cause.situation ?: throw helper.assertionException("la cause $cause n'a pas de message")
        helper.assertValueEqual(messages.filter { it.player === pending.stage.player }, listOf(Delivery(pending.stage.player, situation.with())), "le message de l'annulation")
        helper.assertValueEqual(situation.type, MessageType.REFUSAL, "le type du message de l'annulation")
        helper.assertValueEqual(pending.outcome.cancellations, listOf(cause), "la cause que la famille apprend")
        helper.assertFalse(pending.runtime.teleports.hasPending(pending.stage.player), "la téléportation n'est plus en attente")
        helper.assertTrue(pending.outcome.arrival == null, "le joueur n'est pas arrivé")
    }

    // ── Ce que le tick constate, sur un état propre au test ──

    /**
     * Le joueur qui s'éloigne de plus d'un bloc de son point de départ annule sa téléportation : le délai passé, il n'est pas téléporté, et le
     * cooldown n'a pas démarré.
     */
    @GameTest
    fun movingAwayCancelsThePendingTeleport(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("moving-away-cancels-the-pending-teleport", clock = clock)
        val pending = Pending(helper, lab.runtime)
        val stage = pending.stage
        val away = stage.departure.copy(x = stage.departure.x + 1.5)

        stage.place(stage.player, away)
        val messages = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }

        assertCancelled(helper, pending, CancellationCause.MOVED, messages)
        clock.advance(10)
        lab.runtime.teleports.tick()
        stage.assertPlayerAt(away, "le délai passé")
        helper.assertTrue(lab.runtime.teleports.launch(stage.request(pending.destination)) == null, "une téléportation annulée ne démarre pas le cooldown")
        helper.succeed()
    }

    /** Rester à un bloc ou moins du point de départ et tourner la tête n'annulent rien : le joueur arrive au terme du délai. */
    @GameTest
    fun stayingWithinOneBlockAndLookingAroundCancelNothing(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("staying-within-one-block-and-looking-around-cancel-nothing", clock = clock)
        val pending = Pending(helper, lab.runtime)
        val stage = pending.stage

        stage.place(stage.player, stage.departure.copy(x = stage.departure.x + 0.6, z = stage.departure.z - 0.6, yaw = 170f, pitch = -45f))
        val messages = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }

        helper.assertValueEqual(messages, emptyList(), "aucun message tant que le joueur reste à portée")
        helper.assertTrue(lab.runtime.teleports.hasPending(stage.player), "la téléportation reste en attente")
        clock.advance(3)
        lab.runtime.teleports.tick()
        stage.assertPlayerAt(pending.destination, "le délai écoulé")
        helper.succeed()
    }

    /** Le joueur qui change de dimension annule sa téléportation. */
    @GameTest
    fun changingDimensionCancelsThePendingTeleport(helper: GameTestHelper) {
        val lab = Lab.open("changing-dimension-cancels-the-pending-teleport", clock = ManualClock())
        val pending = Pending(helper, lab.runtime)
        val stage = pending.stage

        stage.place(stage.player, stage.netherPlatform())
        val messages = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }

        assertCancelled(helper, pending, CancellationCause.CHANGED_DIMENSION, messages)
        helper.succeed()
    }

    /** Le joueur qui se déconnecte annule sa téléportation : la famille l'apprend, et lui ne reçoit aucun message. */
    @GameTest
    fun disconnectingCancelsThePendingTeleport(helper: GameTestHelper) {
        val lab = Lab.open("disconnecting-cancels-the-pending-teleport", clock = ManualClock())
        val pending = Pending(helper, lab.runtime)
        val player = pending.stage.player

        player.connection.onDisconnect(DisconnectionDetails(Component.literal("gametest")))
        val messages = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }

        helper.assertTrue(player.hasDisconnected(), "le joueur est déconnecté")
        helper.assertValueEqual(messages, emptyList(), "aucun message pour un joueur parti")
        helper.assertValueEqual(pending.outcome.cancellations, listOf(CancellationCause.DISCONNECTED), "la cause que la famille apprend")
        helper.assertFalse(lab.runtime.teleports.hasPending(player), "la téléportation n'est plus en attente")
        helper.succeed()
    }

    // ── Ce que le jeu annonce au mod, sur l'état du serveur ──

    /** Le joueur qui subit des dégâts annule sa téléportation, dès le coup : c'est l'événement du jeu qui l'apprend au mod. */
    @GameTest
    fun damageCancelsThePendingTeleport(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val pending = Pending(helper, runtime, delay = ONE_HOUR)
        val player = pending.stage.player
        val health = player.health

        val messages = Harness.record(runtime) { player.hurtServer(helper.level, helper.level.damageSources().generic(), 2f) }

        helper.assertTrue(player.health < health && player.isAlive, "le joueur est blessé, et vivant")
        assertCancelled(helper, pending, CancellationCause.DAMAGED, messages)
        helper.succeed()
    }

    /** Le joueur qui meurt annule sa téléportation, et le message dit sa mort, non ses dégâts. */
    @GameTest
    fun deathCancelsThePendingTeleport(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val pending = Pending(helper, runtime, delay = ONE_HOUR)
        val player = pending.stage.player

        val messages = Harness.record(runtime) { player.kill(helper.level) }

        helper.assertTrue(player.isDeadOrDying, "le joueur est mort")
        assertCancelled(helper, pending, CancellationCause.DIED, messages)
        helper.succeed()
    }

    /** Le serveur fait tiquer les téléportations en attente : le joueur qui s'éloigne voit la sienne annulée sans que le test ne fasse rien tiquer. */
    @GameTest
    fun serverTickWatchesThePendingTeleports(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val pending = Pending(helper, runtime, delay = ONE_HOUR)
        val stage = pending.stage

        stage.place(stage.player, stage.departure.copy(z = stage.departure.z + 2.0))

        helper.succeedWhen {
            helper.assertValueEqual(pending.outcome.cancellations, listOf(CancellationCause.MOVED), "la cause que la famille apprend, au tick du serveur")
            helper.assertFalse(runtime.teleports.hasPending(stage.player), "la téléportation n'est plus en attente")
        }
    }
}
