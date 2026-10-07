// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import fr.roumoulou.manymanycommands.rules.warps.WarpsMessages
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.math.BigDecimal
import kotlin.io.path.readText
import kotlin.math.abs

/**
 * `/warps teleport`, l'envoi et les alias (`warps.md` des specs, chapitres 2.1, 3.6 et 5), joués par un joueur simulé en survie : les refus
 * propres dans leur ordre, le déroulé commun, l'envoi sans déroulé, le quota conservé dans `warps.json` d'un état du mod au suivant.
 */
class WarpsTeleportGameTests {

    private companion object {

        /** Les warps se rejoignent tout de suite, sans cooldown. */
        const val AT_ONCE = """{ "rules": { "default": { "delay": 0, "cooldown": 0 } } }"""

        /** Un seul usage par heure, tout de suite et sans cooldown. */
        const val ONE_PER_HOUR = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 3600 } } } }"""
    }

    /** Ce qu'une commande a rendu, et les messages que sa source a reçus : le joueur qu'elle désigne, ou la console. */
    private class Outcome(val result: Int, val messages: List<Message>)

    private fun run(lab: Lab, source: CommandSourceStack, command: String): Outcome {
        var result = 0
        val messages = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, source) }.filter { it.player === source.player }.map { it.message }
        return Outcome(result, messages)
    }

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome = run(lab, player.createCommandSourceStack(), command)

    /** Le nombre en écriture décimale, jamais scientifique, que les arguments du jeu ne lisent pas ; il se relit à l'identique. */
    private fun Double.plain(): String = BigDecimal.valueOf(this).toPlainString()

    /** Le placement complet de [location], tel que `create` et `move` le lisent. */
    private fun placement(location: Location): String = "${location.x.plain()} ${location.y.plain()} ${location.z.plain()} ${location.yaw} ${location.pitch} ${location.dimension}"

    /** Crée le warp [name] à [location] par un placement complet, depuis la console : personne ne bouge. */
    private fun createWarpAt(helper: GameTestHelper, lab: Lab, name: String, location: Location) {
        val created = run(lab, Harness.console(helper), "warps create $name ${placement(location)}")
        helper.assertValueEqual(created.result, 1, "la création du warp $name")
    }

    /** Un joueur simulé en mode créatif, dispensé du contrôle de l'arrivée, posé à [at] dans la structure de [stage]. */
    private fun creativePlayer(helper: GameTestHelper, stage: TeleportStage, at: Location = stage.departure): ServerPlayer = Harness.player(helper).also {
        it.setGameMode(GameType.CREATIVE)
        stage.place(it, at)
    }

    /**
     * Le sélecteur du jeu qui désigne les joueurs qui se tiennent exactement à [location]. Un UUID nu ne désigne pas un joueur pour `<targets>`,
     * parce qu'il pourrait désigner toute entité, et les joueurs simulés portent tous le même nom.
     */
    private fun playersAt(location: Location): String = "@a[x=${location.x.plain()},y=${location.y.plain()},z=${location.z.plain()},distance=..0.5]"

    /** Le sélecteur du jeu qui désigne les joueurs dans la boîte d'un bloc d'épaisseur qui va de [a] à [b]. */
    private fun playersBetween(a: Location, b: Location): String =
        "@a[x=${minOf(a.x, b.x).plain()},y=${minOf(a.y, b.y).plain()},z=${minOf(a.z, b.z).plain()},dx=${abs(a.x - b.x).plain()},dy=${abs(a.y - b.y).plain()},dz=${abs(a.z - b.z).plain()}]"

    private fun at(location: Location): Vec3 = Vec3(location.x, location.y, location.z)

    /** La plage de quota de [player] dans les warps de [lab], qui doit en avoir une. */
    private fun quotaOf(helper: GameTestHelper, lab: Lab, player: ServerPlayer): QuotaUsage =
        lab.runtime.worldData?.warps?.content?.players?.get(player.uuid)?.quota ?: throw helper.assertionException("le joueur n'a pas de plage de quota dans warps.json")

    /** Un `warps.json` avec [warps], ceux de [reserved] sous leur clé, et un warp dans une dimension que le serveur ne porte pas. */
    private fun warpsData(warps: Map<String, Location>, reserved: Map<String, String> = emptyMap()): String {
        val moon = """"moon": { "dimension": "mymod:moon", "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0 }"""
        val written = warps.entries.joinToString(", ") { (name, it) ->
            val key = reserved[name]?.let { """, "key": "$it"""" }.orEmpty()
            """"$name": { "dimension": "${it.dimension}", "x": ${it.x}, "y": ${it.y}, "z": ${it.z}, "yaw": ${it.yaw}, "pitch": ${it.pitch}$key }"""
        }
        return """{ "warps": { $written, $moon } }"""
    }

    // ── Rejoindre (chapitre 2.1) ──

    /** Une téléportation vers un warp suit le déroulé commun : le compte à rebours, l'arrivée au point exact, regard compris, le message d'arrivée de la famille, le cooldown, et l'endroit quitté devient la position de retour. */
    @GameTest
    fun teleportToAWarpRunsThroughTheCommonFlow(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("warps-teleport-runs-through-the-common-flow", clock = clock)
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5).copy(yaw = 90f, pitch = 10f)
        createWarpAt(helper, lab, "far", far)

        val launched = run(lab, stage.player, "warps teleport far")
        clock.advance(3)
        val arrival = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }.filter { it.player === stage.player }.map { it.message }
        val onCooldown = run(lab, stage.player, "warps teleport far")

        helper.assertValueEqual(launched.result, 1, "le résultat de /warps teleport lancée")
        helper.assertValueEqual(launched.messages, listOf(CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3))), "le compte à rebours part au lancement")
        helper.assertValueEqual(arrival, listOf(WarpsMessages.ARRIVED.with("far")), "l'arrivée se dit par la famille")
        helper.assertValueEqual(arrival.single().type, MessageType.SUCCESS, "l'arrivée est un succès")
        stage.assertPlayerAt(far, "au warp, le délai écoulé")
        helper.assertValueEqual(stage.player.yRot to stage.player.xRot, 90f to 10f, "l'orientation du warp à l'arrivée")
        helper.assertValueEqual(onCooldown.messages, listOf(CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(10))), "le cooldown des warps court")
        helper.assertValueEqual(onCooldown.result, 0, "le résultat d'un refus du déroulé")
        helper.assertValueEqual(lab.runtime.returns[stage.player.uuid] ?: throw helper.assertionException("l'arrivée n'a pas enregistré la position de retour"), stage.departure.toPosition(), "l'endroit quitté devient la position de retour, par la source warps")
        helper.succeed()
    }

    /**
     * Les refus propres de `teleport`, dans l'ordre de la fiche : le warp absent, ou réservé à d'autres, par le même refus, la dimension de départ
     * interdite, la dimension du warp introuvable, le quota atteint. L'administrateur a toutes les clés. Un warp dont la dimension n'existe plus
     * reste dans la liste et se renomme comme un autre.
     */
    @GameTest
    fun teleportRefusesInTheOrderOfItsSheet(helper: GameTestHelper) {
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val noDeparture = """{ "rules": { "default": { "delay": 0, "cooldown": 0, "forbidden-departure-dimensions": ["minecraft:overworld"], "quota": { "uses": 1, "period": 60 } } } }"""
        val data = warpsData(mapOf("far" to far, "staff" to far), reserved = mapOf("staff" to "staff"))
        val lab = Lab.open("warps-teleport-refuses-in-the-order-of-its-sheet", rules = mapOf(WarpsRules to noDeparture), data = mapOf("warps" to data), clock = ManualClock())
        val player = stage.player
        val admin = Harness.asGameMaster(player)

        helper.assertValueEqual(run(lab, player, "warps teleport absent").messages, listOf(WarpsMessages.ABSENT.with("absent")), "le warp absent, premier refus")
        helper.assertValueEqual(run(lab, player, "warps teleport staff").messages, listOf(WarpsMessages.ABSENT.with("staff")), "le warp réservé à d'autres, même refus")
        helper.assertValueEqual(run(lab, admin, "warps teleport staff").messages, listOf(WarpsMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld")), "l'administrateur a la clé : le warp existe pour lui, et le refus suivant répond")
        helper.assertValueEqual(run(lab, player, "warps teleport moon").messages, listOf(WarpsMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld")), "la dimension de départ interdite, deuxième refus")

        lab.writeRules(WarpsRules, """{ "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 60 } } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(run(lab, player, "warps teleport far").messages, listOf(WarpsMessages.ARRIVED.with("far")), "le quota s'use d'abord")
        helper.assertValueEqual(run(lab, player, "warps teleport moon").messages, listOf(WarpsMessages.DIMENSION_MISSING.with("mymod:moon", "moon")), "la dimension introuvable, troisième refus, avant le quota")
        val quota = run(lab, player, "warps teleport far")
        helper.assertValueEqual(quota.messages, listOf(WarpsMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(60))), "le quota atteint, quatrième refus, avec l'attente")
        helper.assertValueEqual(quota.result, 0, "le résultat du refus")

        val listed = run(lab, admin, "warps list").messages.drop(1).map { it.text.arguments.first() }
        helper.assertValueEqual(listed, listOf<Any>("far", "moon", "staff"), "le warp dont la dimension n'existe plus reste dans la liste")
        helper.assertValueEqual(run(lab, admin, "warps rename moon lune").messages, listOf(WarpsMessages.RENAMED.with("moon", "lune")), "et se renomme comme un autre")
        helper.succeed()
    }

    /** Les refus du déroulé commun, répondus par la commande : l'arrivée dangereuse d'un warp au-dessus du vide, et la téléportation déjà en attente. */
    @GameTest
    fun teleportAnswersTheRefusalsOfTheCommonFlow(helper: GameTestHelper) {
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val overTheVoid = stage.standing(5, 7, 1)
        val lab = Lab.open("warps-teleport-answers-the-refusals-of-the-common-flow", clock = ManualClock(), data = mapOf("warps" to warpsData(mapOf("far" to far, "void" to overTheVoid))))

        val dangerous = run(lab, stage.player, "warps teleport void")
        helper.assertValueEqual(dangerous.messages, listOf(CommonMessages.TELEPORT_ARRIVAL_VOID.with()), "l'arrivée au-dessus du vide est refusée par le déroulé")
        helper.assertValueEqual(dangerous.result, 0, "le résultat du refus")

        helper.assertValueEqual(run(lab, stage.player, "warps teleport far").result, 1, "la téléportation vers far est lancée")
        val pending = run(lab, stage.player, "warps teleport far")
        helper.assertValueEqual(pending.messages, listOf(CommonMessages.TELEPORT_PENDING.with()), "une téléportation est déjà en attente")
        helper.assertTrue(lab.runtime.teleports.hasPending(stage.player), "la première téléportation reste en attente")
        helper.succeed()
    }

    // ── Envoyer (chapitre 3.6, cahier des charges 2.5.10) ──

    /**
     * L'envoi est immédiat et ne suit pas le déroulé : ni délai, ni cooldown, ni quota, ni règle de dimension, et la clé d'accès du warp ne compte
     * pas. Chaque joueur envoyé l'apprend, l'endroit qu'il quitte devient sa position de retour par la source `send`, et l'administrateur reçoit
     * le compte, journalisé. Un seul joueur se désigne par son UUID, un warp absent est refusé.
     */
    @GameTest
    fun sendIgnoresTheCommonFlowItsRulesAndTheAccessKey(helper: GameTestHelper) {
        val strict = """{ "rules": { "default": { "delay": 3, "cooldown": 10, "forbidden-departure-dimensions": ["minecraft:overworld"], "quota": { "uses": 1, "period": 60 } } } }"""
        val lab = Lab.open("warps-send-ignores-the-common-flow", rules = mapOf(WarpsRules to strict), clock = ManualClock())
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        createWarpAt(helper, lab, "far", far)
        run(lab, Harness.console(helper), "warps restrict far staff")
        val other = creativePlayer(helper, stage)
        val departure = stage.departure
        val recording = Harness.RecordingSource()

        var result = 0
        val deliveries = Harness.record(lab.runtime) { result = lab.dispatcher.execute("warps teleport far ${playersAt(departure)}", Harness.console(helper).withSource(recording)) }

        helper.assertValueEqual(result, 2, "le résultat de l'envoi, le nombre de joueurs envoyés")
        helper.assertValueEqual(deliveries.filter { it.player == null }.map { it.message }, listOf(WarpsMessages.SENT_MANY.with(2, "far")), "l'administrateur reçoit le compte")
        helper.assertValueEqual(deliveries.first { it.player == null }.message.type, MessageType.SUCCESS, "le compte est un succès")
        helper.assertValueEqual(deliveries.filter { it.player === stage.player }.map { it.message }, listOf(WarpsMessages.SENT_TO.with("far")), "le joueur envoyé l'apprend")
        helper.assertValueEqual(deliveries.filter { it.player === other }.map { it.message }, listOf(WarpsMessages.SENT_TO.with("far")), "l'autre joueur envoyé l'apprend")
        stage.assertPlayerAt(far, "envoyé tout de suite, sans délai, sans égard pour la dimension de départ interdite ni pour la clé d'accès")
        helper.assertValueEqual(other.position(), at(far), "l'autre joueur, envoyé aussi")
        helper.assertValueEqual(recording.journalRequests, 1, "l'envoi se journalise")
        helper.assertValueEqual(lab.runtime.returns[stage.player.uuid] ?: throw helper.assertionException("l'envoi n'a pas enregistré la position de retour"), departure.toPosition(), "l'endroit quitté devient la position de retour, par la source send")
        helper.assertTrue(lab.runtime.worldData?.warps?.content?.players?.get(stage.player.uuid) == null, "l'envoi ne compte aucun usage du quota")
        helper.assertValueEqual(run(lab, stage.player, "warps teleport far").messages, listOf(WarpsMessages.ABSENT.with("far")), "le joueur envoyé n'a pas la clé : pour lui, le warp n'existe pas")
        helper.assertValueEqual(run(lab, Harness.console(helper), "warps teleport nowhere ${playersAt(far)}").messages, listOf(WarpsMessages.ABSENT.with("nowhere")), "le warp absent")

        stage.place(other, departure)
        helper.assertValueEqual(run(lab, Harness.console(helper), "warps teleport far ${playersAt(departure)}").messages, listOf(WarpsMessages.SENT_ONE.with(1, "far")), "un seul joueur envoyé")

        val plain = Lab.open("warps-send-starts-no-cooldown", clock = ManualClock())
        createWarpAt(helper, plain, "far", far)
        stage.place(stage.player, departure)
        helper.assertValueEqual(run(plain, Harness.console(helper), "warps teleport far ${playersAt(departure)}").result, 1, "l'envoi sur un état aux règles livrées")
        helper.assertValueEqual(run(plain, stage.player, "warps teleport far").messages, listOf(CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3))), "l'envoi ne démarre pas le cooldown, et ne laisse aucune téléportation en attente")
        helper.succeed()
    }

    /** L'envoi garde le refus d'une arrivée dangereuse, contrôlée une fois pour tous sur un joueur qui n'en est pas dispensé : dangereuse, personne ne part. Tous dispensés, rien ne se contrôle. */
    @GameTest
    fun sendChecksTheArrivalOnceUnlessEveryoneIsExempt(helper: GameTestHelper) {
        val lab = Lab.open("warps-send-checks-the-arrival-once")
        val stage = TeleportStage(helper)
        val overTheVoid = stage.standing(5, 7, 1)
        createWarpAt(helper, lab, "void", overTheVoid)
        val departure = stage.departure
        val aside = stage.platform(1, 5)
        val other = creativePlayer(helper, stage, at = aside)
        val console = Harness.console(helper)

        val refused = run(lab, console, "warps teleport void ${playersAt(departure)}")
        helper.assertValueEqual(refused.messages, listOf(CommonMessages.TELEPORT_ARRIVAL_VOID.with()), "l'arrivée au-dessus du vide refuse l'envoi d'un joueur en survie")
        helper.assertValueEqual(refused.result, 0, "le résultat du refus")
        stage.assertPlayerAt(departure, "le joueur n'est pas parti")

        val both = Harness.record(lab.runtime) { lab.dispatcher.execute("warps teleport void ${playersBetween(departure, aside)}", console) }
        helper.assertValueEqual(both.map { it.message }, listOf(CommonMessages.TELEPORT_ARRIVAL_VOID.with()), "contrôlée une fois pour tous : dangereuse, personne ne part, et personne n'est prévenu d'un envoi")
        stage.assertPlayerAt(departure, "le joueur en survie n'est pas parti")
        helper.assertValueEqual(other.position(), at(aside), "le joueur dispensé n'est pas parti non plus")

        val exempt = run(lab, console, "warps teleport void ${playersAt(aside)}")
        helper.assertValueEqual(exempt.messages, listOf(WarpsMessages.SENT_ONE.with(1, "void")), "tous dispensés, l'arrivée ne se contrôle pas")
        helper.assertValueEqual(other.position(), at(overTheVoid), "le joueur dispensé est parti")
        helper.succeed()
    }

    // ── Les alias (chapitre 5) ──

    /** `/warp`, `/setwarp` et `/delwarp` répondent comme les sous-commandes qu'ils remplacent, `/warp` avec des `<targets>` compris, et un seul réglage les désactive ensemble. */
    @GameTest
    fun aliasesAnswerLikeTheirCommands(helper: GameTestHelper) {
        val lab = Lab.open("warps-aliases-answer-like-their-commands", rules = mapOf(WarpsRules to AT_ONCE))
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        val position = far.toPosition()
        val console = Harness.console(helper)

        val created = run(lab, console, "setwarp far ${placement(far)}")
        val arrived = run(lab, stage.player, "warp far")
        stage.place(stage.player, stage.departure)
        val sent = run(lab, console, "warp far ${playersAt(stage.departure)}")
        val deleted = run(lab, console, "delwarp far")
        val absent = run(lab, stage.player, "warp far")

        helper.assertValueEqual(created.messages, listOf(WarpsMessages.CREATED.with("far", position.dimension, position.blockX, position.blockY, position.blockZ)), "/setwarp crée")
        helper.assertValueEqual(arrived.messages, listOf(WarpsMessages.ARRIVED.with("far")), "/warp <name> rejoint le warp")
        helper.assertValueEqual(sent.messages, listOf(WarpsMessages.SENT_ONE.with(1, "far")), "/warp <name> <targets> envoie")
        stage.assertPlayerAt(far, "au warp, par l'alias")
        helper.assertValueEqual(deleted.messages, listOf(WarpsMessages.DELETED.with("far", position.dimension, position.blockX, position.blockY, position.blockZ)), "/delwarp supprime")
        helper.assertValueEqual(absent.messages, listOf(WarpsMessages.ABSENT.with("far")), "/warp sans warp refuse comme la commande")

        val withoutAliases = Lab.open("warps-aliases-answer-like-their-commands-without-aliases", rules = mapOf(WarpsRules to """{ "aliases": false }"""))
        listOf("warp", "setwarp", "delwarp").forEach { helper.assertTrue(withoutAliases.dispatcher.root.getChild(it) == null, "/$it n'est pas enregistré quand les alias sont inactifs") }
        helper.assertTrue(withoutAliases.dispatcher.root.getChild("warps") != null, "/warps reste enregistrée sans ses alias")
        helper.assertTrue(Harness.serverDispatcher(helper).root.getChild("warp") != null, "/warp est enregistré sur le serveur, où les alias sont actifs")
        helper.succeed()
    }

    // ── Le quota (chapitre 4) ──

    /** Le quota se conserve dans `warps.json` : la plage ouverte par une téléportation survit à un redémarrage, et se rouvre quand elle est finie. */
    @GameTest
    fun quotaWindowSurvivesARestart(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("warps-quota-window-survives-a-restart", rules = mapOf(WarpsRules to ONE_PER_HOUR), clock = clock)
        val stage = TeleportStage(helper)
        val far = stage.platform(5, 5)
        createWarpAt(helper, lab, "far", far)
        val windowStart = clock.instant().epochSecond

        helper.assertValueEqual(run(lab, stage.player, "warps teleport far").messages, listOf(WarpsMessages.ARRIVED.with("far")), "le premier usage")
        helper.assertTrue("\"window-start\": $windowStart" in lab.directory.resolve("world").resolve("warps.json").readText(), "la plage est écrite dans warps.json tout de suite")

        val restarted = Lab.reopen("warps-quota-window-survives-a-restart", clock = clock)
        stage.place(stage.player, stage.departure)
        helper.assertValueEqual(quotaOf(helper, restarted, stage.player), QuotaUsage(QuotaClock.REAL, windowStart, uses = 1), "la plage relue par l'état suivant")
        val reached = run(restarted, stage.player, "warps teleport far")
        helper.assertValueEqual(reached.messages, listOf(WarpsMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(3_600))), "le quota atteint survit au redémarrage")
        stage.assertPlayerAt(stage.departure, "le joueur n'a pas bougé")

        clock.advance(3_600)
        helper.assertValueEqual(run(restarted, stage.player, "warps teleport far").messages, listOf(WarpsMessages.ARRIVED.with("far")), "la plage finie en rouvre une")
        helper.assertValueEqual(quotaOf(helper, restarted, stage.player), QuotaUsage(QuotaClock.REAL, windowStart + 3_600, uses = 1), "la nouvelle plage, écrite à son tour")
        helper.succeed()
    }
}
