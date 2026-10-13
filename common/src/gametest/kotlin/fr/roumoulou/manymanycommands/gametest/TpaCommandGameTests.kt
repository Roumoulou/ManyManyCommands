// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import com.mojang.brigadier.CommandDispatcher
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.commands.tpa.TpaCommand
import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.game.message.MessageComponents
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import fr.roumoulou.manymanycommands.rules.tpa.RequestKind
import fr.roumoulou.manymanycommands.rules.tpa.TpaHoldings
import fr.roumoulou.manymanycommands.rules.tpa.TpaMessages
import fr.roumoulou.manymanycommands.rules.tpa.TpaNodes
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.level.ServerPlayer
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Les six commandes des demandes de téléportation (`tpa.md` des specs, chapitres 2 à 5), jouées par des joueurs simulés nommés : chaque situation
 * du tableau des messages que ces commandes, l'expiration et la déconnexion produisent a son test, reconnue à sa clé, et les deux mots
 * cliquables se lisent dans le composant de la demande reçue. Le voyage qui suit l'acceptation s'éprouve dans `TpaTeleportGameTests`.
 */
class TpaCommandGameTests {

    private companion object {

        /** Deux demandes de suite au même joueur passent. */
        const val NO_INTERVAL = """{ "request-interval": 0 }"""

        /** Un seul usage par heure. */
        const val ONE_PER_HOUR = """{ "rules": { "default": { "quota": { "uses": 1, "period": 3600 } } } }"""

        /** La durée de vie livrée d'une demande. */
        const val LIFETIME = 60L
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

    /** La demande reçue d'un [requester], telle que la règle l'écrit, avec la durée de vie livrée. */
    private fun received(kind: RequestKind, requester: ServerPlayer): Message = TpaMessages.received(kind, requester.scoreboardName, LIFETIME)

    // ── Demander (chapitre 3.1) ──

    /**
     * `/tpa` confirme au demandeur, montre la demande au destinataire avec le temps pour répondre et ses deux mots cliquables, et enregistre qui
     * voyage. `<player>` se lit sans tenir compte de la casse.
     */
    @GameTest
    fun tpaSendsARequestWithItsTwoClickableWords(helper: GameTestHelper) {
        val lab = Lab.open("tpa-sends-a-request-with-its-two-clickable-words")
        val steve = Harness.player(helper, "SteveA")
        val alex = Harness.player(helper, "AlexA")

        val outcome = run(lab, steve, "tpa alexa")

        helper.assertValueEqual(outcome.result, 1, "le résultat de /tpa")
        helper.assertValueEqual(outcome.to(steve), listOf(TpaMessages.SENT.with("AlexA")), "la confirmation du demandeur, avec le nom du destinataire tel qu'il le porte")
        helper.assertValueEqual(outcome.to(steve).single().type, MessageType.SUCCESS, "la confirmation est un succès")
        helper.assertValueEqual(outcome.to(alex), listOf(received(RequestKind.TPA, steve)), "la demande reçue, par /tpa")
        helper.assertValueEqual(outcome.to(alex).single().type, MessageType.DISPLAY, "la demande reçue est un affichage")
        val words = MessageComponents(TextCatalog.load()).forPlayer(outcome.to(alex).single(), Language.FRENCH).siblings.filter { it.style.clickEvent != null }
        helper.assertValueEqual(words.map { (it.style.clickEvent as ClickEvent.RunCommand).command() }, listOf("/tpaaccept SteveA", "/tpadeny SteveA"), "les deux mots cliquables, et la commande de chacun")
        helper.assertValueEqual(words.map { (it.contents as TranslatableContents).key }, listOf(TpaMessages.ACCEPT_WORD.key, TpaMessages.DENY_WORD.key), "les libellés des deux mots")
        val request = lab.runtime.requests.outgoing(steve.uuid) ?: throw helper.assertionException("la demande n'est pas en attente")
        helper.assertValueEqual(request.traveler to request.host, steve.uuid to alex.uuid, "par /tpa, le demandeur voyage chez le destinataire")
        helper.assertValueEqual(request.lifetimeSeconds, LIFETIME, "la durée de vie livrée")
        helper.succeed()
    }

    /** `/tpahere` envoie la demande dans l'autre sens : le destinataire voyagera chez le demandeur. */
    @GameTest
    fun tpahereSendsARequestInTheOtherDirection(helper: GameTestHelper) {
        val lab = Lab.open("tpahere-sends-a-request-in-the-other-direction")
        val steve = Harness.player(helper, "SteveB")
        val alex = Harness.player(helper, "AlexB")

        val outcome = run(lab, steve, "tpahere AlexB")

        helper.assertValueEqual(outcome.to(steve), listOf(TpaMessages.SENT.with("AlexB")), "la confirmation du demandeur")
        helper.assertValueEqual(outcome.to(alex), listOf(received(RequestKind.TPAHERE, steve)), "la demande reçue, par /tpahere")
        val request = lab.runtime.requests.outgoing(steve.uuid) ?: throw helper.assertionException("la demande n'est pas en attente")
        helper.assertValueEqual(request.traveler to request.host, alex.uuid to steve.uuid, "par /tpahere, le destinataire voyage chez le demandeur")
        helper.succeed()
    }

    /**
     * L'envoi refuse dans l'ordre de la fiche : la demande à soi-même, le joueur non connecté, la réception fermée chez le destinataire, la demande
     * trop rapprochée, le quota atteint. Chaque refus prime sur les suivants, et le destinataire ne reçoit rien d'une demande refusée.
     */
    @GameTest
    fun sendRefusesInTheOrderOfItsSheet(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("send-refuses-in-the-order-of-its-sheet", rules = mapOf(TpaRules to ONE_PER_HOUR), clock = clock)
        val steve = Harness.player(helper, "SteveC")
        val alex = Harness.player(helper, "AlexC")
        helper.assertValueEqual(run(lab, steve, "tpa AlexC").result, 1, "la première demande part")
        run(lab, alex, "tpatoggle")
        clock.advance(10)

        helper.assertValueEqual(run(lab, steve, "tpa SteveC").to(steve), listOf(TpaMessages.SELF.with()), "la demande à soi-même, premier refus")
        helper.assertValueEqual(run(lab, steve, "tpa Herobrine").to(steve), listOf(TpaMessages.NOT_CONNECTED.with("Herobrine")), "le joueur non connecté, deuxième refus")
        helper.assertValueEqual(run(lab, steve, "tpa AlexC").to(steve), listOf(TpaMessages.CLOSED.with("AlexC")), "la réception fermée, troisième refus, avant l'intervalle")
        run(lab, alex, "tpatoggle")
        val tooSoon = run(lab, steve, "tpa AlexC")
        helper.assertValueEqual(tooSoon.to(steve), listOf(TpaMessages.TOO_SOON.with(DurationWording.text(50), "AlexC")), "la demande trop rapprochée, quatrième refus, avec l'attente")
        helper.assertValueEqual(tooSoon.result, 0, "le résultat d'un refus")
        helper.assertValueEqual(tooSoon.to(steve).single().type, MessageType.REFUSAL, "un refus")
        helper.assertValueEqual(tooSoon.to(alex), emptyList(), "le destinataire ne reçoit rien d'une demande refusée")

        val file = lab.runtime.worldData?.tpa ?: throw helper.assertionException("tpa.json n'est pas ouvert")
        val data = file.content ?: throw helper.assertionException("tpa.json est indisponible")
        file.update(data.withQuota(steve.uuid, "SteveC", QuotaUsage(QuotaClock.REAL, clock.instant().epochSecond, uses = 1), TpaHoldings()))
        clock.advance(50)
        helper.assertValueEqual(run(lab, steve, "tpa AlexC").to(steve), listOf(TpaMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(3_550))), "le quota atteint, cinquième refus, avec l'attente")
        helper.succeed()
    }

