// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.Situation
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.warps.Warp
import fr.roumoulou.manymanycommands.rules.warps.Warps
import fr.roumoulou.manymanycommands.rules.warps.WarpsListing
import fr.roumoulou.manymanycommands.rules.warps.WarpsMessages
import fr.roumoulou.manymanycommands.rules.warps.WarpsNodes
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.math.BigDecimal
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * `/warps` et ses sous-commandes sans téléportation (`warps.md` des specs, chapitres 2.2 et 3), jouées par un administrateur, un joueur simulé et
 * la console, chacune sur un état à elle : les warps sont ceux du serveur, et deux tests ne les partagent pas. Chaque situation du tableau des
 * messages que ces commandes produisent a son test, reconnue à sa clé, avec l'ordre des refus de chaque fiche, le placement et le journal.
 */
class WarpsCommandGameTests {

    /** Ce qu'une commande a rendu, et les messages que sa source a reçus : le joueur qu'elle désigne, ou la console. */
    private class Outcome(val result: Int, val messages: List<Message>)

    private fun run(lab: Lab, source: CommandSourceStack, command: String): Outcome {
        var result = 0
        val messages = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, source) }.filter { it.player === source.player }.map { it.message }
        return Outcome(result, messages)
    }

    private fun run(lab: Lab, player: ServerPlayer, command: String): Outcome = run(lab, player.createCommandSourceStack(), command)

    /** Un joueur simulé avec le niveau des maîtres du jeu : le profil administrateur par défaut. */
    private fun admin(helper: GameTestHelper): CommandSourceStack = Harness.asGameMaster(Harness.player(helper))

    /**
     * Un joueur simulé en mode créatif, posé dans la structure du test, et le sélecteur du jeu qui ne désigne que lui, pour `<targets>`. Un UUID
     * nu ne désigne pas un joueur, parce qu'il pourrait désigner toute entité, et les joueurs simulés portent tous le même nom.
     */
    private fun target(helper: GameTestHelper): Pair<ServerPlayer, String> {
        val player = Harness.player(helper).also { it.setGameMode(GameType.CREATIVE) }
        val spot = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.teleportTo(helper.level, spot.x, spot.y, spot.z, emptySet(), 0f, 0f, true)
        return player to "@a[x=${spot.x.plain()},y=${spot.y.plain()},z=${spot.z.plain()},distance=..0.5]"
    }

    /** Le nombre en écriture décimale, jamais scientifique, que les arguments du jeu ne lisent pas. */
    private fun Double.plain(): String = BigDecimal.valueOf(this).toPlainString()

    /** Le placement qu'une commande sans `<pos>` prend : la position et l'orientation de la source, dans sa dimension. */
    private fun placementOf(source: CommandSourceStack): Position = Location(source.level, source.position.x, source.position.y, source.position.z, source.rotation.y, source.rotation.x).toPosition()

    /** Le message qui nomme un warp et sa position, comme la règle l'écrit. */
    private fun placed(situation: Situation, name: String, position: Position): Message = situation.with(name, position.dimension, position.blockX, position.blockY, position.blockZ)

    private fun warpsOf(lab: Lab): Warps = lab.runtime.worldData?.warps?.content?.warps ?: Warps()

    private fun warp(helper: GameTestHelper, lab: Lab, name: String): Warp = warpsOf(lab)[name] ?: throw helper.assertionException("le warp $name n'existe pas")

    private fun completions(lab: Lab, source: CommandSourceStack, line: String): List<String> = lab.dispatcher.getCompletionSuggestions(lab.dispatcher.parse(line, source)).join().list.map { it.text }

    private fun file(lab: Lab) = lab.directory.resolve("world").resolve("warps.json")

    // ── Créer (chapitre 3.1) ──

    /** `create` fait un warp ouvert à tous à la position de l'administrateur, le dit en succès avec sa dimension et ses coordonnées entières, l'écrit tout de suite dans le `warps.json` du monde, et se journalise. */
    @GameTest
    fun createMakesAnOpenWarpAtTheAdministratorPositionWritesAndLogsIt(helper: GameTestHelper) {
        val lab = Lab.open("warps-create-makes-an-open-warp")
        val admin = admin(helper)
        val position = placementOf(admin)

        val outcome = run(lab, admin, "warps create spawn")

        helper.assertValueEqual(outcome.result, 1, "le résultat de /warps create")
        helper.assertValueEqual(outcome.messages, listOf(placed(WarpsMessages.CREATED, "spawn", position)), "la réponse de /warps create")
        helper.assertValueEqual(outcome.messages.single().type, MessageType.SUCCESS, "un warp créé est un succès")
        helper.assertValueEqual(warpsOf(lab), Warps(mapOf("spawn" to Warp(position))), "le warp en mémoire, ouvert à tous")
        val text = file(lab).readText()
        helper.assertTrue("\"warps\"" in text && "\"spawn\"" in text, "le warp est écrit dans le warps.json du monde")

        val recording = Harness.RecordingSource()
        lab.dispatcher.execute("warps create hub", admin.withSource(recording))
        helper.assertValueEqual(recording.journalRequests, 1, "la création se journalise")
        lab.dispatcher.execute("warps create hub", admin.withSource(recording))
        helper.assertValueEqual(recording.journalRequests, 1, "un refus ne se journalise pas")
        helper.assertValueEqual(recording.received.size, 2, "le succès et le refus sont reçus")
        helper.succeed()
    }

    /**
     * La création refuse dans l'ordre de sa fiche : le nom invalide, le nom déjà pris, puis le `<pos>` que le placement refuse, hors de la hauteur
     * de la dimension ou de la bordure du monde. Un accent n'arrive pas au mod : l'analyseur du jeu arrête la commande.
     */
    @GameTest
    fun createRefusesInTheOrderOfItsSheet(helper: GameTestHelper) {
        val lab = Lab.open("warps-create-refuses-in-the-order-of-its-sheet")
        val admin = admin(helper)

        helper.assertValueEqual(run(lab, admin, "warps create ba.se").messages, listOf(CommonMessages.NAME_INVALID.with()), "le nom invalide, premier refus")
        helper.assertValueEqual(run(lab, admin, "warps create base").result, 1, "le premier warp")
        val taken = run(lab, admin, "warps create base")
        helper.assertValueEqual(taken.messages, listOf(WarpsMessages.ALREADY_EXISTS.with("base")), "le nom déjà pris, deuxième refus")
        helper.assertValueEqual(taken.result, 0, "le résultat d'un refus")
        helper.assertValueEqual(taken.messages.single().type, MessageType.REFUSAL, "un refus")
        helper.assertValueEqual(run(lab, admin, "warps create sky ~ 10000 ~").messages, listOf(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with()), "le point hors de la hauteur de la dimension, troisième refus")
        helper.assertValueEqual(run(lab, admin, "warps create far 40000000 64 0").messages, listOf(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with()), "le point hors de la bordure du monde, troisième refus")
        helper.assertValueEqual(run(lab, admin, "warps create base ~ 10000 ~").messages, listOf(WarpsMessages.ALREADY_EXISTS.with("base")), "le nom pris passe avant le placement")
        helper.assertValueEqual(run(lab, admin, "warps create ba.se ~ 10000 ~").messages, listOf(CommonMessages.NAME_INVALID.with()), "le nom invalide passe avant tout")
        helper.assertValueEqual(warpsOf(lab).sortedNames(), listOf("base"), "aucun refus n'a créé de warp")

        val deliveries = Harness.record(lab.runtime) {
            try {
                lab.dispatcher.execute("warps create été", admin)
                throw helper.assertionException("un nom accentué doit être arrêté par l'analyseur du jeu")
            } catch (_: CommandSyntaxException) {
            }
        }
        helper.assertValueEqual(deliveries, emptyList(), "le mod ne répond rien à un nom que l'analyseur arrête")
        helper.succeed()
    }

    /**
     * Le placement (cahier des charges, chapitre 2.9) : `<pos>`, `<rotation>` et `<dimension>` sont les arguments du jeu, un `x` ou un `z` entier
     * vise le centre du bloc, `~` désigne la position de la source, le point d'apparition du monde depuis la console, et ce qui manque vient de la
     * source : son orientation, ou `0 0` depuis la console, et sa dimension.
     */
    @GameTest
    fun placementGivesThePositionTheRotationAndTheDimension(helper: GameTestHelper) {
        val lab = Lab.open("warps-placement-gives-the-position-the-rotation-and-the-dimension")
        val console = Harness.console(helper)
        val player = Harness.player(helper)
        val here = Location.of(player)
        player.teleportTo(here.level, here.x, here.y, here.z, emptySet(), 45f, 10f, true)
        // La source d'une commande fige la position et l'orientation du joueur à sa création : elle se prend après le déplacement.
        val admin = Harness.asGameMaster(player)

        val complete = run(lab, console, "warps create nether 10 70 -3 90 10 minecraft:the_nether")
        helper.assertValueEqual(complete.messages, listOf(WarpsMessages.CREATED.with("nether", "minecraft:the_nether", 10, 70, -3)), "le placement complet, depuis la console")
        helper.assertValueEqual(warp(helper, lab, "nether"), Warp(Position("minecraft:the_nether", 10.5, 70.0, -2.5, 90f, 10f)), "un x ou un z entier vise le centre du bloc, un y entier reste au niveau du bloc")
        run(lab, console, "warps create spawn ~ ~ ~")
        helper.assertValueEqual(warp(helper, lab, "spawn"), Warp(placementOf(console)), "depuis la console, ~ désigne le point d'apparition du monde, et l'orientation manquante vaut 0 0")
        run(lab, admin, "warps create here ~ ~ ~")
        helper.assertValueEqual(warp(helper, lab, "here"), Warp(Position(here.dimension, here.x, here.y, here.z, 45f, 10f)), "~ désigne la position exacte du joueur, et l'orientation manquante est la sienne")
        run(lab, admin, "warps create up ~ ~2 ~ 0 0")
        helper.assertValueEqual(warp(helper, lab, "up"), Warp(Position(here.dimension, here.x, here.y + 2, here.z, 0f, 0f)), "un décalage relatif, et une orientation donnée")
        run(lab, admin, "warps create there 5 70 5")
        helper.assertValueEqual(warp(helper, lab, "there"), Warp(Position(here.dimension, 5.5, 70.0, 5.5, 45f, 10f)), "la dimension manquante est celle de la source")
        helper.succeed()
    }

    // ── Supprimer (chapitre 3.2) ──

    /** `delete` refuse le warp absent, et supprime sans confirmation en rappelant la dimension et les coordonnées du warp supprimé, effacé du fichier. */
    @GameTest
    fun deleteAnswersWithThePositionAndRefusesAnAbsentWarp(helper: GameTestHelper) {
        val lab = Lab.open("warps-delete-answers-with-the-position")
        val admin = admin(helper)
        val position = placementOf(admin)
        run(lab, admin, "warps create base")

        val absent = run(lab, admin, "warps delete mine")
        val deleted = run(lab, admin, "warps delete base")

        helper.assertValueEqual(absent.messages, listOf(WarpsMessages.ABSENT.with("mine")), "le warp absent")
        helper.assertValueEqual(absent.result, 0, "le résultat du refus")
        helper.assertValueEqual(deleted.messages, listOf(placed(WarpsMessages.DELETED, "base", position)), "le warp supprimé, avec sa position")
        helper.assertValueEqual(deleted.result, 1, "le résultat de la suppression")
        helper.assertValueEqual(warpsOf(lab), Warps(), "plus aucun warp")
        helper.assertFalse("\"base\"" in file(lab).readText(), "le warp supprimé a quitté le fichier")
        helper.succeed()
    }

    // ── Lister (chapitre 2.2) ──

    /**
     * La liste donne son en-tête avec le nombre de warps affichés, puis les warps triés sans tenir compte de la casse, chaque nom cliquable vers
     * sa téléportation et survolable vers sa position. Un warp réservé est absent de la liste de qui n'a pas sa clé, porte sa clé dans celle de
     * qui a le nœud de `restrict`, et s'affiche comme un autre pour qui n'a que l'accès. `/warps` seul affiche la même liste, une liste vide le
     * dit, et la console lit la position en clair.
     */
    @GameTest
    fun listSortsHidesReservedWarpsAndOffersClickAndHover(helper: GameTestHelper) {
        val holder = Harness.player(helper)
        val lab = Lab.open("warps-list-sorts-hides-reserved-warps-and-offers-click-and-hover", permissionSource = { source, node -> if (source.player === holder && node == WarpsNodes.access("vip")) true else null })
        val admin = admin(helper)
        val player = Harness.player(helper)
        val position = placementOf(admin)
        val hover = WarpsMessages.LIST_POSITION.with(position.dimension, position.blockX, position.blockY, position.blockZ)
        fun line(name: String): Message = WarpsMessages.LIST_WARP.with(name).interactive(WarpsListing.teleportCommand(name), hover)

        val empty = run(lab, player, "warps list")
        listOf("base", "Base", "atelier", "vip").forEach { run(lab, admin, "warps create $it") }
        run(lab, admin, "warps restrict vip vip")
        val listed = run(lab, player, "warps list")
        val alone = run(lab, player, "warps")
        val adminList = run(lab, admin, "warps list")
        val holderList = run(lab, holder, "warps list")

        helper.assertValueEqual(empty.messages, listOf(WarpsMessages.LIST_HEADER.with(0), WarpsMessages.LIST_EMPTY.with()), "la liste vide")
        helper.assertValueEqual(empty.result, 0, "le résultat d'une liste vide")
        val visible = listOf(WarpsMessages.LIST_HEADER.with(3), line("atelier"), line("Base"), line("base"))
        helper.assertValueEqual(listed.messages, visible, "la liste d'un joueur sans la clé du warp réservé")
        helper.assertValueEqual(listed.result, 3, "le résultat de la liste, le nombre de warps affichés")
        helper.assertValueEqual(listed.messages.map { it.type }.toSet(), setOf(MessageType.DISPLAY), "la liste est un affichage")
        helper.assertValueEqual(alone.messages, visible, "/warps seul affiche la même liste")
        val reserved = WarpsMessages.LIST_RESERVED.with("vip", "vip").interactive(WarpsListing.teleportCommand("vip"), hover)
        helper.assertValueEqual(adminList.messages, listOf(WarpsMessages.LIST_HEADER.with(4), line("atelier"), line("Base"), line("base"), reserved), "la liste de l'administrateur, qui a le nœud de restrict : le warp réservé porte sa clé")
        helper.assertValueEqual(holderList.messages, listOf(WarpsMessages.LIST_HEADER.with(4), line("atelier"), line("Base"), line("base"), line("vip")), "la liste de qui a la clé sans le nœud de restrict")

        val recording = Harness.RecordingSource()
        lab.dispatcher.execute("warps", player.createCommandSourceStack().withSource(recording))
        val first = recording.received[1].style
        val click = first.clickEvent as? ClickEvent.RunCommand ?: throw helper.assertionException("un warp de la liste ne se clique pas")
        helper.assertValueEqual(click.command(), "/warps teleport atelier", "la commande que lance le clic")
        helper.assertTrue(first.hoverEvent is HoverEvent.ShowText, "un warp de la liste ne se survole pas")
        helper.assertTrue(recording.received[0].style.clickEvent == null, "l'en-tête ne se clique pas")
        val console = Harness.RecordingSource()
        lab.dispatcher.execute("warps list", Harness.console(helper).withSource(console))
        helper.assertTrue(position.dimension in console.received[1].string, "la console lit la dimension en clair à la suite du nom")
        helper.succeed()
    }

    // ── Renommer (chapitre 3.3) ──

    /** Le renommage refuse dans l'ordre de sa fiche, change le nom et rien d'autre, la clé comprise, et `<name>` se complète avec tous les warps, `<newName>` avec rien. */
    @GameTest
    fun renameRefusesInTheOrderOfItsSheetAndCompletesTheName(helper: GameTestHelper) {
        val lab = Lab.open("warps-rename-refuses-in-the-order-of-its-sheet")
        val admin = admin(helper)
        val position = placementOf(admin)
        listOf("mine", "base").forEach { run(lab, admin, "warps create $it") }
        run(lab, admin, "warps restrict base vip")

        helper.assertValueEqual(run(lab, admin, "warps rename ferme ba.se").messages, listOf(CommonMessages.NAME_INVALID.with()), "le nouveau nom invalide, premier refus")
        helper.assertValueEqual(run(lab, admin, "warps rename ferme mine").messages, listOf(WarpsMessages.ABSENT.with("ferme")), "le warp absent, deuxième refus")
        helper.assertValueEqual(run(lab, admin, "warps rename base base").messages, listOf(WarpsMessages.SAME_NAME.with("base")), "les noms identiques, troisième refus")
        helper.assertValueEqual(run(lab, admin, "warps rename base mine").messages, listOf(WarpsMessages.ALREADY_EXISTS.with("mine")), "le nom déjà pris, quatrième refus")
        val renamed = run(lab, admin, "warps rename base Base")
        helper.assertValueEqual(renamed.messages, listOf(WarpsMessages.RENAMED.with("base", "Base")), "le warp renommé, par un changement de casse")
        helper.assertValueEqual(renamed.result, 1, "le résultat du renommage")
        helper.assertValueEqual(warpsOf(lab), Warps(mapOf("mine" to Warp(position), "Base" to Warp(position, "vip"))), "le nom a changé, et rien d'autre")

        helper.assertValueEqual(completions(lab, admin, "warps rename "), listOf("Base", "mine"), "la complétion de <name>, tous les warps, dans l'ordre de la liste")
        helper.assertValueEqual(completions(lab, admin, "warps rename Base "), emptyList(), "<newName> ne propose rien")
        helper.assertValueEqual(completions(lab, admin, "warps create "), emptyList(), "create ne propose rien")
        helper.succeed()
    }

    // ── Déplacer (chapitre 3.4) ──

    /** `move` refuse le warp absent, puis le placement, et remplace la position, la dimension et l'orientation du warp, dont la clé reste. */
    @GameTest
    fun moveRefusesThenReplacesThePosition(helper: GameTestHelper) {
        val lab = Lab.open("warps-move-refuses-then-replaces-the-position")
        val admin = admin(helper)
        run(lab, admin, "warps create base")
        run(lab, admin, "warps restrict base vip")

        helper.assertValueEqual(run(lab, admin, "warps move mine").messages, listOf(WarpsMessages.ABSENT.with("mine")), "le warp absent, premier refus")
        helper.assertValueEqual(run(lab, admin, "warps move base ~ 10000 ~").messages, listOf(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with()), "le placement refusé, deuxième refus")
        val moved = run(lab, admin, "warps move base 20 70 20 90 0 minecraft:the_nether")

        val target = Position("minecraft:the_nether", 20.5, 70.0, 20.5, 90f, 0f)
        helper.assertValueEqual(moved.messages, listOf(placed(WarpsMessages.MOVED, "base", target)), "le warp déplacé")
        helper.assertValueEqual(moved.result, 1, "le résultat du déplacement")
        helper.assertValueEqual(warp(helper, lab, "base"), Warp(target, "vip"), "la position, la dimension et l'orientation ont changé, la clé reste")
        helper.succeed()
    }

    // ── Réserver (chapitre 3.5) ──

    /**
     * `restrict` refuse la clé invalide avant le warp absent, réserve le warp, une nouvelle clé remplace l'ancienne, et `<key>` se complète avec
     * les clés déjà portées. `unrestrict` refuse le warp absent, rouvre le warp, et rouvrir un warp ouvert réussit de nouveau. La complétion de
     * `teleport` suit l'accès du joueur, celle des commandes de l'administrateur donne tous les warps.
     */
    @GameTest
    fun restrictReservesAndUnrestrictReopens(helper: GameTestHelper) {
        val lab = Lab.open("warps-restrict-reserves-and-unrestrict-reopens")
        val admin = admin(helper)
        val player = Harness.player(helper)
        listOf("base", "hub").forEach { run(lab, admin, "warps create $it") }

        helper.assertValueEqual(run(lab, admin, "warps restrict absent Vip").messages, listOf(WarpsMessages.KEY_INVALID.with()), "la clé invalide, premier refus")
        helper.assertValueEqual(run(lab, admin, "warps restrict absent vip").messages, listOf(WarpsMessages.ABSENT.with("absent")), "le warp absent, deuxième refus")
        val restricted = run(lab, admin, "warps restrict base vip")
        helper.assertValueEqual(restricted.messages, listOf(WarpsMessages.RESTRICTED.with("base", "vip")), "le warp réservé")
        helper.assertValueEqual(restricted.result, 1, "le résultat de la réservation")
        helper.assertValueEqual(warp(helper, lab, "base").key ?: throw helper.assertionException("le warp n'a pas de clé"), "vip", "la clé du warp")
        helper.assertValueEqual(run(lab, admin, "warps restrict base staff").messages, listOf(WarpsMessages.RESTRICTED.with("base", "staff")), "une nouvelle clé remplace l'ancienne")
        run(lab, admin, "warps restrict hub vip")
        helper.assertValueEqual(completions(lab, admin, "warps restrict base "), listOf("staff", "vip"), "la complétion de <key>, les clés déjà portées")
        helper.assertValueEqual(completions(lab, player.createCommandSourceStack(), "warps teleport "), emptyList(), "un joueur sans clé ne voit aucun warp réservé à la complétion")
        helper.assertValueEqual(completions(lab, admin, "warps teleport "), listOf("base", "hub"), "l'administrateur a toutes les clés")
        helper.assertValueEqual(completions(lab, admin, "warps delete "), listOf("base", "hub"), "les commandes de l'administrateur complètent avec tous les warps")

        helper.assertValueEqual(run(lab, admin, "warps unrestrict absent").messages, listOf(WarpsMessages.ABSENT.with("absent")), "le warp absent")
        val reopened = run(lab, admin, "warps unrestrict base")
        helper.assertValueEqual(reopened.messages, listOf(WarpsMessages.UNRESTRICTED.with("base")), "le warp rouvert")
        helper.assertValueEqual(reopened.result, 1, "le résultat de la réouverture")
        helper.assertTrue(warp(helper, lab, "base").key == null, "le warp n'a plus de clé")
        helper.assertValueEqual(run(lab, admin, "warps unrestrict base").messages, listOf(WarpsMessages.UNRESTRICTED.with("base")), "rouvrir un warp ouvert réussit de nouveau")
        helper.assertValueEqual(completions(lab, player.createCommandSourceStack(), "warps teleport "), listOf("base"), "le warp rouvert revient à la complétion du joueur")
        helper.succeed()
    }

    // ── La console (chapitre 3.7) ──

    /** Depuis la console, chaque commande s'exécute, sauf `create` et `move` sans `<pos>` et `teleport` sans `<targets>`, qui ont besoin d'une position et répondent par le refus de la source. */
    @GameTest
    fun consoleRunsEveryCommandGivenAPlacementOrTargets(helper: GameTestHelper) {
        val lab = Lab.open("warps-console-runs-every-command-given-a-placement-or-targets")
        val console = Harness.console(helper)
        val (_, targets) = target(helper)
        val moved = Position("minecraft:overworld", 1.5, 70.0, 1.5, 0f, 0f)

        listOf("warps create base", "warps teleport base").forEach { command ->
            val outcome = run(lab, console, command)
            helper.assertValueEqual(outcome.messages, listOf(CommonMessages.IN_GAME_ONLY.with()), "la réponse de /$command à la console")
            helper.assertValueEqual(outcome.result, 0, "le résultat de /$command à la console")
        }
        helper.assertValueEqual(run(lab, console, "warps create base 0 70 0").result, 1, "create avec un <pos> se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps move base").messages, listOf(CommonMessages.IN_GAME_ONLY.with()), "move sans <pos> a besoin d'une position")
        helper.assertValueEqual(run(lab, console, "warps move base 1 70 1").messages, listOf(placed(WarpsMessages.MOVED, "base", moved)), "move avec un <pos> se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps teleport base $targets").messages, listOf(WarpsMessages.SENT_ONE.with(1, "base")), "teleport avec des <targets> se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps list").result, 1, "list se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps rename base hub").messages, listOf(WarpsMessages.RENAMED.with("base", "hub")), "rename se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps restrict hub vip").messages, listOf(WarpsMessages.RESTRICTED.with("hub", "vip")), "restrict se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps unrestrict hub").messages, listOf(WarpsMessages.UNRESTRICTED.with("hub")), "unrestrict se lance depuis la console")
        helper.assertValueEqual(run(lab, console, "warps delete hub").messages, listOf(placed(WarpsMessages.DELETED, "hub", moved)), "delete se lance depuis la console")
        helper.succeed()
    }

    // ── Les données, l'activation, les nœuds ──

    /**
     * Sur un fichier de données invalide, les warps sont indisponibles : chaque commande le dit en refus, rien ne s'écrit dans le fichier fautif,
     * et l'état le montre refusé. Le fichier corrigé puis rechargé remet la famille en service.
     */
    @GameTest
    fun warpsAreUnavailableOnAnInvalidDataFileUntilItIsReloaded(helper: GameTestHelper) {
        val lab = Lab.open("warps-are-unavailable-on-an-invalid-data-file", data = mapOf("warps" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))
        val admin = admin(helper)
        val (player, targets) = target(helper)
        val faulty = file(lab).readBytes()
        val console = Harness.console(helper)

        listOf("warps", "warps create base", "warps delete base", "warps restrict base vip", "warps teleport base $targets").forEach { command ->
            val outcome = run(lab, admin, command)
            helper.assertValueEqual(outcome.messages, listOf(WarpsMessages.UNAVAILABLE.with()), "la réponse de /$command sur des données invalides")
            helper.assertValueEqual(outcome.result, 0, "le résultat de /$command sur des données invalides")
        }
        val teleport = run(lab, player, "warps teleport base")
        helper.assertValueEqual(teleport.messages, listOf(WarpsMessages.UNAVAILABLE.with()), "la réponse de /warps teleport sur des données invalides")
        helper.assertValueEqual(teleport.messages.single().type, MessageType.REFUSAL, "les warps indisponibles sont un refus")
        helper.assertTrue(faulty.contentEquals(file(lab).readBytes()), "le fichier fautif n'est pas réécrit")
        val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", console) }
        helper.assertValueEqual(status[2 + Harness.FILES.indexOf("world/warps.json")], ModMessages.STATUS_FILE_REFUSED.key, "l'état montre world/warps.json refusé")

        lab.writeData("warps", """{ "warps": {} }""")
        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", console) }.map { it.message }
        helper.assertValueEqual(reload[Harness.FILES.indexOf("world/warps.json")], ModMessages.RELOAD_FILE_APPLIED.with("world/warps.json"), "le fichier corrigé s'applique au rechargement")
        val created = run(lab, admin, "warps create base")
        helper.assertValueEqual(created.messages, listOf(placed(WarpsMessages.CREATED, "base", placementOf(admin))), "la famille est remise en service")
        helper.assertTrue("\"base\"" in file(lab).readText(), "le warp s'écrit dans le fichier remis en service")
        helper.succeed()
    }

    /** Une famille inactive au démarrage n'enregistre ni ses commandes ni ses alias ; active, elle les enregistre. */
    @GameTest
    fun disabledFamilyRegistersNoCommand(helper: GameTestHelper) {
        val disabled = Lab.open("disabled-warps-register-no-command", rules = mapOf(WarpsRules to """{ "enabled": false }"""))

        listOf("warps", "warp", "setwarp", "delwarp").forEach { helper.assertTrue(disabled.dispatcher.root.getChild(it) == null, "/$it n'est pas enregistré quand la famille est inactive") }
        Harness.assertUnknownCommand(helper, disabled.dispatcher, Harness.console(helper), "warps list")
        helper.assertTrue(Harness.serverDispatcher(helper).root.getChild("warps") != null, "/warps est enregistrée sur le serveur, où la famille est active")
        helper.succeed()
    }

    /**
     * Les commandes suivent leurs nœuds : un joueur a `teleport` et `list`, et les commandes de l'administrateur sont pour lui un argument
     * incorrect, absentes de la complétion. Un maître du jeu privé du nœud de `teleport` garde celui de l'envoi : il envoie, mais ne rejoint pas
     * lui-même. Privé de tous ses nœuds, un joueur ne voit plus `/warps`.
     */
    @GameTest
    fun warpsCommandsFollowTheirNodes(helper: GameTestHelper) {
        val (player, targets) = target(helper)
        val sender = Harness.player(helper)
        val lab = Lab.open("warps-commands-follow-their-nodes", permissionSource = { source, node -> if (source.player === sender && node == WarpsNodes.TELEPORT) false else null })
        val admin = admin(helper)
        helper.assertValueEqual(run(lab, admin, "warps create base").result, 1, "l'administrateur crée")

        val root = lab.dispatcher.root.getChild("warps")
        helper.assertTrue(root.canUse(player.createCommandSourceStack()), "/warps existe pour tout joueur, par teleport et list")
        helper.assertFalse(root.getChild("create").canUse(player.createCommandSourceStack()), "create n'existe pas pour un joueur, ni à la complétion")
        helper.assertFalse(root.getChild("teleport").getChild("name").getChild("targets").canUse(player.createCommandSourceStack()), "l'envoi n'existe pas pour un joueur")
        listOf("warps create hub", "warps delete base", "warps rename base hub", "warps move base", "warps restrict base vip", "warps unrestrict base").forEach {
            Harness.assertUnknownSubcommand(helper, lab.dispatcher, player.createCommandSourceStack(), it)
        }
        helper.assertValueEqual(run(lab, player, "warps list").result, 1, "le joueur liste")

        // Le jeu réserve les sélecteurs aux maîtres du jeu : l'envoyeur en est un, à qui la source des droits retire le seul nœud de teleport.
        val sending = Harness.asGameMaster(sender)
        Harness.assertUnknownCommand(helper, lab.dispatcher, sending, "warps teleport base")
        helper.assertValueEqual(run(lab, sending, "warps teleport base $targets").messages, listOf(WarpsMessages.SENT_ONE.with(1, "base")), "privé de teleport, le maître du jeu envoie, sans rejoindre lui-même")

        val nobody = Harness.player(helper)
        val hidden = Lab.open("warps-commands-follow-their-nodes-hidden", permissionSource = { source, _ -> if (source.player === nobody) false else null })
        helper.assertFalse(hidden.dispatcher.root.getChild("warps").canUse(nobody.createCommandSourceStack()), "/warps n'existe pas pour le joueur privé de tous ses nœuds")
        Harness.assertUnknownCommand(helper, hidden.dispatcher, nobody.createCommandSourceStack(), "warps")
        helper.succeed()
    }
}
