// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.Situation
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.math.BigDecimal
import java.util.UUID
import kotlin.io.path.readText

/**
 * La branche `/homes player <player>` (`homes.md` des specs, chapitre 4), jouée par un maître du jeu et par la console sur les homes d'un joueur
 * nommé : chaque situation du tableau des messages que la branche produit a son test, reconnue à sa clé, les règles qu'elle passe outre, la
 * journalisation, les nœuds, le registre.
 */
class HomesPlayerGameTests {

    private companion object {

        /** La limite vaut 1, le Nether est interdit aux homes et les homes ne se rejoignent pas depuis l'Overworld : des règles que la branche passe outre. Tout de suite, sans cooldown. */
        const val STRICT = """{ "rules": { "default": { "max-homes": 1, "forbidden-dimensions": ["minecraft:the_nether"], "forbidden-departure-dimensions": ["minecraft:overworld"], "delay": 0, "cooldown": 0 } } }"""

        /** Un nom de 33 caractères, un de trop. */
        const val TOO_LONG = "abcdefghijklmnopqrstuvwxyzabcdefg"
    }

    /** Ce qu'une commande a rendu, et les messages que chaque destinataire a reçus. */
    private class Outcome(val result: Int, val deliveries: List<Delivery>) {

        fun to(player: ServerPlayer?): List<Message> = deliveries.filter { it.player === player }.map { it.message }
    }