    // ── Accepter, refuser (chapitres 3.2 et 3.3) ──

    /** `/tpaaccept` consomme la demande du joueur nommé, ou sans nom la dernière reçue, et prévient les deux joueurs ; sans demande, chaque refus. */
    @GameTest
    fun acceptConsumesTheRequestAndTellsBothPlayers(helper: GameTestHelper) {
        val lab = Lab.open("accept-consumes-the-request-and-tells-both-players")
        val steve = Harness.player(helper, "SteveD")
        val alex = Harness.player(helper, "AlexD")
        val notch = Harness.player(helper, "NotchD")
        run(lab, steve, "tpa AlexD")
        run(lab, notch, "tpa AlexD")

        // L'acceptation lance le voyage, dont les messages suivent ceux de l'acceptation : le voyage s'éprouve dans TpaTeleportGameTests.
        val last = run(lab, alex, "tpaaccept")
        helper.assertValueEqual(last.to(alex).first(), TpaMessages.ACCEPTED_RECIPIENT.with("NotchD"), "sans nom, la dernière reçue, chez le destinataire")
        helper.assertValueEqual(last.to(notch).first(), TpaMessages.ACCEPTED_REQUESTER.with("AlexD"), "chez le demandeur")
        helper.assertValueEqual(last.to(alex).first().type to last.to(notch).first().type, MessageType.SUCCESS to MessageType.SUCCESS, "deux succès")
        helper.assertTrue(lab.runtime.requests.outgoing(notch.uuid) == null, "la demande acceptée est consommée")

        val named = run(lab, alex, "tpaaccept steved")
        helper.assertValueEqual(named.to(alex).first(), TpaMessages.ACCEPTED_RECIPIENT.with("SteveD"), "la demande du joueur nommé, sans égard pour la casse")
        helper.assertValueEqual(named.to(steve).first(), TpaMessages.ACCEPTED_REQUESTER.with("AlexD"), "chez le demandeur nommé")
        helper.assertValueEqual(run(lab, alex, "tpaaccept").to(alex), listOf(TpaMessages.NONE_PENDING.with()), "aucune demande en attente")
        helper.assertValueEqual(run(lab, alex, "tpaaccept SteveD").to(alex), listOf(TpaMessages.NONE_FROM.with("SteveD")), "aucune demande du joueur nommé")
        helper.assertValueEqual(run(lab, alex, "tpaaccept Herobrine").to(alex), listOf(TpaMessages.NONE_FROM.with("Herobrine")), "ni d'un nom que personne ne porte")
        helper.succeed()
    }

