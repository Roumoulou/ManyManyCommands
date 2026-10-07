// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import kotlin.io.path.readText

/**
 * `/homes teleport [<name>]` et les alias (`homes.md` des specs, chapitres 3.2 et 6), joués par un joueur simulé en survie : les refus propres
 * dans leur ordre, le déroulé commun, l'arrivée, le quota conservé dans `homes.json` d'un état du mod au suivant.
 */
class HomesTeleportGameTests {

    private companion object {

        /** Les homes se rejoignent tout de suite, sans cooldown. */
        const val AT_ONCE = """{ "rules": { "default": { "delay": 0, "cooldown": 0 } } }"""

        /** Un seul usage par heure, tout de suite et sans cooldown. */
        const val ONE_PER_HOUR = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 3600 } } } }"""
    }

    /** Ce qu'une commande a rendu, et les messages que le joueur a reçus. */
    private class Outcome(val result: Int, val messages: List<Message>)

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome {
        var result = 0
        val messages = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, player.createCommandSourceStack()) }.filter { it.player === player }.map { it.message }
        return Outcome(result, messages)
    }

    /** Crée le home [name] de [stage] à [location], et ramène le joueur à son départ. */
    private fun createHomeAt(lab: Lab, stage: TeleportStage, name: String, location: Location) {
        stage.place(stage.player, location)
        run(lab, stage.player, "homes create $name")
        stage.place(stage.player, stage.departure)
    }

    /** La plage de quota de [player] dans les homes de [lab], qui doit en avoir une. */
    private fun quotaOf(helper: GameTestHelper, lab: Lab, player: ServerPlayer): QuotaUsage =
        lab.runtime.worldData?.homes?.content?.get(player.uuid)?.quota ?: throw helper.assertionException("le joueur n'a pas de plage de quota dans homes.json")

    /** L'entrée d'un joueur dans `homes.json`, avec ses homes, dont un dans une dimension que le serveur ne porte pas. */
    private fun homesData(player: ServerPlayer, homes: Map<String, Location>): String {
        val moon = """"moon": { "dimension": "mymod:moon", "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0 }"""
        val written = homes.entries.joinToString(", ") { (name, it) -> """"$name": { "dimension": "${it.dimension}", "x": ${it.x}, "y": ${it.y}, "z": ${it.z}, "yaw": ${it.yaw}, "pitch": ${it.pitch} }""" }
        return """{ "players": { "${player.stringUUID}": { "name": "${player.scoreboardName}", "homes": { $written, $moon } } } }"""
    }

    /** Une téléportation vers un home suit le déroulé commun : le compte à rebours, l'arrivée au point exact, regard compris, le message d'arrivée de la famille, puis le cooldown. */
    @GameTest
    fun teleportToAHomeRunsThroughTheCommonFlow(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("teleport-to-a-home-runs-through-the-common-flow", clock = clock)
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5).copy(yaw = 90f, pitch = 10f)
        createHomeAt(lab, stage, "far", far)

        val launched = run(lab, stage.player, "homes teleport far")
        clock.advance(3)
        val arrival = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }.filter { it.player === stage.player }.map { it.message }
        val onCooldown = run(lab, stage.player, "homes teleport far")

        helper.assertValueEqual(launched.result, 1, "le résultat de /homes teleport lancée")
        helper.assertValueEqual(launched.messages, listOf(CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3))), "le compte à rebours part au lancement")
        helper.assertValueEqual(arrival, listOf(HomesMessages.ARRIVED.with("far")), "l'arrivée se dit par la famille")
        helper.assertValueEqual(arrival.single().type, MessageType.SUCCESS, "l'arrivée est un succès")
        stage.assertPlayerAt(far, "au home, le délai écoulé")
        helper.assertValueEqual(stage.player.yRot to stage.player.xRot, 90f to 10f, "l'orientation du home à l'arrivée")
        helper.assertValueEqual(onCooldown.messages, listOf(CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(10))), "le cooldown des homes court")
        helper.assertValueEqual(onCooldown.result, 0, "le résultat d'un refus du déroulé")
        helper.succeed()
    }

    /** Sans nom, `teleport` rejoint le home par défaut, et refuse quand le joueur n'en a pas en disant comment en désigner un. */
    @GameTest
    fun teleportWithoutNameGoesToTheDefaultHome(helper: GameTestHelper) {
        val lab = Lab.open("teleport-without-name-goes-to-the-default-home", rules = mapOf(HomesRules to AT_ONCE))
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val near = stage.platform(5, 1)
        createHomeAt(lab, stage, "far", far)

        val single = run(lab, stage.player, "homes teleport")
        helper.assertValueEqual(single.messages, listOf(HomesMessages.ARRIVED.with("far")), "le seul home est le home par défaut")
        stage.assertPlayerAt(far, "au seul home")

        createHomeAt(lab, stage, "near", near)
        val none = run(lab, stage.player, "homes teleport")
        helper.assertValueEqual(none.messages, listOf(HomesMessages.NO_DEFAULT.with()), "à deux homes sans désignation, aucun home par défaut")
        helper.assertValueEqual(none.result, 0, "le résultat du refus")

        run(lab, stage.player, "homes default near")
        helper.assertValueEqual(run(lab, stage.player, "homes teleport").messages, listOf(HomesMessages.ARRIVED.with("near")), "le home désigné")
        stage.assertPlayerAt(near, "au home désigné")
        helper.succeed()
    }

    /**
     * Les refus propres de `teleport`, dans l'ordre de la fiche : le home absent, la dimension de départ interdite, la dimension du home
     * introuvable, le quota atteint. Un home dont la dimension n'existe plus reste dans la liste et se renomme comme un autre.
     */
    @GameTest
    fun teleportRefusesInTheOrderOfItsSheet(helper: GameTestHelper) {
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val noDeparture = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "forbidden-departure-dimensions": ["minecraft:overworld"], "quota": { "uses": 1, "period": 60 } } } }"""
        val lab = Lab.open("teleport-refuses-in-the-order-of-its-sheet", rules = mapOf(HomesRules to noDeparture), data = mapOf("homes" to homesData(stage.player, mapOf("far" to far))), clock = ManualClock())

        helper.assertValueEqual(run(lab, stage.player, "homes teleport absent").messages, listOf(HomesMessages.ABSENT.with("absent")), "le home absent, premier refus")
        helper.assertValueEqual(run(lab, stage.player, "homes teleport").messages, listOf(HomesMessages.NO_DEFAULT.with()), "l'absence de home par défaut, premier refus aussi")
        helper.assertValueEqual(run(lab, stage.player, "homes teleport moon").messages, listOf(HomesMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld")), "la dimension de départ interdite, deuxième refus")

        lab.writeRules(HomesRules, """{ "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 60 } } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(run(lab, stage.player, "homes teleport far").messages, listOf(HomesMessages.ARRIVED.with("far")), "le quota s'use d'abord")
        helper.assertValueEqual(run(lab, stage.player, "homes teleport moon").messages, listOf(HomesMessages.DIMENSION_MISSING.with("mymod:moon", "moon")), "la dimension introuvable, troisième refus, avant le quota")
        val quota = run(lab, stage.player, "homes teleport far")
        helper.assertValueEqual(quota.messages, listOf(HomesMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(60))), "le quota atteint, quatrième refus, avec l'attente")
        helper.assertValueEqual(quota.result, 0, "le résultat du refus")

        val listed = run(lab, stage.player, "homes list").messages.drop(1).map { it.text.arguments.single() }
        helper.assertValueEqual(listed, listOf<Any>("far", "moon"), "le home dont la dimension n'existe plus reste dans la liste")
        helper.assertValueEqual(run(lab, stage.player, "homes rename moon lune").messages, listOf(HomesMessages.RENAMED.with("moon", "lune")), "et se renomme comme un autre")
        helper.succeed()
    }

    /** Les refus du déroulé commun, répondus par la commande : la téléportation déjà en attente, et l'arrivée dangereuse d'un home au-dessus du vide. */
    @GameTest
    fun teleportAnswersTheRefusalsOfTheCommonFlow(helper: GameTestHelper) {
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val overTheVoid = stage.standing(5, 7, 1)
        val lab = Lab.open("teleport-answers-the-refusals-of-the-common-flow", clock = ManualClock(), data = mapOf("homes" to homesData(stage.player, mapOf("far" to far, "void" to overTheVoid))))

        val dangerous = run(lab, stage.player, "homes teleport void")
        helper.assertValueEqual(dangerous.messages, listOf(CommonMessages.TELEPORT_ARRIVAL_VOID.with()), "l'arrivée au-dessus du vide est refusée par le déroulé")
        helper.assertValueEqual(dangerous.result, 0, "le résultat du refus")

        helper.assertValueEqual(run(lab, stage.player, "homes teleport far").result, 1, "la téléportation vers far est lancée")
        val pending = run(lab, stage.player, "homes teleport far")
        helper.assertValueEqual(pending.messages, listOf(CommonMessages.TELEPORT_PENDING.with()), "une téléportation est déjà en attente")
        helper.assertTrue(lab.runtime.teleports.hasPending(stage.player), "la première téléportation reste en attente")
        helper.succeed()
    }

    /** `/home`, `/sethome` et `/delhome` répondent comme les sous-commandes qu'ils remplacent, et un seul réglage les désactive ensemble. */
    @GameTest
    fun aliasesAnswerLikeTheirCommands(helper: GameTestHelper) {
        val lab = Lab.open("aliases-answer-like-their-commands", rules = mapOf(HomesRules to AT_ONCE))
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val position = far.toPosition()

        stage.place(stage.player, far)
        val created = run(lab, stage.player, "sethome far")
        stage.place(stage.player, stage.departure)
        val arrived = run(lab, stage.player, "home")
        stage.place(stage.player, stage.departure)
        val arrivedByName = run(lab, stage.player, "home far")
        val deleted = run(lab, stage.player, "delhome far")
        val noDefault = run(lab, stage.player, "home")

        helper.assertValueEqual(created.messages, listOf(HomesMessages.CREATED.with("far", position.dimension, position.blockX, position.blockY, position.blockZ)), "/sethome crée")
        helper.assertValueEqual(arrived.messages, listOf(HomesMessages.ARRIVED.with("far")), "/home rejoint le home par défaut")
        helper.assertValueEqual(arrivedByName.messages, listOf(HomesMessages.ARRIVED.with("far")), "/home <name> rejoint le home nommé")
        stage.assertPlayerAt(far, "au home, par l'alias")
        helper.assertValueEqual(deleted.messages, listOf(HomesMessages.DELETED.with("far", position.dimension, position.blockX, position.blockY, position.blockZ)), "/delhome supprime")
        helper.assertValueEqual(noDefault.messages, listOf(HomesMessages.NO_DEFAULT.with()), "/home sans home refuse comme la commande")

        val withoutAliases = Lab.open("aliases-answer-like-their-commands-without-aliases", rules = mapOf(HomesRules to """{ "aliases": false }"""))
        listOf("home", "sethome", "delhome").forEach { helper.assertTrue(withoutAliases.dispatcher.root.getChild(it) == null, "/$it n'est pas enregistré quand les alias sont inactifs") }
        helper.assertTrue(withoutAliases.dispatcher.root.getChild("homes") != null, "/homes reste enregistrée sans ses alias")
        helper.assertTrue(Harness.serverDispatcher(helper).root.getChild("home") != null, "/home est enregistré sur le serveur, où les alias sont actifs")
        helper.succeed()
    }

    /** Le quota se conserve dans `homes.json` : la plage ouverte par une téléportation survit à un redémarrage, et se rouvre quand elle est finie. */
    @GameTest
    fun quotaWindowSurvivesARestart(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("quota-window-survives-a-restart", rules = mapOf(HomesRules to ONE_PER_HOUR), clock = clock)
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        createHomeAt(lab, stage, "far", far)
        val windowStart = clock.instant().epochSecond

        helper.assertValueEqual(run(lab, stage.player, "homes teleport far").messages, listOf(HomesMessages.ARRIVED.with("far")), "le premier usage")
        val file = lab.directory.resolve("world").resolve("homes.json")
        helper.assertTrue("\"window-start\": $windowStart" in file.readText(), "la plage est écrite dans homes.json tout de suite")

        val restarted = Lab.reopen("quota-window-survives-a-restart", clock = clock)
        stage.place(stage.player, stage.departure)
        helper.assertValueEqual(quotaOf(helper, restarted, stage.player), QuotaUsage(QuotaClock.REAL, windowStart, uses = 1), "la plage relue par l'état suivant")
        val reached = run(restarted, stage.player, "homes teleport far")
        helper.assertValueEqual(reached.messages, listOf(HomesMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(3_600))), "le quota atteint survit au redémarrage")
        stage.assertPlayerAt(stage.departure, "le joueur n'a pas bougé")

        clock.advance(3_600)
        helper.assertValueEqual(run(restarted, stage.player, "homes teleport far").messages, listOf(HomesMessages.ARRIVED.with("far")), "la plage finie en rouvre une")
        helper.assertValueEqual(quotaOf(helper, restarted, stage.player), QuotaUsage(QuotaClock.REAL, windowStart + 3_600, uses = 1), "la nouvelle plage, écrite à son tour")
        helper.succeed()
    }
}
