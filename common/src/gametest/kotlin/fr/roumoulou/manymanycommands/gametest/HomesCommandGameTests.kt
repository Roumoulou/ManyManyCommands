// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.homes.HomesListing
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesNodes
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.Situation
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * `/homes` et ses sous-commandes sans téléportation (`homes.md` des specs, chapitre 3), jouées par un joueur simulé : chaque situation du tableau
 * des messages que ces commandes produisent a son test, reconnue à sa clé, avec l'ordre des refus de chaque fiche.
 */
class HomesCommandGameTests {

    private companion object {

        /** Le fichier de règles où le Nether est interdit aux homes et où la limite vaut 1. */
        const val STRICT_RULES = """{ "rules": { "default": { "max-homes": 1, "forbidden-dimensions": ["minecraft:the_nether"] } } }"""

        const val FORBIDDEN_NETHER = """{ "rules": { "default": { "forbidden-dimensions": ["minecraft:the_nether"] } } }"""
    }

    /** Ce qu'une commande a rendu, et les messages que le joueur a reçus. */
    private class Outcome(val result: Int, val messages: List<Message>)

    private fun run(runtime: ModRuntime, dispatcher: CommandDispatcher<CommandSourceStack>, player: ServerPlayer, command: String): Outcome {
        var result = 0
        val messages = Harness.record(runtime) { result = dispatcher.execute(command, player.createCommandSourceStack()) }.filter { it.player === player }.map { it.message }
        return Outcome(result, messages)
    }

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome = run(lab.runtime, lab.dispatcher, player, command)

    private fun positionOf(player: ServerPlayer): Position = Location.of(player).toPosition()

    /** Le message qui nomme un home et sa position, comme la règle l'écrit. */
    private fun placed(situation: Situation, name: String, position: Position): Message = situation.with(name, position.dimension, position.blockX, position.blockY, position.blockZ)

    /** Les homes de [player] en mémoire, aucun quand il n'a pas d'entrée. */
    private fun homesOf(runtime: ModRuntime, player: ServerPlayer): PlayerHomes = runtime.worldData?.homes?.content?.get(player.uuid)?.holdings ?: PlayerHomes()

    /** Emmène [player] dans le Nether, au-dessus de son toit de bedrock, et rend d'où il vient. */
    private fun sendToTheNether(helper: GameTestHelper, player: ServerPlayer): Location {
        val nether = helper.level.server.getLevel(Level.NETHER) ?: throw helper.assertionException("le Nether n'existe pas sur le serveur GameTest")
        val from = Location.of(player)
        player.teleportTo(nether, 0.5, 128.0, 0.5, emptySet(), 0f, 0f, true)
        return from
    }

    private fun bringBack(player: ServerPlayer, to: Location) {
        player.teleportTo(to.level, to.x, to.y, to.z, emptySet(), to.yaw, to.pitch, true)
    }

    // ── Créer (chapitre 3.1) ──