    /** `/tpadeny` consomme la demande du joueur nommé, ou sans nom la dernière reçue, et prévient les deux joueurs, en refus chez le demandeur. */
    @GameTest
    fun denyConsumesTheRequestAndTellsBothPlayers(helper: GameTestHelper) {
        val lab = Lab.open("deny-consumes-the-request-and-tells-both-players")
        val steve = Harness.player(helper, "SteveE")
        val alex = Harness.player(helper, "AlexE")
        val notch = Harness.player(helper, "NotchE")
        run(lab, steve, "tpa AlexE")
        run(lab, notch, "tpa AlexE")

        val named = run(lab, alex, "tpadeny SteveE")
        helper.assertValueEqual(named.result, 1, "le résultat de /tpadeny")
        helper.assertValueEqual(named.to(alex), listOf(TpaMessages.DENIED_RECIPIENT.with("SteveE")), "chez le destinataire")
        helper.assertValueEqual(named.to(alex).single().type, MessageType.SUCCESS, "un succès chez le destinataire")
        helper.assertValueEqual(named.to(steve), listOf(TpaMessages.DENIED_REQUESTER.with("AlexE")), "chez le demandeur")
        helper.assertValueEqual(named.to(steve).single().type, MessageType.REFUSAL, "un refus chez le demandeur")
        helper.assertValueEqual(run(lab, alex, "tpadeny").to(notch), listOf(TpaMessages.DENIED_REQUESTER.with("AlexE")), "sans nom, la dernière reçue")
        helper.assertValueEqual(run(lab, alex, "tpadeny").to(alex), listOf(TpaMessages.NONE_PENDING.with()), "plus rien en attente")
        helper.assertValueEqual(lab.runtime.requests.received(alex.uuid), emptyList(), "les demandes refusées sont consommées")
        helper.succeed()
    }

    // ── Annuler (chapitre 3.4) ──

