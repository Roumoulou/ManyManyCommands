// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import com.mojang.brigadier.CommandDispatcher
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import fr.roumoulou.manymanycommands.rules.tpa.TpaJourney
import fr.roumoulou.manymanycommands.rules.tpa.TpaMessages
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.io.path.readText

/**
 * Le voyage d'une demande acceptée (`tpa.md` des specs, chapitres 3.2 et 5), joué par des joueurs simulés nommés : le déroulé commun pour le
 * voyageur, l'arrivée dite aux deux joueurs, la position de retour, chaque cause qu'un refus ou une annulation donne à l'autre joueur, et le
 * quota du demandeur, conservé dans `tpa.json`.
 */
class TpaTeleportGameTests {

    private companion object {

        /** Deux demandes de suite au même joueur passent ; le délai et le cooldown sont ceux de la fiche. */
        const val NO_INTERVAL = """{ "request-interval": 0 }"""

        /** Un seul usage par heure, tout de suite et sans cooldown. */
        const val ONE_PER_HOUR = """{ "request-interval": 0, "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 3600 } } } }"""

        /** Le voyageur ne part pas de l'Overworld, tout de suite et sans cooldown. */
        const val NO_OVERWORLD_DEPARTURE = """{ "request-interval": 0, "rules": { "default": { "delay": 0, "cooldown": 0, "forbidden-departure-dimensions": ["minecraft:overworld"] } } }"""
    }

    /** Ce qu'une commande a rendu, et les messages que chaque joueur a reçus. */
    private class Outcome(val result: Int, val deliveries: List<Delivery>) {

        fun to(player: ServerPlayer): List<Message> = deliveries.filter { it.player === player }.map { it.message }
    }

    private fun run(runtime: ModRuntime, dispatcher: CommandDispatcher<CommandSourceStack>, player: ServerPlayer, command: String): Outcome {
        var result = 0
        val deliveries = Harness.record(runtime) { result = dispatcher.execute(command, player.createCommandSourceStack()) }
        return Outcome(result, deliveries)
    }

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome = run(lab.runtime, lab.dispatcher, player, command)

    /** [requester] demande [recipient] par [command], qui accepte : ce que l'acceptation rend et envoie. */
    private fun requestAndAccept(helper: GameTestHelper, lab: Lab, requester: ServerPlayer, recipient: ServerPlayer, command: String = "tpa"): Outcome {
        helper.assertValueEqual(run(lab, requester, "$command ${recipient.scoreboardName}").result, 1, "la demande de ${requester.scoreboardName} part")
        return run(lab, recipient, "tpaaccept")
    }

    private fun notDone(traveler: ServerPlayer, cause: TranslatableText): Message = TpaJourney.notDone(traveler.scoreboardName, cause)

    private fun at(location: Location): Vec3 = Vec3(location.x, location.y, location.z)

    // ── Le voyage (chapitre 3.2) ──