    private fun run(lab: Lab, source: CommandSourceStack, command: String): Outcome {
        var result = 0
        val deliveries = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, source) }
        return Outcome(result, deliveries)
    }

    /** Le nombre en écriture décimale, jamais scientifique, que les arguments du jeu ne lisent pas. */
    private fun Double.plain(): String = BigDecimal.valueOf(this).toPlainString()

    /** Le placement complet de [location], tel que `create` et `move` le lisent. */
    private fun placement(location: Location): String = "${location.x.plain()} ${location.y.plain()} ${location.z.plain()} ${location.yaw} ${location.pitch} ${location.dimension}"

    /** Le sélecteur du jeu qui désigne les joueurs qui se tiennent exactement à [location]. */
    private fun playersAt(location: Location): String = "@a[x=${location.x.plain()},y=${location.y.plain()},z=${location.z.plain()},distance=..0.5]"

    private fun at(location: Location): Vec3 = Vec3(location.x, location.y, location.z)

    /** `data\players.json` pour [players], connectés, et [others], des joueurs qui ne le sont pas, par leur UUID et leur nom. */
    private fun registry(players: List<ServerPlayer>, vararg others: Pair<UUID, String>): String {
        val entries = players.map { it.uuid to it.scoreboardName } + others
        return entries.joinToString(", ", prefix = """{ "players": { """, postfix = " } }") { (id, name) -> """"$id": { "name": "$name", "last-seen": "2026-10-05T12:00:00Z" }""" }
    }

    /** `data\memberships.json` où [player] suit `default` avec les nœuds [allow] en propre. */
    private fun memberships(player: ServerPlayer, vararg allow: String): String =
        """{ "players": { "${player.uuid}": { "name": "${player.scoreboardName}", "allow": [${allow.joinToString(", ") { "\"$it\"" }}] } } }"""

    /** Les homes de [id] en mémoire, aucun quand il n'a pas d'entrée. */
    private fun homesOf(lab: Lab, id: UUID): PlayerHomes = lab.runtime.worldData?.homes?.content?.get(id)?.holdings ?: PlayerHomes()

    /** Le message de la branche qui nomme un home, son propriétaire et sa position, comme la règle l'écrit. */
    private fun placed(situation: Situation, name: String, owner: String, position: Position): Message = situation.with(name, owner, position.dimension, position.blockX, position.blockY, position.blockZ)

    /**
     * Les situations « home créé », « home supprimé », « home renommé », « home déplacé » et « home par défaut désigné » de la branche : chaque
     * succès nomme le propriétaire, se journalise, et le propriétaire ne reçoit rien ; la limite et les dimensions interdites passent, par le
     * placement aussi ; le nom invalide, le nom déjà pris, le home absent, le `<pos>` hors du monde et le joueur inconnu refusent, `<player>` lu
     * sans la casse.
     */
    @GameTest
    fun commandsActOnTheHomesOfTheNamedPlayer(helper: GameTestHelper) {
        val admin = Harness.player(helper, "HomesAdminA")
        val owner = Harness.player(helper, "HomesOwnerA")
        val lab = Lab.open("homes-player-commands-act-on-the-named-player", rules = mapOf(HomesRules to STRICT))
        val source = Harness.asGameMaster(admin)
        val here = Location.of(admin).toPosition()
        val cave = Position("minecraft:the_nether", 0.5, 128.0, 0.5, 0f, 0f)

        val created = run(lab, source, "homes player homesownera create base")
        helper.assertValueEqual(created.result, 1, "le résultat de create")
        helper.assertValueEqual(created.to(admin), listOf(placed(HomesMessages.CREATED_OF_PLAYER, "base", "HomesOwnerA", here)), "la situation « home créé » de la branche, à la position de l'administrateur, sous le nom que le joueur porte")
        helper.assertValueEqual(created.to(admin).single().type, MessageType.SUCCESS, "un succès")
        helper.assertValueEqual(created.to(owner), emptyList(), "le propriétaire n'est pas prévenu")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA create camp").result, 1, "la limite de homes du joueur nommé passe")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA create cave ${placement(Location.of(helper.level.server, cave) ?: throw helper.assertionException("le Nether n'existe pas"))}").to(admin), listOf(placed(HomesMessages.CREATED_OF_PLAYER, "cave", "HomesOwnerA", cave)), "la dimension interdite aux homes passe, par le placement")
        helper.assertValueEqual(homesOf(lab, owner.uuid).homes.keys, setOf("base", "camp", "cave"), "les homes du joueur nommé en mémoire")
        helper.assertTrue(lab.directory.resolve("world").resolve("homes.json").readText().contains("\"${owner.uuid}\""), "les homes du joueur nommé sont écrits tout de suite")

        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA create $TOO_LONG").to(admin), listOf(CommonMessages.NAME_INVALID.with()), "le nom invalide")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA create base").to(admin), listOf(HomesMessages.ALREADY_EXISTS.with("base")), "le nom déjà pris")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA create sky 0.5 1000 0.5").to(admin), listOf(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with()), "le placement hors de la hauteur de la dimension")
        helper.assertValueEqual(run(lab, source, "homes player Herobrine create base").to(admin), listOf(HomesMessages.UNKNOWN_PLAYER.with("Herobrine")), "la situation « joueur inconnu »")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA delete nowhere").to(admin), listOf(HomesMessages.ABSENT.with("nowhere")), "le home absent")

        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA delete camp").to(admin), listOf(placed(HomesMessages.DELETED_OF_PLAYER, "camp", "HomesOwnerA", here)), "la situation « home supprimé » de la branche")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA rename base camp").to(admin), listOf(HomesMessages.RENAMED_OF_PLAYER.with("base", "HomesOwnerA", "camp")), "la situation « home renommé » de la branche")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerA move camp ${placement(Location.of(helper.level.server, cave) ?: throw helper.assertionException("le Nether n'existe pas"))}").to(admin), listOf(placed(HomesMessages.MOVED_OF_PLAYER, "camp", "HomesOwnerA", cave)), "la situation « home déplacé » de la branche, vers la dimension interdite")
        val recording = Harness.RecordingSource()
        val designated = Harness.record(lab.runtime) { lab.dispatcher.execute("homes player HomesOwnerA default cave", source.withSource(recording)) }.map { it.message }
        helper.assertValueEqual(designated, listOf(HomesMessages.DEFAULT_SET_OF_PLAYER.with("HomesOwnerA", "cave")), "la situation « home par défaut désigné » de la branche")
        helper.assertValueEqual(recording.journalRequests, 1, "une commande de la branche se journalise")
        lab.dispatcher.execute("homes player HomesOwnerA delete nowhere", source.withSource(recording))
        helper.assertValueEqual(recording.journalRequests, 1, "un refus ne se journalise pas")
        helper.assertValueEqual(homesOf(lab, owner.uuid).designated ?: throw helper.assertionException("aucun home par défaut n'est désigné"), "cave", "le home par défaut du joueur nommé")
        helper.succeed()
    }

    /** La liste du joueur nommé : l'en-tête nomme le joueur et compte sans limite, chaque home se clique pour la commande de la branche, la liste vide ; l'en-tête seul se journalise ; la console la lit. */
    @GameTest
    fun listShowsTheHomesOfTheNamedPlayer(helper: GameTestHelper) {
        val admin = Harness.player(helper, "HomesAdminB")
        Harness.player(helper, "HomesOwnerB")
        val lab = Lab.open("homes-player-list-shows-the-homes-of-the-named-player", rules = mapOf(HomesRules to STRICT))
        val source = Harness.asGameMaster(admin)
        run(lab, source, "homes player HomesOwnerB create base")
        run(lab, source, "homes player HomesOwnerB create mine")

        val recording = Harness.RecordingSource()
        var result = 0
        val list = Harness.record(lab.runtime) { result = lab.dispatcher.execute("homes player HomesOwnerB list", source.withSource(recording)) }.map { it.message }
        val empty = run(lab, source, "homes player HomesAdminB list")
        val console = run(lab, Harness.console(helper), "homes player HomesOwnerB list")

        helper.assertValueEqual(result, 2, "le résultat de list, le nombre de homes")
        helper.assertValueEqual(list.first(), HomesMessages.LIST_HEADER_OF_PLAYER.with("HomesOwnerB", 2), "l'en-tête nomme le joueur et compte, sans limite")
        helper.assertValueEqual(list.drop(1).map { it.key }, listOf(HomesMessages.LIST_HOME.key, HomesMessages.LIST_HOME.key), "une ligne par home, aucun par défaut à deux homes sans désignation")
        helper.assertValueEqual(list[1].interaction?.clickCommand ?: throw helper.assertionException("la ligne d'un home ne se clique pas"), "/homes player HomesOwnerB teleport base", "le clic lance la commande de la branche")
        helper.assertValueEqual(recording.journalRequests, 1, "l'en-tête seul se journalise, la trace de la consultation")
        helper.assertValueEqual(empty.to(admin), listOf(HomesMessages.LIST_HEADER_OF_PLAYER.with("HomesAdminB", 0), HomesMessages.LIST_EMPTY.with()), "la liste vide")
        helper.assertValueEqual(console.to(null).size, 3, "la console lit la liste")
        helper.succeed()
    }

    /**
     * `teleport <name>` de la branche : l'administrateur rejoint le home du joueur nommé par le déroulé commun, sous ses propres règles, sans la
     * dimension de départ interdite ; l'arrivée se dit à l'administrateur, journalisée, l'endroit quitté devient sa position de retour ; le home
     * absent ; la console sans `<targets>` est refusée.
     */
    @GameTest
    fun teleportBringsTheAdministratorToTheHomeOfThePlayer(helper: GameTestHelper) {
        val lab = Lab.open("homes-player-teleport-brings-the-administrator", rules = mapOf(HomesRules to STRICT))
        val stage = TeleportStage(helper)
        stage.namedPlayer("HomesOwnerC")
        val far = stage.platform(5, 5)
        helper.assertValueEqual(run(lab, Harness.console(helper), "homes player HomesOwnerC create far ${placement(far)}").result, 1, "la création du home far")
        val admin = stage.player
        val source = Harness.asGameMaster(admin)
        val recording = Harness.RecordingSource()

        var result = 0
        val deliveries = Harness.record(lab.runtime) {
            result = lab.dispatcher.execute("homes player HomesOwnerC teleport far", source.withSource(recording))
            lab.runtime.teleports.tick()
        }

        helper.assertValueEqual(result, 1, "le résultat de teleport")
        helper.assertValueEqual(deliveries.filter { it.player === admin }.map { it.message }, listOf(HomesMessages.ARRIVED_OF_PLAYER.with("far", "HomesOwnerC")), "la situation « arrivée » de la branche, tout de suite à délai 0, malgré la dimension de départ interdite")
        helper.assertValueEqual(recording.journalRequests, 1, "l'arrivée se journalise")
        stage.assertPlayerAt(far, "au home du joueur nommé")
        helper.assertValueEqual(lab.runtime.returns[admin.uuid] ?: throw helper.assertionException("la téléportation n'a pas enregistré la position de retour"), stage.departure.toPosition(), "l'endroit quitté devient la position de retour de l'administrateur")
        helper.assertValueEqual(run(lab, source, "homes player HomesOwnerC teleport nowhere").to(admin), listOf(HomesMessages.ABSENT.with("nowhere")), "le home absent")
        helper.assertValueEqual(run(lab, Harness.console(helper), "homes player HomesOwnerC teleport far").to(null), listOf(CommonMessages.IN_GAME_ONLY.with()), "la console sans targets est refusée")
        helper.succeed()
    }

    /**
     * `teleport <name> <targets>` de la branche : l'envoi, tout de suite, sans le déroulé ; chaque joueur envoyé l'apprend, l'endroit quitté
     * devient sa position de retour par la source `send`, et l'administrateur reçoit le compte, journalisé ; le home absent.
     */
    @GameTest
    fun sendBringsTheTargetsToTheHomeOfThePlayer(helper: GameTestHelper) {
        val lab = Lab.open("homes-player-send-brings-the-targets", rules = mapOf(HomesRules to STRICT))
        val stage = TeleportStage(helper)
        val owner = stage.namedPlayer("HomesOwnerD")
        val far = stage.platform(5, 5)
        helper.assertValueEqual(run(lab, Harness.console(helper), "homes player HomesOwnerD create far ${placement(far)}").result, 1, "la création du home far")
        val departure = stage.departure
        val recording = Harness.RecordingSource()

        var result = 0
        val deliveries = Harness.record(lab.runtime) { result = lab.dispatcher.execute("homes player HomesOwnerD teleport far ${playersAt(departure)}", Harness.console(helper).withSource(recording)) }

        helper.assertValueEqual(result, 2, "le résultat de l'envoi, le nombre de joueurs envoyés")
        helper.assertValueEqual(deliveries.filter { it.player == null }.map { it.message }, listOf(HomesMessages.SENT_MANY.with(2, "far", "HomesOwnerD")), "la situation « joueurs envoyés », chez l'administrateur")
        helper.assertValueEqual(deliveries.filter { it.player === stage.player }.map { it.message }, listOf(HomesMessages.SENT_TO.with("far", "HomesOwnerD")), "la situation « envoi », chez le joueur envoyé")
        helper.assertValueEqual(deliveries.filter { it.player === owner }.map { it.message }, listOf(HomesMessages.SENT_TO.with("far", "HomesOwnerD")), "chez l'autre joueur envoyé, le propriétaire lui-même")
        stage.assertPlayerAt(far, "envoyé tout de suite, sans délai")
        helper.assertValueEqual(owner.position(), at(far), "le propriétaire, envoyé aussi")
        helper.assertValueEqual(recording.journalRequests, 1, "l'envoi se journalise")
        helper.assertValueEqual(lab.runtime.returns[stage.player.uuid] ?: throw helper.assertionException("l'envoi n'a pas enregistré la position de retour"), departure.toPosition(), "l'endroit quitté devient la position de retour, par la source send")
        helper.assertValueEqual(run(lab, Harness.console(helper), "homes player HomesOwnerD teleport nowhere ${playersAt(far)}").to(null), listOf(HomesMessages.ABSENT.with("nowhere")), "le home absent")
        helper.succeed()
    }

    /** Depuis la console : `list`, `delete`, `rename` et `default` s'exécutent, `create` et `move` avec un `<pos>` ; sans `<pos>`, elles sont refusées. */
    @GameTest
    fun consoleRunsWhatNeedsNoPosition(helper: GameTestHelper) {
        Harness.player(helper, "HomesOwnerE")
        val lab = Lab.open("homes-player-console-runs-what-needs-no-position")
        val console = Harness.console(helper)

        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE create base").to(null), listOf(CommonMessages.IN_GAME_ONLY.with()), "create sans pos, depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE create base 1 64 1").result, 1, "create avec un pos, depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE move base").to(null), listOf(CommonMessages.IN_GAME_ONLY.with()), "move sans pos, depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE move base 2 64 2").result, 1, "move avec un pos, depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE rename base camp").result, 1, "rename depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE default camp").result, 1, "default depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE list").result, 1, "list depuis la console")
        helper.assertValueEqual(run(lab, console, "homes player HomesOwnerE delete camp").result, 1, "delete depuis la console")
        helper.succeed()
    }

    /**
     * Les nœuds de la branche : un joueur ne la voit pas, un maître du jeu et la console l'ont entière ; avec le seul nœud de l'envoi, `teleport`
     * sans `<targets>` est inconnue ; le joueur nommé hors ligne se résout par le registre, sous son dernier nom connu ; `<player>` se complète
     * avec les joueurs connectés et `<name>` avec les homes du joueur nommé.
     */
    @GameTest
    fun branchFollowsItsNodesAndTheRegistry(helper: GameTestHelper) {
        val visitor = Harness.player(helper, "HomesVisitorF")
        val sender = Harness.player(helper, "HomesSenderF")
        val admin = Harness.player(helper, "HomesAdminF")
        val offline = UUID.randomUUID()
        val lab = Lab.open(
            "homes-player-branch-follows-its-nodes", rules = mapOf(HomesRules to STRICT),
            serverData = mapOf("players" to registry(listOf(visitor, sender, admin), offline to "HomesOfflineF"), "memberships" to memberships(sender, "manymanycommands.homes.player.teleport.others")),
        )
        val source = Harness.asGameMaster(admin)
        val branch = lab.dispatcher.root.getChild("homes").getChild("player")

        helper.assertFalse(branch.canUse(visitor.createCommandSourceStack()), "la branche n'existe pas pour un joueur")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, visitor.createCommandSourceStack(), "homes player HomesAdminF list")
        helper.assertTrue(branch.canUse(source), "le maître du jeu a la branche")
        helper.assertTrue(branch.canUse(Harness.console(helper)), "la console a la branche")
        helper.assertValueEqual(run(lab, source, "homes player HomesAdminF create base").result, 1, "l'administrateur se crée un home par la branche")
        Harness.assertUnknownCommand(helper, lab.dispatcher, sender.createCommandSourceStack(), "homes player HomesAdminF teleport base")

        val created = run(lab, source, "homes player homesofflinef create base")
        helper.assertValueEqual(created.to(admin), listOf(placed(HomesMessages.CREATED_OF_PLAYER, "base", "HomesOfflineF", Location.of(admin).toPosition())), "le joueur hors ligne se désigne par le registre, sous son dernier nom connu")
        helper.assertValueEqual(homesOf(lab, offline).homes.keys, setOf("base"), "le home du joueur hors ligne, sous son UUID")
        helper.assertTrue(lab.directory.resolve("world").resolve("homes.json").readText().contains("\"HomesOfflineF\""), "l'entrée du joueur hors ligne porte son nom")

        val dispatcher = lab.dispatcher
        val players = dispatcher.getCompletionSuggestions(dispatcher.parse("homes player ", source)).join().list.map { it.text }
        val names = dispatcher.getCompletionSuggestions(dispatcher.parse("homes player HomesAdminF delete ", source)).join().list.map { it.text }
        helper.assertTrue(listOf("HomesVisitorF", "HomesSenderF", "HomesAdminF").all { it in players }, "la complétion de <player> propose les joueurs connectés")
        helper.assertValueEqual(names, listOf("base"), "la complétion de <name> propose les homes du joueur nommé")
        helper.succeed()
    }
}
