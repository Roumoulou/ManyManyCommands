// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.back.BackMessages
import fr.roumoulou.manymanycommands.rules.back.BackNodes
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.back.BackSource
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * `/back` et la position de retour (`back.md` des specs), joués par un joueur simulé en survie : ce qui enregistre la position selon les sources
 * du jeu de règles, les refus propres dans leur ordre, le déroulé commun, l'aller-retour, le quota conservé dans `back.json`.
 */
class BackCommandGameTests {

    private companion object {

        /** Les homes se rejoignent tout de suite, sans cooldown. */
        const val HOMES_AT_ONCE = """{ "rules": { "default": { "delay": 0, "cooldown": 0 } } }"""

        /** Le retour se fait tout de suite, sans cooldown. */
        const val BACK_AT_ONCE = """{ "rules": { "default": { "delay": 0, "cooldown": 0 } } }"""
    }

    /** Ce qu'une commande a rendu, et les messages que le joueur a reçus. */
    private class Outcome(val result: Int, val messages: List<Message>)

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome {
        var result = 0
        val messages = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, player.createCommandSourceStack()) }.filter { it.player === player }.map { it.message }
        return Outcome(result, messages)
    }

    /** Un état où les homes et le retour se font tout de suite, avec les règles du retour [back] quand le test en donne d'autres. */
    private fun open(name: String, back: String = BACK_AT_ONCE, clock: ManualClock = ManualClock()): Lab = Lab.open(name, rules = mapOf(HomesRules to HOMES_AT_ONCE, BackRules to back), clock = clock)

    /** Crée le home `far` de [stage] à [far], ramène le joueur à son départ, et l'y téléporte : son départ devient sa position de retour. */
    private fun goHome(helper: GameTestHelper, lab: Lab, stage: TeleportStage, far: Location) {
        stage.place(stage.player, far)
        run(lab, stage.player, "homes create far")
        stage.place(stage.player, stage.departure)
        helper.assertValueEqual(run(lab, stage.player, "homes teleport far").messages, listOf(HomesMessages.ARRIVED.with("far")), "le joueur rejoint son home")
    }

    /** Une téléportation vers un home enregistre l'endroit quitté ; `/back` y ramène par le déroulé commun, et enregistre à son tour : deux `/back` font un aller-retour. */
    @GameTest
    fun backReturnsToThePlaceLeftAndMakesARoundTrip(helper: GameTestHelper) {
        val lab = open("back-returns-to-the-place-left-and-makes-a-round-trip")
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        goHome(helper, lab, stage, far)

        val back = run(lab, stage.player, "back")
        helper.assertValueEqual(back.result, 1, "le résultat de /back")
        helper.assertValueEqual(back.messages, listOf(BackMessages.ARRIVED.with()), "l'arrivée se dit par la famille")
        helper.assertValueEqual(back.messages.single().type, MessageType.SUCCESS, "l'arrivée est un succès")
        stage.assertPlayerAt(stage.departure, "de retour à l'endroit quitté")

        val again = run(lab, stage.player, "back")
        helper.assertValueEqual(again.messages, listOf(BackMessages.ARRIVED.with()), "le second /back repart")
        stage.assertPlayerAt(far, "l'aller-retour ramène au home")
        helper.succeed()
    }

    /** Le déroulé commun s'applique à `/back` : le délai et son compte à rebours, puis le cooldown de la famille. */
    @GameTest
    fun backRunsThroughTheCommonFlow(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = open("back-runs-through-the-common-flow", back = """{ "rules": { "default": { "delay": 3, "cooldown": 10 } } }""", clock = clock)
        val stage = TeleportStage(helper)
        goHome(helper, lab, stage, stage.platform(5, 5))

        val launched = run(lab, stage.player, "back")
        clock.advance(3)
        val arrival = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }.filter { it.player === stage.player }.map { it.message }
        val onCooldown = run(lab, stage.player, "back")

        helper.assertValueEqual(launched.messages, listOf(CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3))), "le compte à rebours part au lancement")
        helper.assertValueEqual(arrival, listOf(BackMessages.ARRIVED.with()), "l'arrivée, le délai écoulé")
        stage.assertPlayerAt(stage.departure, "de retour, le délai écoulé")
        helper.assertValueEqual(onCooldown.messages, listOf(CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(10))), "le cooldown du retour court")
        helper.succeed()
    }

    /**
     * Les refus propres de `/back`, dans l'ordre de la fiche : l'absence de position de retour, la dimension de départ interdite, le quota atteint
     * avec son attente. La dimension introuvable ne se joue pas ici : une position de retour vit en mémoire, dans un monde dont les dimensions ne
     * disparaissent pas.
     */
    @GameTest
    fun backRefusesInTheOrderOfItsSheet(helper: GameTestHelper) {
        val noDeparture = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "forbidden-departure-dimensions": ["minecraft:overworld"], "quota": { "uses": 1, "period": 60 } } } }"""
        val lab = open("back-refuses-in-the-order-of-its-sheet", back = noDeparture)
        val stage = TeleportStage(helper)

        val none = run(lab, stage.player, "back")
        helper.assertValueEqual(none.messages, listOf(BackMessages.NO_POSITION.with()), "l'absence de position, premier refus, avant la dimension de départ")
        helper.assertValueEqual(none.result, 0, "le résultat du refus")

        goHome(helper, lab, stage, stage.platform(5, 5))
        helper.assertValueEqual(run(lab, stage.player, "back").messages, listOf(BackMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld")), "la dimension de départ interdite, deuxième refus")

        lab.writeRules(BackRules, """{ "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 60 } } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(run(lab, stage.player, "back").messages, listOf(BackMessages.ARRIVED.with()), "le quota s'use d'abord")
        val quota = run(lab, stage.player, "back")
        helper.assertValueEqual(quota.messages, listOf(BackMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(60))), "le quota atteint, quatrième refus, avec l'attente")
        helper.assertValueEqual(quota.result, 0, "le résultat du refus")
        helper.succeed()
    }

    /**
     * Les sources du jeu de règles du joueur décident de ce qui enregistre : la source `homes` désactivée, une téléportation vers un home
     * n'enregistre rien ; la mort n'enregistre que si `death` est active, ce qu'elle n'est pas par défaut.
     */
    @GameTest
    fun sourcesDecideWhatRecordsTheReturnPosition(helper: GameTestHelper) {
        val lab = open("sources-decide-what-records-the-return-position", back = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "sources": { "homes": false } } } }""")
        val stage = TeleportStage(helper)
        goHome(helper, lab, stage, stage.platform(5, 5))
        helper.assertValueEqual(run(lab, stage.player, "back").messages, listOf(BackMessages.NO_POSITION.with()), "la source homes désactivée n'enregistre rien")

        val withDeath = open("sources-decide-what-records-the-return-position-death", back = """{ "rules": { "default": { "sources": { "death": true } } } }""")
        val deathPlace = Location.of(stage.player)
        withDeath.runtime.recordReturn(stage.player, BackSource.DEATH, deathPlace)
        helper.assertValueEqual(withDeath.runtime.returns[stage.player.uuid] ?: throw helper.assertionException("la mort n'a pas enregistré la position"), deathPlace.toPosition(), "la source death active enregistre l'endroit de la mort")

        val server = Harness.serverRuntime(helper)
        stage.player.kill(helper.level)
        helper.assertTrue(server.returns[stage.player.uuid] == null, "par défaut, la mort n'enregistre pas la position de retour")
        helper.succeed()
    }

    /** `/back` exige un joueur, et un `back.json` invalide rend le retour indisponible, jusqu'à sa correction et un rechargement ; le fichier fautif n'est pas réécrit. */
    @GameTest
    fun backIsRefusedToTheConsoleAndUnavailableOnAnInvalidDataFile(helper: GameTestHelper) {
        val console = Harness.console(helper)
        var result = 1
        val consoleKeys = Harness.keysSentTo(null, Harness.serverRuntime(helper)) { result = Harness.serverDispatcher(helper).execute("back", console) }
        helper.assertValueEqual(consoleKeys, listOf(CommonMessages.IN_GAME_ONLY.key), "la réponse de /back à la console")
        helper.assertValueEqual(result, 0, "le résultat de /back à la console")

        val lab = Lab.open("back-is-unavailable-on-an-invalid-data-file", data = mapOf("back" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))
        val player = Harness.player(helper)
        val file = lab.directory.resolve("world").resolve("back.json")
        val faulty = file.readBytes()
        val unavailable = run(lab, player, "back")
        helper.assertValueEqual(unavailable.messages, listOf(BackMessages.UNAVAILABLE.with()), "le retour indisponible")
        helper.assertValueEqual(unavailable.result, 0, "le résultat du refus")
        helper.assertTrue(faulty.contentEquals(file.readBytes()), "le fichier fautif n'est pas réécrit")
        val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", console) }
        helper.assertValueEqual(status[2 + Harness.FILES.indexOf("world/back.json")], ModMessages.STATUS_FILE_REFUSED.key, "l'état montre world/back.json refusé")

        lab.writeData("back", """{ "players": {} }""")
        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", console) }.map { it.message }
        helper.assertValueEqual(reload[Harness.FILES.indexOf("world/back.json")], ModMessages.RELOAD_FILE_APPLIED.with("world/back.json"), "le fichier corrigé s'applique au rechargement")
        helper.assertValueEqual(run(lab, player, "back").messages, listOf(BackMessages.NO_POSITION.with()), "le retour remis en service répond par ses propres refus")
        helper.succeed()
    }

    /** Une famille inactive au démarrage n'enregistre pas `/back`, et un joueur sans le nœud ne la voit pas. */
    @GameTest
    fun disabledOrHiddenBackDoesNotExist(helper: GameTestHelper) {
        val disabled = Lab.open("disabled-back-registers-no-command", rules = mapOf(BackRules to """{ "enabled": false }"""))
        val player = Harness.player(helper)
        val hidden = Lab.open("hidden-back-does-not-exist", permissionSource = { source, node -> if (source.player === player && node == BackNodes.BACK) false else null })

        helper.assertTrue(disabled.dispatcher.root.getChild("back") == null, "/back n'est pas enregistrée quand la famille est inactive")
        Harness.assertUnknownCommand(helper, hidden.dispatcher, player.createCommandSourceStack(), "back")
        helper.assertTrue(Harness.serverDispatcher(helper).root.getChild("back").canUse(player.createCommandSourceStack()), "/back existe pour tout joueur sur le serveur")
        helper.succeed()
    }

    /** Le quota du retour se conserve dans `back.json` : la plage survit au redémarrage, alors que la position de retour, en mémoire, ne lui survit pas. */
    @GameTest
    fun backQuotaWindowSurvivesARestart(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = open("back-quota-window-survives-a-restart", back = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 3600 } } } }""", clock = clock)
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        goHome(helper, lab, stage, far)
        helper.assertValueEqual(run(lab, stage.player, "back").messages, listOf(BackMessages.ARRIVED.with()), "le premier retour")
        helper.assertTrue("\"window-start\": ${clock.instant().epochSecond}" in lab.directory.resolve("world").resolve("back.json").readText(), "la plage est écrite dans back.json tout de suite")

        val restarted = Lab.reopen("back-quota-window-survives-a-restart", clock = clock)
        helper.assertValueEqual(run(restarted, stage.player, "back").messages, listOf(BackMessages.NO_POSITION.with()), "la position de retour ne survit pas au redémarrage")
        helper.assertValueEqual(run(restarted, stage.player, "homes teleport far").messages, listOf(HomesMessages.ARRIVED.with("far")), "une nouvelle position de retour")
        helper.assertValueEqual(run(restarted, stage.player, "back").messages, listOf(BackMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(3_600))), "le quota atteint survit au redémarrage")

        clock.advance(3_600)
        helper.assertValueEqual(run(restarted, stage.player, "back").messages, listOf(BackMessages.ARRIVED.with()), "la plage finie en rouvre une")
        helper.succeed()
    }
}