    /** `/tpacancel` retire la demande envoyée et prévient les deux joueurs ; sans demande, un refus. Une nouvelle demande annule la précédente, et son destinataire l'apprend. */
    @GameTest
    fun cancelTellsBothPlayersAndANewRequestCancelsThePreviousOne(helper: GameTestHelper) {
        val lab = Lab.open("cancel-tells-both-players-and-a-new-request-cancels-the-previous-one", rules = mapOf(TpaRules to NO_INTERVAL))
        val steve = Harness.player(helper, "SteveF")
        val alex = Harness.player(helper, "AlexF")
        val notch = Harness.player(helper, "NotchF")
        run(lab, steve, "tpa AlexF")

        val cancelled = run(lab, steve, "tpacancel")
        helper.assertValueEqual(cancelled.result, 1, "le résultat de /tpacancel")
        helper.assertValueEqual(cancelled.to(steve), listOf(TpaMessages.CANCELLED_REQUESTER.with("AlexF")), "chez le demandeur")
        helper.assertValueEqual(cancelled.to(alex), listOf(TpaMessages.CANCELLED_RECIPIENT.with("SteveF")), "chez le destinataire")
        helper.assertValueEqual(cancelled.to(alex).single().type, MessageType.DISPLAY, "un affichage chez le destinataire")
        val nothing = run(lab, steve, "tpacancel")
        helper.assertValueEqual(nothing.to(steve), listOf(TpaMessages.NONE_TO_CANCEL.with()), "aucune demande à annuler")
        helper.assertValueEqual(nothing.result, 0, "le résultat du refus")

        run(lab, steve, "tpa AlexF")
        val replaced = run(lab, steve, "tpa NotchF")
        helper.assertValueEqual(replaced.to(alex), listOf(TpaMessages.CANCELLED_RECIPIENT.with("SteveF")), "la nouvelle demande annule la précédente, et son destinataire l'apprend")
        helper.assertValueEqual(replaced.to(notch), listOf(received(RequestKind.TPA, steve)), "le nouveau destinataire reçoit la demande")
        helper.assertValueEqual(replaced.to(steve), listOf(TpaMessages.SENT.with("NotchF")), "la confirmation du demandeur")
        helper.assertValueEqual(lab.runtime.requests.received(alex.uuid), emptyList(), "la demande annulée n'attend plus")
        helper.succeed()
    }

    // ── L'expiration et la déconnexion (chapitre 2) ──

    /** Une demande expire au terme de sa durée de vie : les deux joueurs l'apprennent, et elle n'attend plus. */
    @GameTest
    fun expiryTellsBothPlayers(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("expiry-tells-both-players", clock = clock)
        val steve = Harness.player(helper, "SteveG")
        val alex = Harness.player(helper, "AlexG")
        run(lab, steve, "tpa AlexG")

        clock.advance(59)
        val early = Harness.record(lab.runtime) { lab.runtime.tickRequests(helper.level.server) }
        clock.advance(1)
        val expired = Harness.record(lab.runtime) { lab.runtime.tickRequests(helper.level.server) }

        helper.assertValueEqual(early, emptyList(), "rien avant le terme de la durée de vie")
        helper.assertValueEqual(expired.filter { it.player === steve }.map { it.message }, listOf(TpaMessages.EXPIRED_REQUESTER.with("AlexG")), "chez le demandeur")
        helper.assertValueEqual(expired.filter { it.player === alex }.map { it.message }, listOf(TpaMessages.EXPIRED_RECIPIENT.with("SteveG")), "chez le destinataire")
        helper.assertValueEqual(expired.map { it.message.type }.toSet(), setOf(MessageType.DISPLAY), "des affichages")
        helper.assertTrue(lab.runtime.requests.outgoing(steve.uuid) == null, "la demande expirée n'attend plus")
        helper.assertValueEqual(run(lab, alex, "tpaaccept").to(alex), listOf(TpaMessages.NONE_PENDING.with()), "plus rien à accepter")
        helper.succeed()
    }