    /** `create` fait un home à la position du joueur, répond en succès avec sa dimension et ses coordonnées entières, et l'écrit tout de suite dans le `homes.json` du monde. */
    @GameTest
    fun createMakesAHomeAtThePlayerPositionAndWritesIt(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val player = Harness.player(helper)
        val position = positionOf(player)

        val outcome = run(runtime, Harness.serverDispatcher(helper), player, "homes create base")

        helper.assertValueEqual(outcome.result, 1, "le résultat de /homes create")
        helper.assertValueEqual(outcome.messages, listOf(placed(HomesMessages.CREATED, "base", position)), "la réponse de /homes create")
        helper.assertValueEqual(outcome.messages.single().type, MessageType.SUCCESS, "un home créé est un succès")
        helper.assertValueEqual(homesOf(runtime, player), PlayerHomes(mapOf("base" to position)), "les homes du joueur en mémoire")
        val text = helper.level.server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID).resolve("homes.json").readText()
        helper.assertTrue(player.stringUUID in text && "\"base\"" in text, "le home est écrit dans le homes.json du monde")
        helper.succeed()
    }

    /**
     * La création refuse dans l'ordre de sa fiche : le nom invalide, la dimension interdite aux homes, la limite atteinte, puis le nom déjà pris.
     * Un accent n'arrive pas au mod : l'analyseur du jeu arrête la commande.
     */
    @GameTest
    fun createRefusesInTheOrderOfItsSheet(helper: GameTestHelper) {
        val lab = Lab.open("create-refuses-in-the-order-of-its-sheet", rules = mapOf(HomesRules to STRICT_RULES))
        val player = Harness.player(helper)

        helper.assertValueEqual(run(lab, player, "homes create ba.se").messages, listOf(CommonMessages.NAME_INVALID.with()), "le nom invalide, premier refus")
        val from = sendToTheNether(helper, player)
        helper.assertValueEqual(run(lab, player, "homes create base").messages, listOf(HomesMessages.FORBIDDEN_DIMENSION.with("minecraft:the_nether")), "la dimension interdite, deuxième refus")
        bringBack(player, from)
        helper.assertValueEqual(run(lab, player, "homes create base").result, 1, "le premier home, sous la limite")
        helper.assertValueEqual(run(lab, player, "homes create mine").messages, listOf(HomesMessages.LIMIT_REACHED_ONE.with(1L)), "la limite atteinte, troisième refus")
        helper.assertValueEqual(run(lab, player, "homes create base").messages, listOf(HomesMessages.LIMIT_REACHED_ONE.with(1L)), "à la limite, le nom déjà pris se dit limite atteinte")

        val server = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        run(server, dispatcher, player, "homes create base")
        val taken = run(server, dispatcher, player, "homes create base")
        helper.assertValueEqual(taken.messages, listOf(HomesMessages.ALREADY_EXISTS.with("base")), "le nom déjà pris, quatrième refus")
        helper.assertValueEqual(taken.result, 0, "le résultat d'un refus")
        helper.assertValueEqual(taken.messages.single().type, MessageType.REFUSAL, "un refus")

        val deliveries = Harness.record(server) {
            try {
                dispatcher.execute("homes create été", player.createCommandSourceStack())
                throw helper.assertionException("un nom accentué doit être arrêté par l'analyseur du jeu")
            } catch (_: CommandSyntaxException) {
            }
        }
        helper.assertValueEqual(deliveries, emptyList(), "le mod ne répond rien à un nom que l'analyseur arrête")
        helper.succeed()
    }

    // ── Supprimer (chapitre 3.3) ──

    /** `delete` refuse le home absent, et supprime sans confirmation en rappelant la dimension et les coordonnées du home supprimé. */
    @GameTest
    fun deleteAnswersWithThePositionAndRefusesAnAbsentHome(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)
        val position = positionOf(player)
        run(runtime, dispatcher, player, "homes create base")

        val absent = run(runtime, dispatcher, player, "homes delete mine")
        val deleted = run(runtime, dispatcher, player, "homes delete base")

        helper.assertValueEqual(absent.messages, listOf(HomesMessages.ABSENT.with("mine")), "le home absent")
        helper.assertValueEqual(absent.result, 0, "le résultat du refus")
        helper.assertValueEqual(deleted.messages, listOf(placed(HomesMessages.DELETED, "base", position)), "le home supprimé, avec sa position")
        helper.assertValueEqual(deleted.result, 1, "le résultat de la suppression")
        helper.assertTrue(runtime.worldData?.homes?.content?.get(player.uuid) == null, "le joueur sans home n'a plus d'entrée")
        helper.succeed()
    }

    // ── Lister (chapitre 3.4) ──

    /**
     * La liste donne son en-tête, puis les homes triés sans tenir compte de la casse, la majuscule d'abord à égalité, le home par défaut marqué,
     * chaque nom cliquable vers sa téléportation et survolable vers sa position. `/homes` seul affiche la même liste, et une liste vide le dit.
     */
    @GameTest
    fun listSortsMarksTheDefaultAndOffersClickAndHover(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)
        val position = positionOf(player)
        val max = runtime.rules(HomesRules).default[HomesRules.maxHomes]

        val empty = run(runtime, dispatcher, player, "homes list")
        listOf("base", "Base", "atelier").forEach { run(runtime, dispatcher, player, "homes create $it") }
        run(runtime, dispatcher, player, "homes default Base")
        val listed = run(runtime, dispatcher, player, "homes list")
        val alone = run(runtime, dispatcher, player, "homes")

        helper.assertValueEqual(empty.messages, listOf(HomesMessages.LIST_HEADER.with(0, max), HomesMessages.LIST_EMPTY.with()), "la liste vide")
        helper.assertValueEqual(empty.result, 0, "le résultat d'une liste vide")
        val hover = HomesMessages.LIST_POSITION.with(position.dimension, position.blockX, position.blockY, position.blockZ)
        val expected = listOf(
            HomesMessages.LIST_HEADER.with(3, max),
            HomesMessages.LIST_HOME.with("atelier").interactive(HomesListing.teleportCommand("atelier"), hover),
            HomesMessages.LIST_DEFAULT_HOME.with("Base").interactive(HomesListing.teleportCommand("Base"), hover),
            HomesMessages.LIST_HOME.with("base").interactive(HomesListing.teleportCommand("base"), hover),
        )
        helper.assertValueEqual(listed.messages, expected, "la liste")
        helper.assertValueEqual(listed.result, 3, "le résultat de la liste, le nombre de homes")
        helper.assertValueEqual(listed.messages.map { it.type }.toSet(), setOf(MessageType.DISPLAY), "la liste est un affichage")
        helper.assertValueEqual(alone.messages, expected, "/homes seul affiche la même liste")

        val recording = Harness.RecordingSource()
        dispatcher.execute("homes", player.createCommandSourceStack().withSource(recording))
        val line = recording.received[1].style
        val click = line.clickEvent as? ClickEvent.RunCommand ?: throw helper.assertionException("un home de la liste ne se clique pas")
        helper.assertValueEqual(click.command(), "/homes teleport atelier", "la commande que lance le clic")
        helper.assertTrue(line.hoverEvent is HoverEvent.ShowText, "un home de la liste ne se survole pas")
        helper.assertTrue(recording.received[0].style.clickEvent == null, "l'en-tête ne se clique pas")
        helper.succeed()
    }

    // ── Renommer (chapitre 3.5) ──

    /** Le renommage refuse dans l'ordre de sa fiche, change le nom et rien d'autre, et `<name>` se complète avec les homes du joueur, `<newName>` avec rien. */
    @GameTest
    fun renameRefusesInTheOrderOfItsSheetAndCompletesTheName(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)
        val position = positionOf(player)
        listOf("mine", "base").forEach { run(runtime, dispatcher, player, "homes create $it") }

        helper.assertValueEqual(run(runtime, dispatcher, player, "homes rename ferme ba.se").messages, listOf(CommonMessages.NAME_INVALID.with()), "le nouveau nom invalide, premier refus")
        helper.assertValueEqual(run(runtime, dispatcher, player, "homes rename ferme mine").messages, listOf(HomesMessages.ABSENT.with("ferme")), "le home absent, deuxième refus")
        helper.assertValueEqual(run(runtime, dispatcher, player, "homes rename base base").messages, listOf(HomesMessages.SAME_NAME.with("base")), "les noms identiques, troisième refus")
        helper.assertValueEqual(run(runtime, dispatcher, player, "homes rename base mine").messages, listOf(HomesMessages.ALREADY_EXISTS.with("mine")), "le nom déjà pris, quatrième refus")
        val renamed = run(runtime, dispatcher, player, "homes rename base Base")
        helper.assertValueEqual(renamed.messages, listOf(HomesMessages.RENAMED.with("base", "Base")), "le home renommé")
        helper.assertValueEqual(renamed.result, 1, "le résultat du renommage")
        helper.assertValueEqual(homesOf(runtime, player), PlayerHomes(mapOf("mine" to position, "Base" to position)), "le nom a changé, et rien d'autre")

        val source = player.createCommandSourceStack()
        val names = dispatcher.getCompletionSuggestions(dispatcher.parse("homes rename ", source)).join().list.map { it.text }
        val newNames = dispatcher.getCompletionSuggestions(dispatcher.parse("homes rename Base ", source)).join().list.map { it.text }
        val created = dispatcher.getCompletionSuggestions(dispatcher.parse("homes create ", source)).join().list.map { it.text }
        helper.assertValueEqual(names, listOf("Base", "mine"), "la complétion de <name>, dans l'ordre de la liste")
        helper.assertValueEqual(newNames, emptyList(), "<newName> ne propose rien")
        helper.assertValueEqual(created, emptyList(), "create ne propose rien")
        helper.succeed()
    }

    // ── Déplacer (chapitre 3.6) ──

    /** `move` refuse le home absent, puis la dimension interdite aux homes, et remplace la position, la dimension et l'orientation du home. */
    @GameTest
    fun moveRefusesThenReplacesThePosition(helper: GameTestHelper) {
        val lab = Lab.open("move-refuses-then-replaces-the-position", rules = mapOf(HomesRules to FORBIDDEN_NETHER))
        val player = Harness.player(helper)
        val position = positionOf(player)
        run(lab, player, "homes create base")

        helper.assertValueEqual(run(lab, player, "homes move mine").messages, listOf(HomesMessages.ABSENT.with("mine")), "le home absent, premier refus")
        val from = sendToTheNether(helper, player)
        helper.assertValueEqual(run(lab, player, "homes move base").messages, listOf(HomesMessages.FORBIDDEN_DIMENSION.with("minecraft:the_nether")), "la dimension interdite, deuxième refus")
        bringBack(player, Location(from.level, from.x + 10, from.y, from.z, 45f, 10f))
        val moved = run(lab, player, "homes move base")

        val target = positionOf(player)
        helper.assertValueEqual(moved.messages, listOf(placed(HomesMessages.MOVED, "base", target)), "le home déplacé")
        helper.assertValueEqual(moved.result, 1, "le résultat du déplacement")
        helper.assertValueEqual(homesOf(lab.runtime, player), PlayerHomes(mapOf("base" to target)), "la position, la dimension et l'orientation ont changé")
        helper.assertTrue(target != position, "le joueur a bougé avant de déplacer son home")
        helper.succeed()
    }

    // ── Désigner le home par défaut (chapitre 3.7) ──

    /**
     * Un joueur à un seul home l'a pour home par défaut ; à plusieurs, seul un home désigné l'est. `default` refuse le home absent, désigner le home
     * déjà par défaut réussit de nouveau, et la suppression du home désigné efface la désignation.
     */
    @GameTest
    fun defaultDesignatesAHome(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)
        fun marked(): List<String> = run(runtime, dispatcher, player, "homes list").messages.drop(1).map { it.key }

        run(runtime, dispatcher, player, "homes create base")
        helper.assertValueEqual(marked(), listOf(HomesMessages.LIST_DEFAULT_HOME.key), "le seul home est par défaut")
        run(runtime, dispatcher, player, "homes create mine")
        helper.assertValueEqual(marked(), listOf(HomesMessages.LIST_HOME.key, HomesMessages.LIST_HOME.key), "à deux homes, aucun n'est par défaut sans désignation")

        helper.assertValueEqual(run(runtime, dispatcher, player, "homes default ferme").messages, listOf(HomesMessages.ABSENT.with("ferme")), "le home absent")
        val designated = run(runtime, dispatcher, player, "homes default mine")
        helper.assertValueEqual(designated.messages, listOf(HomesMessages.DEFAULT_SET.with("mine")), "le home désigné")
        helper.assertValueEqual(designated.result, 1, "le résultat de la désignation")
        helper.assertValueEqual(marked(), listOf(HomesMessages.LIST_HOME.key, HomesMessages.LIST_DEFAULT_HOME.key), "mine est marqué par défaut")
        helper.assertValueEqual(run(runtime, dispatcher, player, "homes default mine").messages, listOf(HomesMessages.DEFAULT_SET.with("mine")), "désigner le home déjà par défaut réussit de nouveau")

        run(runtime, dispatcher, player, "homes create ferme")
        run(runtime, dispatcher, player, "homes delete mine")
        helper.assertValueEqual(marked(), listOf(HomesMessages.LIST_HOME.key, HomesMessages.LIST_HOME.key), "la suppression du home désigné efface la désignation")
        helper.succeed()
    }

    // ── La console, les données, l'activation, les nœuds ──

    /** Chaque commande du joueur exige un joueur : la console reçoit le refus de la source, et rien ne change. */
    @GameTest
    fun consoleIsRefusedByEveryPlayerCommand(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val console = Harness.console(helper)

        listOf("homes", "homes list", "homes create base", "homes delete base", "homes rename base mine", "homes move base", "homes default base").forEach { command ->
            var result = 1
            val keys = Harness.keysSentTo(null, runtime) { result = dispatcher.execute(command, console) }
            helper.assertValueEqual(keys, listOf(CommonMessages.IN_GAME_ONLY.key), "la réponse de /$command à la console")
            helper.assertValueEqual(result, 0, "le résultat de /$command à la console")
        }
        helper.succeed()
    }

    /**
     * Sur un fichier de données invalide, les homes sont indisponibles : chaque commande le dit en refus, rien ne s'écrit dans le fichier fautif,
     * et l'état le montre refusé. Le fichier corrigé puis rechargé remet la famille en service.
     */
    @GameTest
    fun homesAreUnavailableOnAnInvalidDataFileUntilItIsReloaded(helper: GameTestHelper) {
        val lab = Lab.open("homes-are-unavailable-on-an-invalid-data-file", data = mapOf("homes" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))
        val player = Harness.player(helper)
        val file = lab.directory.resolve("world").resolve("homes.json")
        val faulty = file.readBytes()
        val console = Harness.console(helper)

        listOf("homes", "homes create base", "homes delete base", "homes default base").forEach { command ->
            val outcome = run(lab, player, command)
            helper.assertValueEqual(outcome.messages, listOf(HomesMessages.UNAVAILABLE.with()), "la réponse de /$command sur des données invalides")
            helper.assertValueEqual(outcome.result, 0, "le résultat de /$command sur des données invalides")
        }
        helper.assertValueEqual(run(lab, player, "homes").messages.single().type, MessageType.REFUSAL, "les homes indisponibles sont un refus")
        helper.assertTrue(faulty.contentEquals(file.readBytes()), "le fichier fautif n'est pas réécrit")
        val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", console) }
        helper.assertValueEqual(status.last(), ModMessages.STATUS_FILE_REFUSED.key, "l'état montre world/homes.json refusé")

        lab.writeData("homes", """{ "players": {} }""")
        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", console) }.map { it.message }
        helper.assertValueEqual(reload.last(), ModMessages.RELOAD_FILE_APPLIED.with("world/homes.json"), "le fichier corrigé s'applique au rechargement")
        val created = run(lab, player, "homes create base")
        helper.assertValueEqual(created.messages, listOf(placed(HomesMessages.CREATED, "base", positionOf(player))), "la famille est remise en service")
        helper.assertTrue(player.stringUUID in file.readText(), "le home s'écrit dans le fichier remis en service")
        helper.succeed()
    }

    /** Une famille inactive au démarrage n'enregistre pas ses commandes ; active, elle les enregistre. */
    @GameTest
    fun disabledFamilyRegistersNoCommand(helper: GameTestHelper) {
        val disabled = Lab.open("disabled-homes-register-no-command", rules = mapOf(HomesRules to """{ "enabled": false }"""))
        val player = Harness.player(helper)

        helper.assertTrue(disabled.dispatcher.root.getChild("homes") == null, "/homes n'est pas enregistrée quand la famille est inactive")
        Harness.assertUnknownCommand(helper, disabled.dispatcher, player.createCommandSourceStack(), "homes create base")
        helper.assertTrue(Harness.serverDispatcher(helper).root.getChild("homes") != null, "/homes est enregistrée sur le serveur, où la famille est active")
        helper.succeed()
    }

    /**
     * Une commande des homes dont le joueur n'a pas le nœud n'existe pas pour lui : sans aucun nœud, `/homes` est une commande inconnue ; avec le
     * seul nœud de `create`, les autres sous-commandes sont refusées par le jeu comme un argument incorrect, et `/homes` seul, qui exige celui de
     * `list`, est une commande inconnue.
     */
    @GameTest
    fun homesCommandsFollowTheirNodes(helper: GameTestHelper) {
        val nobody = Harness.player(helper)
        val creator = Harness.player(helper)
        val lab = Lab.open("homes-commands-follow-their-nodes", permissionSource = { source, node ->
            when {
                node !in HomesNodes.PLAYER_COMMANDS -> null
                source.player === nobody -> false
                source.player === creator -> node == HomesNodes.CREATE
                else -> null
            }
        })

        helper.assertFalse(lab.dispatcher.root.getChild("homes").canUse(nobody.createCommandSourceStack()), "/homes n'existe pas pour le joueur privé de tous ses nœuds")
        Harness.assertUnknownCommand(helper, lab.dispatcher, nobody.createCommandSourceStack(), "homes")
        Harness.assertUnknownCommand(helper, lab.dispatcher, nobody.createCommandSourceStack(), "homes create base")
        helper.assertValueEqual(run(lab, creator, "homes create base").result, 1, "le joueur qui n'a que create crée")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, creator.createCommandSourceStack(), "homes list")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, creator.createCommandSourceStack(), "homes delete base")
        Harness.assertUnknownCommand(helper, lab.dispatcher, creator.createCommandSourceStack(), "homes")
        helper.assertFalse(lab.dispatcher.root.getChild("homes").getChild("list").canUse(creator.createCommandSourceStack()), "list n'existe pas pour le joueur qui n'a que create, ni à la complétion")
        helper.succeed()
    }
}
