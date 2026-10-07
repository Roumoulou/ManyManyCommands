// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.wild.Column
import fr.roumoulou.manymanycommands.rules.wild.WildMessages
import fr.roumoulou.manymanycommands.rules.wild.WildNodes
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import fr.roumoulou.manymanycommands.rules.wild.WildZone
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * `/wild`, `/wild timed` et `/rtp` (`wild.md` des specs), joués par un joueur simulé en survie sur le monde plat du serveur GameTest : le point
 * tiré dans la couronne, à la surface, le déroulé commun, les refus dans leur ordre, la recherche qui échoue, le tirage commun et ses créneaux.
 * Chaque test tire près du point d'apparition, pour ne charger que quelques chunks.
 */
class WildCommandGameTests {

    private companion object {

        /** Une couronne de 100 à 130 blocs autour du point d'apparition, rejointe tout de suite et sans cooldown. */
        const val NEAR_AT_ONCE = """{ "min-distance": 100, "max-distance": 130, "rules": { "default": { "delay": 0, "cooldown": 0 } } }"""

        const val MIN = 100L
        const val MAX = 130L
    }

    /** Ce qu'une commande a rendu, et les messages que le joueur a reçus. */
    private class Outcome(val result: Int, val messages: List<Message>)

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome {
        var result = 0
        val messages = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, player.createCommandSourceStack()) }.filter { it.player === player }.map { it.message }
        return Outcome(result, messages)
    }

    /** La couronne des tests, autour du point d'apparition du monde. */
    private fun zone(helper: GameTestHelper, min: Long = MIN, max: Long = MAX): WildZone {
        val spawn: BlockPos = helper.level.server.overworld().respawnData.pos()
        return WildZone(spawn.x.toLong(), spawn.z.toLong(), min, max)
    }

    private fun columnOf(player: ServerPlayer): Column = Column(player.blockX.toLong(), player.blockZ.toLong())

    /** Le joueur se tient à la surface de sa colonne, sur un bloc solide, hors de l'eau, dans la bordure du monde. */
    private fun assertOnASafeSurface(helper: GameTestHelper, player: ServerPlayer, what: String) {
        val level = player.level()
        val feet = player.blockPosition()
        val ground = feet.below()
        helper.assertTrue(!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty, "sur un bloc solide, $what")
        helper.assertTrue(level.getFluidState(feet).isEmpty && level.getFluidState(ground).isEmpty, "hors de l'eau, $what")
        helper.assertTrue(level.worldBorder.isWithinBounds(player.x, player.z), "dans la bordure du monde, $what")
        helper.assertValueEqual(feet.y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.x, feet.z), "à la surface, $what")
    }

    /** L'arrivée dite par la famille : ses coordonnées entières sont celles du joueur. */
    private fun assertArrived(helper: GameTestHelper, message: Message, player: ServerPlayer, what: String) {
        helper.assertValueEqual(message, WildMessages.ARRIVED.with(player.blockX, player.blockY, player.blockZ), "l'arrivée, $what")
    }

    // ── Le tirage individuel (chapitres 2, 3 et 5) ──

    /** `/wild` tire un point de la couronne, à la surface, dans la dimension du joueur, le dit en succès avec ses coordonnées entières, enregistre la position de retour et compte l'usage dans `wild.json`. */
    @GameTest
    fun wildTeleportsToASafePointOfTheZone(helper: GameTestHelper) {
        val lab = Lab.open("wild-teleports-to-a-safe-point-of-the-zone", rules = mapOf(WildRules to NEAR_AT_ONCE))
        val stage = TeleportStage(helper)

        val outcome = run(lab, stage.player, "wild")

        helper.assertValueEqual(outcome.result, 1, "le résultat de /wild")
        assertArrived(helper, outcome.messages.single(), stage.player, "avec les coordonnées entières du point")
        helper.assertValueEqual(outcome.messages.single().type, MessageType.SUCCESS, "l'arrivée est un succès")
        helper.assertTrue(columnOf(stage.player) in zone(helper), "le point est dans la couronne : ${columnOf(stage.player)}")
        helper.assertValueEqual(stage.player.level().dimension(), stage.departure.level.dimension(), "dans la dimension du joueur")
        assertOnASafeSurface(helper, stage.player, "au point tiré")
        helper.assertValueEqual(lab.runtime.returns[stage.player.uuid] ?: throw helper.assertionException("l'arrivée n'a pas enregistré la position de retour"), stage.departure.toPosition(), "l'endroit quitté devient la position de retour, par la source wild")
        val quota = lab.runtime.worldData?.wild?.content?.get(stage.player.uuid)?.quota ?: throw helper.assertionException("l'usage n'est pas compté dans wild.json")
        helper.assertValueEqual(quota.uses, 1L, "le quota livré, 3 par 8 heures, compte l'usage")
        helper.assertTrue("\"window-start\"" in lab.directory.resolve("world").resolve("wild.json").readText(), "la plage est écrite dans wild.json tout de suite")
        helper.succeed()
    }

    /** `/wild` suit le déroulé commun : le compte à rebours, l'arrivée le délai écoulé, puis le cooldown livré d'une heure. */
    @GameTest
    fun wildRunsThroughTheCommonFlow(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("wild-runs-through-the-common-flow", rules = mapOf(WildRules to """{ "min-distance": 100, "max-distance": 130 }"""), clock = clock)
        val stage = TeleportStage(helper)

        val launched = run(lab, stage.player, "wild")
        clock.advance(3)
        val arrival = Harness.record(lab.runtime) { lab.runtime.teleports.tick() }.filter { it.player === stage.player }.map { it.message }
        val onCooldown = run(lab, stage.player, "wild")

        helper.assertValueEqual(launched.result, 1, "le résultat de /wild lancée")
        helper.assertValueEqual(launched.messages, listOf(CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(3))), "le compte à rebours part au lancement, le point déjà tiré")
        assertArrived(helper, arrival.single(), stage.player, "le délai écoulé")
        helper.assertTrue(columnOf(stage.player) in zone(helper), "le point est dans la couronne")
        helper.assertValueEqual(onCooldown.messages, listOf(CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(3_600))), "le cooldown livré court")
        helper.assertValueEqual(onCooldown.result, 0, "le résultat d'un refus du déroulé")
        helper.succeed()
    }

    /** Les refus propres dans l'ordre de la fiche : la dimension interdite, le Nether par défaut, avant le quota atteint avec son attente ; et la console, refusée. */
    @GameTest
    fun wildRefusesInTheOrderOfItsSheetAndRefusesTheConsole(helper: GameTestHelper) {
        val onePerMinute = """{ "min-distance": 100, "max-distance": 130, "rules": { "default": { "delay": 0, "cooldown": 0, "quota": { "uses": 1, "period": 60 } } } }"""
        val lab = Lab.open("wild-refuses-in-the-order-of-its-sheet", rules = mapOf(WildRules to onePerMinute), clock = ManualClock())
        val stage = TeleportStage(helper)
        val nether = stage.netherPlatform()

        stage.place(stage.player, nether)
        val forbidden = run(lab, stage.player, "wild")
        helper.assertValueEqual(forbidden.messages, listOf(WildMessages.DEPARTURE_FORBIDDEN.with("minecraft:the_nether")), "la dimension interdite, premier refus")
        helper.assertValueEqual(forbidden.result, 0, "le résultat du refus")
        stage.assertPlayerAt(nether, "le joueur n'a pas bougé")

        stage.place(stage.player, stage.departure)
        assertArrived(helper, run(lab, stage.player, "wild").messages.single(), stage.player, "le quota s'use d'abord")
        val quota = run(lab, stage.player, "wild")
        helper.assertValueEqual(quota.messages, listOf(WildMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(60))), "le quota atteint, deuxième refus, avec l'attente")
        stage.place(stage.player, nether)
        helper.assertValueEqual(run(lab, stage.player, "wild").messages, listOf(WildMessages.DEPARTURE_FORBIDDEN.with("minecraft:the_nether")), "la dimension interdite passe avant le quota")

        var result = 1
        val console = Harness.keysSentTo(null, lab.runtime) { result = lab.dispatcher.execute("wild", Harness.console(helper)) }
        helper.assertValueEqual(console, listOf(CommonMessages.IN_GAME_ONLY.key), "la réponse de /wild à la console")
        helper.assertValueEqual(result, 0, "le résultat de /wild à la console")
        helper.succeed()
    }

    /**
     * La recherche qui échoue : la couronne réduite aux neuf colonnes autour du point d'apparition, toutes couvertes d'eau le temps de la
     * commande, ne donne aucun point sûr en dix tirages. Le refus ne démarre pas le cooldown et ne compte aucun usage. Une couronne que la
     * bordure du monde exclut est refusée sans rien tirer, par son propre refus.
     */
    @GameTest
    fun noSafePointAndANarrowBorderRefuseWithoutACooldownOrAUsage(helper: GameTestHelper) {
        val lab = Lab.open("wild-no-safe-point", rules = mapOf(WildRules to """{ "min-distance": 0, "max-distance": 1, "rules": { "default": { "delay": 0 } } }"""))
        val stage = TeleportStage(helper)
        val level = helper.level.server.overworld()
        val spawn = level.respawnData.pos()
        val surface = (-1..1).flatMap { dx ->
            (-1..1).map { dz ->
                val x = spawn.x + dx
                val z = spawn.z + dz
                level.getChunk(x shr 4, z shr 4)
                BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z)
            }
        }

        surface.forEach { level.setBlockAndUpdate(it, Blocks.WATER.defaultBlockState()) }
        val none = try {
            run(lab, stage.player, "wild")
        } finally {
            surface.forEach { level.setBlockAndUpdate(it, Blocks.AIR.defaultBlockState()) }
        }
        helper.assertValueEqual(none.messages, listOf(WildMessages.NO_SAFE_POINT.with()), "aucun point sûr : toute la couronne est sous l'eau")
        helper.assertValueEqual(none.result, 0, "le résultat du refus")
        helper.assertValueEqual(none.messages.single().type, MessageType.REFUSAL, "un refus")
        stage.assertPlayerAt(stage.departure, "le joueur n'a pas bougé")
        helper.assertTrue(lab.runtime.worldData?.wild?.content?.get(stage.player.uuid) == null, "aucun usage compté")
        assertArrived(helper, run(lab, stage.player, "wild").messages.single(), stage.player, "aucun cooldown n'a démarré : l'eau retirée, le tirage suivant part")
        helper.assertTrue(columnOf(stage.player) in zone(helper, 0, 1), "dans la couronne réduite")

        val excluded = Lab.open("wild-narrow-border", rules = mapOf(WildRules to """{ "min-distance": 100000000, "max-distance": 100000001, "rules": { "default": { "delay": 0 } } }"""))
        val narrow = run(excluded, stage.player, "wild")
        helper.assertValueEqual(narrow.messages, listOf(WildMessages.BORDER_TOO_NARROW.with()), "la bordure exclut toute la couronne")
        helper.assertValueEqual(narrow.result, 0, "le résultat du refus")
        helper.succeed()
    }

    // ── Le tirage commun (chapitre 4) ──

    /**
     * `/wild timed` : le premier joueur ouvre un créneau et le dit, les suivants rejoignent le même point tant qu'il dure, avec ce qui en reste ;
     * le créneau fini, le suivant ouvre un nouveau créneau. Chaque dimension a le sien : depuis le Nether, le créneau de l'Overworld ne se
     * rejoint pas. Une commande refusée, depuis l'End interdit, n'en ouvre pas.
     */
    @GameTest
    fun timedSharesThePointOfTheWindowPerDimension(helper: GameTestHelper) {
        val clock = ManualClock()
        val tenSeconds = """{ "min-distance": 100, "max-distance": 130, "timed-window": 10, "rules": { "default": { "delay": 0, "cooldown": 0, "forbidden-departure-dimensions": ["minecraft:the_end"] } } }"""
        val lab = Lab.open("wild-timed-shares-the-point", rules = mapOf(WildRules to tenSeconds), clock = clock)
        val stage = TeleportStage(helper)
        val second = Harness.player(helper).also { stage.place(it, stage.departure) }
        val third = Harness.player(helper).also { stage.place(it, stage.departure) }

        val opened = run(lab, stage.player, "wild timed")
        helper.assertValueEqual(opened.result, 1, "le résultat de /wild timed")
        helper.assertValueEqual(opened.messages.size, 2, "le créneau ouvert, puis l'arrivée")
        helper.assertValueEqual(opened.messages[0], WildMessages.TIMED_OPENED.with(DurationWording.text(10)), "le premier joueur ouvre un créneau de la durée réglée")
        helper.assertValueEqual(opened.messages[0].type, MessageType.DISPLAY, "le créneau est un affichage")
        assertArrived(helper, opened.messages[1], stage.player, "le point tiré")
        val point = stage.player.position()

        clock.advance(4)
        val joined = run(lab, second, "wild timed")
        helper.assertValueEqual(joined.messages[0], WildMessages.TIMED_JOINED.with(DurationWording.text(6)), "le second joueur rejoint le créneau, avec ce qui en reste")
        assertArrived(helper, joined.messages[1], second, "au point du créneau")
        helper.assertValueEqual(second.position(), point, "le même point que le premier joueur")

        clock.advance(6)
        val reopened = run(lab, third, "wild timed")
        helper.assertValueEqual(reopened.messages[0], WildMessages.TIMED_OPENED.with(DurationWording.text(10)), "le créneau fini, le suivant en ouvre un nouveau")

        stage.place(stage.player, stage.netherPlatform())
        // Le relief du Nether du serveur GameTest change avec la version : la recherche y aboutit ou non, mais rien ne s'y rejoint.
        val nether = run(lab, stage.player, "wild timed")
        helper.assertTrue(nether.messages.none { it.key == WildMessages.TIMED_JOINED.key }, "dans le Nether, le créneau de l'Overworld ne se rejoint pas")
        val overworldWindow = lab.runtime.wildWindows.current("minecraft:overworld", clock.instant()) ?: throw helper.assertionException("l'Overworld a perdu son créneau")
        helper.assertValueEqual(overworldWindow.position, Location.of(third).toPosition(), "le créneau de l'Overworld est resté celui du troisième joueur")
        lab.runtime.wildWindows.current("minecraft:the_nether", clock.instant())?.let { helper.assertValueEqual(it.position.dimension, "minecraft:the_nether", "le créneau du Nether, s'il s'est ouvert, est dans le Nether") }

        val end = helper.level.server.getLevel(Level.END) ?: throw helper.assertionException("l'End n'existe pas sur le serveur GameTest")
        stage.player.teleportTo(end, 0.5, 64.0, 0.5, emptySet(), 0f, 0f, true)
        val refused = run(lab, stage.player, "wild timed")
        helper.assertValueEqual(refused.messages, listOf(WildMessages.DEPARTURE_FORBIDDEN.with("minecraft:the_end")), "l'End est interdit au départ")
        helper.assertTrue(lab.runtime.wildWindows.current("minecraft:the_end", clock.instant()) == null, "une commande refusée n'ouvre pas de créneau")
        helper.succeed()
    }

    // ── L'alias, les données, l'activation, les nœuds ──

    /** `/rtp` et `/rtp timed` répondent comme `/wild` et `/wild timed`, et un réglage les désactive. */
    @GameTest
    fun aliasAnswersLikeTheCommand(helper: GameTestHelper) {
        val lab = Lab.open("wild-alias-answers-like-the-command", rules = mapOf(WildRules to NEAR_AT_ONCE))
        val stage = TeleportStage(helper)

        assertArrived(helper, run(lab, stage.player, "rtp").messages.single(), stage.player, "/rtp tire un point")
        helper.assertTrue(columnOf(stage.player) in zone(helper), "le point est dans la couronne")
        val timed = run(lab, stage.player, "rtp timed")
        helper.assertValueEqual(timed.messages[0], WildMessages.TIMED_OPENED.with(DurationWording.text(10)), "/rtp timed ouvre un créneau")

        val withoutAlias = Lab.open("wild-alias-answers-like-the-command-without-alias", rules = mapOf(WildRules to """{ "aliases": false }"""))
        helper.assertTrue(withoutAlias.dispatcher.root.getChild("rtp") == null, "/rtp n'est pas enregistré quand l'alias est inactif")
        helper.assertTrue(withoutAlias.dispatcher.root.getChild("wild") != null, "/wild reste enregistrée sans son alias")
        helper.assertTrue(Harness.serverDispatcher(helper).root.getChild("rtp") != null, "/rtp est enregistré sur le serveur, où l'alias est actif")
        helper.succeed()
    }

    /**
     * Sur un `wild.json` invalide, la famille est indisponible jusqu'au rechargement du fichier corrigé, et le fichier fautif n'est pas réécrit.
     * Inactive au démarrage, elle n'enregistre ni `/wild` ni `/rtp`. Et les commandes suivent leurs nœuds : sans celui de `wild`, `/wild` seul
     * est inconnue et `timed` reste ; sans celui de `timed`, `timed` est un argument incorrect ; sans les deux, `/wild` n'existe pas.
     */
    @GameTest
    fun wildIsUnavailableDisabledOrHiddenByItsNodes(helper: GameTestHelper) {
        val lab = Lab.open("wild-is-unavailable-on-an-invalid-data-file", rules = mapOf(WildRules to NEAR_AT_ONCE), data = mapOf("wild" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))
        val stage = TeleportStage(helper)
        val file = lab.directory.resolve("world").resolve("wild.json")
        val faulty = file.readBytes()
        val console = Harness.console(helper)
        listOf("wild", "wild timed").forEach { command ->
            val outcome = run(lab, stage.player, command)
            helper.assertValueEqual(outcome.messages, listOf(WildMessages.UNAVAILABLE.with()), "la réponse de /$command sur des données invalides")
            helper.assertValueEqual(outcome.result, 0, "le résultat de /$command sur des données invalides")
        }
        helper.assertTrue(faulty.contentEquals(file.readBytes()), "le fichier fautif n'est pas réécrit")
        val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", console) }
        helper.assertValueEqual(status[2 + Harness.FILES.indexOf("world/wild.json")], ModMessages.STATUS_FILE_REFUSED.key, "l'état montre world/wild.json refusé")
        lab.writeData("wild", """{ "players": {} }""")
        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", console) }.map { it.message }
        helper.assertValueEqual(reload[Harness.FILES.indexOf("world/wild.json")], ModMessages.RELOAD_FILE_APPLIED.with("world/wild.json"), "le fichier corrigé s'applique au rechargement")
        assertArrived(helper, run(lab, stage.player, "wild").messages.single(), stage.player, "la famille remise en service")

        val disabled = Lab.open("disabled-wild-registers-no-command", rules = mapOf(WildRules to """{ "enabled": false }"""))
        listOf("wild", "rtp").forEach { helper.assertTrue(disabled.dispatcher.root.getChild(it) == null, "/$it n'est pas enregistré quand la famille est inactive") }

        val individual = Harness.player(helper).also { stage.place(it, stage.departure) }
        val timed = Harness.player(helper).also { stage.place(it, stage.departure) }
        val nobody = Harness.player(helper)
        val nodes = Lab.open("wild-commands-follow-their-nodes", rules = mapOf(WildRules to NEAR_AT_ONCE), permissionSource = { source, node ->
            when {
                node !in WildNodes.ALL -> null
                source.player === individual -> node == WildNodes.WILD
                source.player === timed -> node == WildNodes.TIMED
                source.player === nobody -> false
                else -> null
            }
        })
        Harness.assertUnknownSubcommand(helper, nodes.dispatcher, individual.createCommandSourceStack(), "wild timed")
        helper.assertValueEqual(run(nodes, individual, "wild").result, 1, "le joueur qui n'a que wild tire seul")
        Harness.assertUnknownCommand(helper, nodes.dispatcher, timed.createCommandSourceStack(), "wild")
        helper.assertValueEqual(run(nodes, timed, "wild timed").result, 1, "le joueur qui n'a que timed rejoint le tirage commun")
        helper.assertFalse(nodes.dispatcher.root.getChild("wild").canUse(nobody.createCommandSourceStack()), "/wild n'existe pas pour le joueur privé de ses deux nœuds")
        Harness.assertUnknownCommand(helper, nodes.dispatcher, nobody.createCommandSourceStack(), "wild")
        helper.succeed()
    }
}