    /**
     * La déconnexion d'un joueur fait tomber ses demandes, envoyées comme reçues, au tick : l'autre joueur de chacune l'apprend, nommé tel qu'il
     * l'était. Sur l'état du serveur, le tick du serveur le constate sans que le test ne fasse rien tiquer.
     */
    @GameTest
    fun disconnectionDropsTheRequestsAndTellsTheOtherPlayer(helper: GameTestHelper) {
        val server = helper.level.server
        val lab = Lab.open("disconnection-drops-the-requests-and-tells-the-other-player")
        val steve = Harness.player(helper, "SteveH")
        val alex = Harness.player(helper, "AlexH")
        val notch = Harness.player(helper, "NotchH")
        run(lab, steve, "tpa AlexH")
        run(lab, alex, "tpa NotchH")
        val runtime = Harness.serverRuntime(helper)
        val steve2 = Harness.player(helper, "SteveH2")
        val alex2 = Harness.player(helper, "AlexH2")
        helper.assertValueEqual(run(runtime, Harness.serverDispatcher(helper), steve2, "tpa AlexH2").result, 1, "la demande part, sur l'état du serveur")
        val deliveries = mutableListOf<Delivery>()
        val observer = runtime.messenger.observe { player, message -> deliveries += Delivery(player, message) }

        alex.connection.onDisconnect(DisconnectionDetails(Component.literal("gametest")))
        alex2.connection.onDisconnect(DisconnectionDetails(Component.literal("gametest")))
        val dropped = Harness.record(lab.runtime) { lab.runtime.tickRequests(server) }

        helper.assertTrue(alex.hasDisconnected() && alex2.hasDisconnected(), "les destinataires sont déconnectés")
        helper.assertValueEqual(dropped.filter { it.player === steve }.map { it.message }, listOf(TpaMessages.DISCONNECTED.with("AlexH")), "le demandeur apprend que sa demande est tombée")
        helper.assertValueEqual(dropped.filter { it.player === notch }.map { it.message }, listOf(TpaMessages.DISCONNECTED.with("AlexH")), "le destinataire apprend que la demande reçue est tombée")
        helper.assertValueEqual(dropped.first { it.player === steve }.message.type, MessageType.DISPLAY, "un affichage")
        helper.assertValueEqual(lab.runtime.requests.pending, emptyList(), "plus rien n'attend")
        helper.runAfterDelay(5) {
            observer.close()
            helper.assertValueEqual(deliveries.filter { it.player === steve2 }.map { it.message }, listOf(TpaMessages.DISCONNECTED.with("AlexH2")), "sur l'état du serveur, le tick du serveur fait tomber la demande")
            helper.assertTrue(runtime.requests.outgoing(steve2.uuid) == null, "la demande n'attend plus sur l'état du serveur")
            helper.succeed()
        }
    }

    // ── Fermer la réception (chapitre 3.5) ──

    /** `/tpatoggle` ferme la réception, l'écrit tout de suite dans `tpa.json`, où elle survit à un redémarrage, puis la rouvre. */
    @GameTest
    fun toggleClosesAndReopensTheReceptionAndKeepsItAcrossARestart(helper: GameTestHelper) {
        val lab = Lab.open("toggle-closes-and-reopens-the-reception-and-keeps-it-across-a-restart")
        val steve = Harness.player(helper, "SteveI")
        val alex = Harness.player(helper, "AlexI")
        val file = lab.directory.resolve("world").resolve("tpa.json")

        val closed = run(lab, alex, "tpatoggle")
        helper.assertValueEqual(closed.result, 1, "le résultat de /tpatoggle")
        helper.assertValueEqual(closed.to(alex), listOf(TpaMessages.RECEPTION_CLOSED.with()), "la réception fermée")
        helper.assertValueEqual(closed.to(alex).single().type, MessageType.SUCCESS, "un succès")
        helper.assertTrue("\"requests-closed\": true" in file.readText() && alex.stringUUID in file.readText(), "la réception fermée est écrite dans tpa.json tout de suite")
        helper.assertValueEqual(run(lab, steve, "tpa AlexI").to(steve), listOf(TpaMessages.CLOSED.with("AlexI")), "toute demande au joueur est refusée")

        val restarted = Lab.reopen("toggle-closes-and-reopens-the-reception-and-keeps-it-across-a-restart")
        helper.assertValueEqual(run(restarted, steve, "tpa AlexI").to(steve), listOf(TpaMessages.CLOSED.with("AlexI")), "la réception fermée se conserve au redémarrage")
        helper.assertValueEqual(run(restarted, alex, "tpatoggle").to(alex), listOf(TpaMessages.RECEPTION_OPENED.with()), "la réception rouverte")
        helper.assertFalse("requests-closed" in file.readText(), "la réception ouverte ne s'écrit pas")
        helper.assertValueEqual(run(restarted, steve, "tpa AlexI").result, 1, "la demande passe, la réception rouverte")
        helper.succeed()
    }