    /**
     * Une demande par `/tpa` acceptée mène le demandeur chez le destinataire, par le déroulé commun : le compte à rebours, l'arrivée à la
     * position de l'hôte au moment de l'acceptation, regard compris, l'arrivée dite aux deux joueurs, et l'endroit quitté en position de retour
     * du voyageur, par la source `tpa`.
     */
    @GameTest
    fun tpaAcceptedTravelsTheRequesterToTheHost(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("tpa-accepted-travels-the-requester-to-the-host", rules = mapOf(TpaRules to NO_INTERVAL), clock = clock)
        val stage = TeleportStage(helper)
        val steve = stage.namedPlayer("SteveM")
        val far = stage.platform(5, 5).copy(yaw = 90f, pitch = 10f)
        val alex = stage.namedPlayer("AlexM", at = far)

        val accepted = requestAndAccept(helper, lab, steve, alex)
        helper.assertValueEqual(accepted.result, 1, "le résultat de /tpaaccept, le voyage lancé")
        helper.assertValueEqual(accepted.to(steve), listOf(TpaMessages.ACCEPTED_REQUESTER.with("AlexM"), CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3))), "le demandeur apprend l'acceptation, puis le compte à rebours : il voyage")
        helper.assertValueEqual(accepted.to(alex), listOf(TpaMessages.ACCEPTED_RECIPIENT.with("SteveM")), "le destinataire confirme, et ne voyage pas")
        helper.assertTrue(lab.runtime.requests.outgoing(steve.uuid) == null, "la demande est consommée")

        stage.place(alex, stage.platform(1, 5))
        clock.advance(3)
        val arrival = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }

        helper.assertValueEqual(arrival.filter { it.player === steve }.map { it.message }, listOf(TpaMessages.ARRIVED_TRAVELER.with("AlexM")), "l'arrivée dite au voyageur")
        helper.assertValueEqual(arrival.filter { it.player === alex }.map { it.message }, listOf(TpaMessages.ARRIVED_HOST.with("SteveM")), "l'arrivée dite à l'hôte")
        helper.assertValueEqual(arrival.map { it.message.type }, listOf(MessageType.SUCCESS, MessageType.DISPLAY), "un succès chez le voyageur, un affichage chez l'hôte")
        helper.assertValueEqual(steve.position(), at(far), "le voyageur est à la position de l'hôte au moment de l'acceptation, pas là où l'hôte est allé depuis")
        helper.assertValueEqual(steve.yRot to steve.xRot, 90f to 10f, "le regard de l'hôte à l'acceptation")
        helper.assertValueEqual(lab.runtime.returns[steve.uuid] ?: throw helper.assertionException("l'arrivée n'a pas enregistré la position de retour"), stage.departure.toPosition(), "l'endroit quitté devient la position de retour du voyageur, par la source tpa")
        helper.assertTrue(lab.runtime.returns[alex.uuid] == null, "l'hôte n'a pas voyagé : pas de position de retour")
        helper.succeed()
    }

    /**
     * Une demande par `/tpahere` acceptée mène le destinataire chez le demandeur, tout de suite quand le délai vaut 0, et le quota se compte au
     * demandeur, qui n'a pas voyagé.
     */
    @GameTest
    fun tpahereAcceptedTravelsTheRecipientToTheRequester(helper: GameTestHelper) {
        val lab = Lab.open("tpahere-accepted-travels-the-recipient-to-the-requester", rules = mapOf(TpaRules to ONE_PER_HOUR), clock = ManualClock())
        val stage = TeleportStage(helper)
        val steve = stage.namedPlayer("SteveN")
        val far = stage.platform(5, 5)
        val alex = stage.namedPlayer("AlexN", at = far)

        val accepted = requestAndAccept(helper, lab, steve, alex, command = "tpahere")

        helper.assertValueEqual(accepted.result, 1, "le résultat de /tpaaccept, le voyage fait")
        helper.assertValueEqual(accepted.to(alex), listOf(TpaMessages.ACCEPTED_RECIPIENT.with("SteveN"), TpaMessages.ARRIVED_TRAVELER.with("SteveN")), "le destinataire confirme, puis arrive : c'est lui qui voyage")
        helper.assertValueEqual(accepted.to(steve), listOf(TpaMessages.ACCEPTED_REQUESTER.with("AlexN"), TpaMessages.ARRIVED_HOST.with("AlexN")), "le demandeur apprend l'acceptation, puis l'arrivée chez lui")
        helper.assertValueEqual(alex.position(), at(stage.departure), "le destinataire est chez le demandeur")
        helper.assertValueEqual(lab.runtime.returns[alex.uuid] ?: throw helper.assertionException("l'arrivée n'a pas enregistré la position de retour du voyageur"), far.toPosition(), "la position de retour du voyageur")
        val tpa = lab.runtime.worldData?.tpa?.content ?: throw helper.assertionException("tpa.json est indisponible")
        helper.assertTrue(tpa[steve.uuid]?.quota != null, "l'usage se compte au demandeur")
        helper.assertTrue(tpa[alex.uuid]?.quota == null, "et non au voyageur")
        helper.succeed()
    }

    // ── Ce que l'autre joueur apprend (chapitre 5) ──

    /**
     * Le déroulé refuse le voyage, la demande est consommée quand même, et l'hôte apprend la cause : la dimension de départ interdite au
     * voyageur, une téléportation du voyageur déjà en attente, son cooldown, l'arrivée obstruée, dans la lave, au-dessus du vide.
     */
    @GameTest
    fun refusedVoyageIsConsumedAndTellsTheHostTheCause(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("refused-voyage-is-consumed-and-tells-the-host-the-cause", rules = mapOf(TpaRules to NO_OVERWORLD_DEPARTURE), clock = clock)
        val stage = TeleportStage(helper)
        val steve = stage.namedPlayer("SteveO")
        val far = stage.platform(5, 5)
        val alex = stage.namedPlayer("AlexO", at = far)
        val console = Harness.console(helper)

        val forbidden = requestAndAccept(helper, lab, steve, alex)
        helper.assertValueEqual(forbidden.result, 0, "le résultat de /tpaaccept, le voyage refusé")
        helper.assertValueEqual(forbidden.to(steve), listOf(TpaMessages.ACCEPTED_REQUESTER.with("AlexO"), TpaMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld")), "le voyageur apprend l'acceptation, puis le refus de sa dimension de départ")
        helper.assertValueEqual(forbidden.to(alex), listOf(TpaMessages.ACCEPTED_RECIPIENT.with("SteveO"), notDone(steve, TpaMessages.CAUSE_DEPARTURE_FORBIDDEN.with())), "l'hôte apprend la cause")
        helper.assertValueEqual(forbidden.to(alex).last().type, MessageType.DISPLAY, "un affichage chez l'hôte")
        helper.assertTrue(lab.runtime.requests.outgoing(steve.uuid) == null, "la demande est consommée malgré le refus")
        helper.assertValueEqual(steve.position(), at(stage.departure), "le voyageur n'a pas bougé")

        lab.writeRules(TpaRules, NO_INTERVAL)
        lab.dispatcher.execute("manymanycommands reload", console)
        helper.assertTrue(lab.runtime.teleports.launch(TeleportRequest(steve, steve.createCommandSourceStack(), HomesRules, TeleportStage.rules(), Arrival.At(far))) == null, "une téléportation du voyageur attend")
        helper.assertValueEqual(requestAndAccept(helper, lab, steve, alex).to(alex).last(), notDone(steve, TpaMessages.CAUSE_PENDING.with()), "une téléportation du voyageur est déjà en attente")
        clock.advance(3)
        lab.runtime.teleports.tick()
        stage.place(steve, stage.departure)

        helper.assertValueEqual(requestAndAccept(helper, lab, steve, alex).to(steve).last(), CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3)), "le voyage part")
        clock.advance(3)
        lab.runtime.teleports.tick()
        helper.assertValueEqual(steve.position(), at(far), "le voyageur est arrivé, son cooldown court")
        stage.place(steve, stage.departure)
        val onCooldown = requestAndAccept(helper, lab, steve, alex)
        helper.assertValueEqual(onCooldown.to(steve).last(), CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(10)), "le cooldown du voyageur, lu du déroulé")
        helper.assertValueEqual(onCooldown.to(alex).last(), notDone(steve, TpaMessages.CAUSE_COOLDOWN.with()), "son cooldown n'est pas écoulé")
        clock.advance(10)

        stage.place(alex, stage.standing(5, 7, 1))
        helper.assertValueEqual(requestAndAccept(helper, lab, steve, alex).to(alex).last(), notDone(steve, TpaMessages.CAUSE_VOID.with()), "l'hôte au-dessus du vide")

        val inTheStone = stage.platform(5, 1)
        stage.place(alex, inTheStone)
        stage.set(5, 2, 1, Blocks.STONE)
        stage.set(5, 3, 1, Blocks.STONE)
        helper.assertValueEqual(requestAndAccept(helper, lab, steve, alex).to(alex).last(), notDone(steve, TpaMessages.CAUSE_OBSTRUCTED.with()), "l'hôte dans la pierre")
        stage.set(5, 2, 1, Blocks.AIR)
        stage.set(5, 3, 1, Blocks.AIR)

        listOf(4 to 5, 6 to 5, 5 to 4, 5 to 6).forEach { (x, z) -> stage.set(x, 2, z, Blocks.STONE) }
        stage.place(alex, far)
        stage.set(5, 2, 5, Blocks.LAVA)
        helper.assertValueEqual(requestAndAccept(helper, lab, steve, alex).to(alex).last(), notDone(steve, TpaMessages.CAUSE_LAVA_OR_FIRE.with()), "l'hôte dans la lave")
        stage.set(5, 2, 5, Blocks.AIR)
        helper.assertValueEqual(steve.position(), at(stage.departure), "le voyageur n'a jamais bougé")
        helper.succeed()
    }

    /** La téléportation s'annule après l'accord, et l'hôte apprend la cause : le voyageur a bougé, a changé de dimension, s'est déconnecté. */
    @GameTest
    fun cancelledVoyageTellsTheHostTheCause(helper: GameTestHelper) {
        val lab = Lab.open("cancelled-voyage-tells-the-host-the-cause", rules = mapOf(TpaRules to NO_INTERVAL), clock = ManualClock())
        val stage = TeleportStage(helper)
        val steve = stage.namedPlayer("SteveP")
        val far = stage.platform(5, 5)
        val alex = stage.namedPlayer("AlexP", at = far)

        requestAndAccept(helper, lab, steve, alex)
        stage.place(steve, stage.departure.copy(x = stage.departure.x + 2.0))
        val moved = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }
        helper.assertValueEqual(moved.filter { it.player === steve }.map { it.message }, listOf(CommonMessages.TELEPORT_CANCELLED_MOVED.with()), "le voyageur lit l'annulation du déroulé")
        helper.assertValueEqual(moved.filter { it.player === alex }.map { it.message }, listOf(notDone(steve, TpaMessages.CAUSE_MOVED.with())), "il a bougé")
        helper.assertTrue(lab.runtime.returns[steve.uuid] == null, "une téléportation annulée n'enregistre pas de position de retour")
        stage.place(steve, stage.departure)

        requestAndAccept(helper, lab, steve, alex)
        stage.place(steve, stage.netherPlatform())
        val changed = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }
        helper.assertValueEqual(changed.filter { it.player === alex }.map { it.message }, listOf(notDone(steve, TpaMessages.CAUSE_CHANGED_DIMENSION.with())), "il a changé de dimension")
        stage.place(steve, stage.departure)

        requestAndAccept(helper, lab, steve, alex)
        steve.connection.onDisconnect(DisconnectionDetails(Component.literal("gametest")))
        val gone = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }
        helper.assertValueEqual(gone.filter { it.player === alex }.map { it.message }, listOf(notDone(steve, TpaMessages.CAUSE_DISCONNECTED.with())), "il s'est déconnecté")
        helper.assertValueEqual(gone.filter { it.player === steve }, emptyList(), "un joueur parti ne lit plus rien")
        helper.succeed()
    }

    /**
     * Les dégâts et la mort annulent le voyage par l'événement du jeu que le point d'entrée branche, sur l'état du serveur, et l'hôte apprend la
     * cause. Le délai y est celui de la fiche, trois secondes, que le test ne laisse pas s'écouler.
     */
    @GameTest
    fun damageAndDeathCancelTheVoyageAndTellTheHost(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val stage = TeleportStage(helper)
        val steve = stage.namedPlayer("SteveQ")
        val alex = stage.namedPlayer("AlexQ", at = stage.platform(5, 5))
        val notch = stage.namedPlayer("NotchQ", at = stage.platform(1, 5))

        helper.assertValueEqual(run(runtime, dispatcher, steve, "tpa AlexQ").result, 1, "la demande part")
        helper.assertValueEqual(run(runtime, dispatcher, alex, "tpaaccept").result, 1, "le voyage est lancé")
        val hurt = Harness.record(runtime) { steve.hurtServer(helper.level, helper.level.damageSources().generic(), 2f) }
        helper.assertValueEqual(hurt.filter { it.player === steve }.map { it.message }, listOf(CommonMessages.TELEPORT_CANCELLED_DAMAGED.with()), "le voyageur lit l'annulation")
        helper.assertValueEqual(hurt.filter { it.player === alex }.map { it.message }, listOf(notDone(steve, TpaMessages.CAUSE_DAMAGED.with())), "il a subi des dégâts")

        helper.assertValueEqual(run(runtime, dispatcher, steve, "tpa NotchQ").result, 1, "la seconde demande part, vers un autre joueur")
        helper.assertValueEqual(run(runtime, dispatcher, notch, "tpaaccept").result, 1, "le second voyage est lancé")
        val killed = Harness.record(runtime) { steve.kill(helper.level) }
        helper.assertValueEqual(killed.filter { it.player === notch }.map { it.message }, listOf(notDone(steve, TpaMessages.CAUSE_DIED.with())), "il est mort")
        helper.succeed()
    }

    // ── Le quota (chapitre 4) ──

    /** Le quota se contrôle à l'envoi, se compte au demandeur à l'arrivée, et sa plage survit au redémarrage dans `tpa.json`. */
    @GameTest
    fun quotaIsChargedToTheRequesterAtTheArrivalAndSurvivesARestart(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("tpa-quota-is-charged-to-the-requester-and-survives-a-restart", rules = mapOf(TpaRules to ONE_PER_HOUR), clock = clock)
        val stage = TeleportStage(helper)
        val steve = stage.namedPlayer("SteveR")
        val far = stage.platform(5, 5)
        val alex = stage.namedPlayer("AlexR", at = far)
        val windowStart = clock.instant().epochSecond

        helper.assertValueEqual(requestAndAccept(helper, lab, steve, alex).to(steve).last(), TpaMessages.ARRIVED_TRAVELER.with("AlexR"), "le premier usage")
        val file = lab.directory.resolve("world").resolve("tpa.json")
        helper.assertTrue("\"window-start\": $windowStart" in file.readText() && steve.stringUUID in file.readText(), "la plage du demandeur est écrite dans tpa.json tout de suite")

        val restarted = Lab.reopen("tpa-quota-is-charged-to-the-requester-and-survives-a-restart", clock = clock)
        stage.place(steve, stage.departure)
        helper.assertValueEqual(run(restarted, steve, "tpa AlexR").to(steve), listOf(TpaMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(3_600))), "le quota atteint survit au redémarrage, contrôlé à l'envoi")
        helper.assertValueEqual(run(restarted, alex, "tpa SteveR").result, 1, "l'autre joueur, sans usage, demande")
        clock.advance(3_600)
        helper.assertValueEqual(run(restarted, steve, "tpa AlexR").result, 1, "la plage finie en rouvre une")
        helper.succeed()
    }
}