    // ── La console, le fichier invalide, l'activation, les nœuds, la complétion ──

    /** Les six commandes exigent un joueur : la console reçoit le refus commun. */
    @GameTest
    fun consoleIsRefusedByEveryCommand(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val console = Harness.console(helper)

        listOf("tpa SteveX", "tpahere SteveX", "tpaaccept", "tpaaccept SteveX", "tpadeny", "tpadeny SteveX", "tpacancel", "tpatoggle").forEach { command ->
            var result = 1
            val keys = Harness.keysSentTo(null, runtime) { result = dispatcher.execute(command, console) }
            helper.assertValueEqual(keys, listOf(CommonMessages.IN_GAME_ONLY.key), "la réponse de /$command à la console")
            helper.assertValueEqual(result, 0, "le résultat de /$command à la console")
        }
        helper.succeed()
    }

    /** Sur un `tpa.json` invalide, les six commandes refusent, le fichier n'est jamais réécrit, l'état le montre refusé, et le rechargement du fichier corrigé remet la famille en service. */
    @GameTest
    fun commandsAreUnavailableOnAnInvalidDataFileUntilItIsReloaded(helper: GameTestHelper) {
        val lab = Lab.open("tpa-commands-are-unavailable-on-an-invalid-data-file", data = mapOf("tpa" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))
        val steve = Harness.player(helper, "SteveJ")
        Harness.player(helper, "AlexJ")
        val file = lab.directory.resolve("world").resolve("tpa.json")
        val faulty = file.readBytes()
        val console = Harness.console(helper)

        listOf("tpa AlexJ", "tpahere AlexJ", "tpaaccept", "tpadeny", "tpacancel", "tpatoggle").forEach { command ->
            val outcome = run(lab, steve, command)
            helper.assertValueEqual(outcome.to(steve), listOf(TpaMessages.UNAVAILABLE.with()), "la réponse de /$command sur des données invalides")
            helper.assertValueEqual(outcome.result, 0, "le résultat de /$command sur des données invalides")
        }
        helper.assertValueEqual(run(lab, steve, "tpatoggle").to(steve).single().type, MessageType.REFUSAL, "les demandes indisponibles sont un refus")
        helper.assertTrue(faulty.contentEquals(file.readBytes()), "le fichier fautif n'est pas réécrit")
        val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", console) }
        helper.assertValueEqual(status[2 + Harness.FILES.indexOf("world/tpa.json")], ModMessages.STATUS_FILE_REFUSED.key, "l'état montre world/tpa.json refusé")

        lab.writeData("tpa", """{ "players": {} }""")
        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", console) }.map { it.message }
        helper.assertValueEqual(reload[Harness.FILES.indexOf("world/tpa.json")], ModMessages.RELOAD_FILE_APPLIED.with("world/tpa.json"), "le fichier corrigé s'applique au rechargement")
        helper.assertValueEqual(run(lab, steve, "tpatoggle").to(steve), listOf(TpaMessages.RECEPTION_CLOSED.with()), "la famille est remise en service")
        helper.assertTrue(steve.stringUUID in file.readText(), "la réception s'écrit dans le fichier remis en service")
        helper.succeed()
    }

    /** `enabled` inactif au démarrage : aucune des six commandes n'est enregistrée. */
    @GameTest
    fun disabledFamilyRegistersNoCommand(helper: GameTestHelper) {
        val disabled = Lab.open("tpa-disabled-family-registers-no-command", rules = mapOf(TpaRules to """{ "enabled": false }"""))
        val player = Harness.player(helper)

        TpaCommand.ALL.forEach { helper.assertTrue(disabled.dispatcher.root.getChild(it) == null, "/$it n'est pas enregistrée quand la famille est inactive") }
        Harness.assertUnknownCommand(helper, disabled.dispatcher, player.createCommandSourceStack(), "tpacancel")
        TpaCommand.ALL.forEach { helper.assertTrue(Harness.serverDispatcher(helper).root.getChild(it) != null, "/$it est enregistrée sur le serveur, où la famille est active") }
        helper.succeed()
    }

    /** Sans son nœud, une commande n'existe pas pour le joueur : chacune des six a le sien. */
    @GameTest
    fun commandsFollowTheirNodes(helper: GameTestHelper) {
        val nobody = Harness.player(helper, "NobodyK")
        val sender = Harness.player(helper, "SenderK")
        val lab = Lab.open("tpa-commands-follow-their-nodes", permissionSource = { source, node ->
            when {
                node !in TpaNodes.ALL -> null
                source.player === nobody -> false
                source.player === sender -> node == TpaNodes.TPA
                else -> null
            }
        })

        TpaCommand.ALL.forEach { helper.assertFalse(lab.dispatcher.root.getChild(it).canUse(nobody.createCommandSourceStack()), "/$it n'existe pas pour le joueur privé de ses nœuds") }
        Harness.assertUnknownCommand(helper, lab.dispatcher, nobody.createCommandSourceStack(), "tpacancel")
        Harness.assertUnknownCommand(helper, lab.dispatcher, nobody.createCommandSourceStack(), "tpa SenderK")
        helper.assertValueEqual(run(lab, sender, "tpa NobodyK").result, 1, "le joueur qui n'a que tpa demande")
        Harness.assertUnknownCommand(helper, lab.dispatcher, sender.createCommandSourceStack(), "tpahere NobodyK")
        Harness.assertUnknownCommand(helper, lab.dispatcher, sender.createCommandSourceStack(), "tpacancel")
        helper.succeed()
    }

    /** `<player>` se complète avec les joueurs connectés pour `/tpa` et `/tpahere`, et avec les joueurs dont une demande attend pour `/tpaaccept` et `/tpadeny`. */
    @GameTest
    fun playerArgumentCompletesWithConnectedPlayersOrPendingRequesters(helper: GameTestHelper) {
        val lab = Lab.open("tpa-player-argument-completes-with-connected-players-or-pending-requesters")
        val steve = Harness.player(helper, "SteveL")
        val alex = Harness.player(helper, "AlexL")
        val notch = Harness.player(helper, "NotchL")
        run(lab, steve, "tpa AlexL")
        run(lab, notch, "tpa AlexL")
        val dispatcher = lab.dispatcher
        val source = alex.createCommandSourceStack()

        val connected = dispatcher.getCompletionSuggestions(dispatcher.parse("tpa ", source)).join().list.map { it.text }
        val connectedHere = dispatcher.getCompletionSuggestions(dispatcher.parse("tpahere ", source)).join().list.map { it.text }
        val requesters = dispatcher.getCompletionSuggestions(dispatcher.parse("tpaaccept ", source)).join().list.map { it.text }
        val toDeny = dispatcher.getCompletionSuggestions(dispatcher.parse("tpadeny ", source)).join().list.map { it.text }
        val forSteve = dispatcher.getCompletionSuggestions(dispatcher.parse("tpaaccept ", steve.createCommandSourceStack())).join().list.map { it.text }

        helper.assertTrue(listOf("SteveL", "AlexL", "NotchL").all { it in connected } && connected == connectedHere, "la complétion de <player> propose les joueurs connectés")
        helper.assertValueEqual(requesters.toSet(), setOf("SteveL", "NotchL"), "la complétion de /tpaaccept propose les joueurs dont une demande attend")
        helper.assertValueEqual(toDeny.toSet(), setOf("SteveL", "NotchL"), "celle de /tpadeny aussi")
        helper.assertValueEqual(forSteve, emptyList(), "rien pour qui n'a reçu aucune demande")
        helper.succeed()
    }
}
