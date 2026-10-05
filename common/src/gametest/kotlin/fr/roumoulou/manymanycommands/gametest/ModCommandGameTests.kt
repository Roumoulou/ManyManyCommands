// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.permissions.LevelBasedPermissionSet
import java.util.Optional
import kotlin.io.path.exists

/**
 * `/manymanycommands`, `reload` et l'alias `/mmc` (`manymanycommands.md` des specs), joués par un joueur simulé et par la console : chaque
 * situation du tableau des messages a son test, reconnue à sa clé.
 */
class ModCommandGameTests {

    private companion object {
        val STATUS_KEYS = listOf(ModMessages.STATUS_VERSION.key, ModMessages.STATUS_SOURCE.key, ModMessages.STATUS_FILE_VALID.key)
        val APPLIED_KEYS = listOf(ModMessages.RELOAD_FILE_APPLIED.key)
        val REFUSED_KEYS = listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key)
    }

    // ── Sur le serveur lui-même : ses commandes, son état, son premier lancement ──

    /** Le premier lancement du mod écrit `settings.json` dans le dossier des configurations du serveur, et le serveur démarre sur les réglages livrés. */
    @GameTest
    fun firstLaunchWritesTheSettingsFile(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val settingsFile = FabricLoader.getInstance().configDir.resolve(MOD_ID).resolve(GlobalSettings.FILE_NAME)

        helper.assertTrue(settingsFile.exists(), "le premier lancement n'a pas écrit $settingsFile")
        helper.assertValueEqual(runtime.startupSettings, GlobalSettings(), "les réglages du démarrage sont les réglages livrés")
        helper.assertFalse(runtime.hasShippedSettingsInPlace, "le fichier créé par le mod est valide")
        helper.succeed()
    }

    /** L'état répond à un joueur qui a le niveau des maîtres du jeu : la version, la source des droits, puis chaque fichier, en affichage. */
    @GameTest
    fun statusAnswersAGameMaster(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val player = Harness.player(helper)

        val deliveries = Harness.record(runtime) {
            helper.assertValueEqual(Harness.serverDispatcher(helper).execute("manymanycommands", Harness.asGameMaster(player)), 1, "le résultat de /manymanycommands")
        }.filter { it.player === player }

        helper.assertValueEqual(deliveries.map { it.message.key }, STATUS_KEYS, "les lignes de l'état")
        helper.assertValueEqual(deliveries.map { it.message.type }.toSet(), setOf(MessageType.DISPLAY), "l'état est un affichage")
        helper.assertValueEqual(deliveries[0].message, ModMessages.STATUS_VERSION.with(runtime.version), "la version du mod")
        helper.assertValueEqual(deliveries[1].message, ModMessages.STATUS_SOURCE.with(PermissionSourceSetting.INTERNAL.value), "la source des droits")
        helper.assertValueEqual(deliveries[2].message, ModMessages.STATUS_FILE_VALID.with(GlobalSettings.FILE_NAME), "l'état de settings.json")
        helper.succeed()
    }

    /** L'état et le rechargement s'exécutent depuis la console, qui reçoit les mêmes situations. */
    @GameTest
    fun statusAndReloadAnswerTheConsole(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)

        val status = Harness.keysSentTo(null, runtime) { helper.assertValueEqual(dispatcher.execute("manymanycommands", Harness.console(helper)), 1, "le résultat de l'état, à la console") }
        val reload = Harness.keysSentTo(null, runtime) { helper.assertValueEqual(dispatcher.execute("manymanycommands reload", Harness.console(helper)), 1, "le résultat du rechargement, à la console") }

        helper.assertValueEqual(status, STATUS_KEYS, "les lignes de l'état, à la console")
        helper.assertValueEqual(reload, APPLIED_KEYS, "la réponse du rechargement, à la console")
        helper.succeed()
    }

    /** Le rechargement relit les fichiers du serveur : un fichier valide s'applique, en succès, et la commande rend le nombre de fichiers appliqués. */
    @GameTest
    fun reloadAppliesAValidFile(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val player = Harness.player(helper)

        val deliveries = Harness.record(runtime) {
            helper.assertValueEqual(Harness.serverDispatcher(helper).execute("manymanycommands reload", Harness.asGameMaster(player)), 1, "le résultat de /manymanycommands reload")
        }.filter { it.player === player }

        helper.assertValueEqual(deliveries.map { it.message }, listOf(ModMessages.RELOAD_FILE_APPLIED.with(GlobalSettings.FILE_NAME)), "la réponse du rechargement")
        helper.assertValueEqual(deliveries.single().message.type, MessageType.SUCCESS, "un fichier appliqué est un succès")
        helper.succeed()
    }

    /** `/mmc` vaut `/manymanycommands`, pour toutes ses sous-commandes : mêmes résultats, mêmes situations. */
    @GameTest
    fun aliasAnswersLikeTheCommand(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)

        val status = Harness.keysSentTo(player, runtime) { helper.assertValueEqual(dispatcher.execute("mmc", Harness.asGameMaster(player)), 1, "le résultat de /mmc") }
        val reload = Harness.keysSentTo(player, runtime) { helper.assertValueEqual(dispatcher.execute("mmc reload", Harness.asGameMaster(player)), 1, "le résultat de /mmc reload") }

        helper.assertValueEqual(status, STATUS_KEYS, "les lignes de /mmc")
        helper.assertValueEqual(reload, APPLIED_KEYS, "la réponse de /mmc reload")
        helper.succeed()
    }

    /** Sur un serveur aux réglages valides, la connexion d'un joueur n'annonce rien, opérateur ou non. */
    @GameTest
    fun joiningAnnouncesNothingWhenSettingsAreValid(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)

        val deliveries = Harness.record(runtime) { Harness.player(helper) }

        helper.assertValueEqual(deliveries, emptyList(), "les messages reçus à la connexion")
        helper.succeed()
    }

    // ── Sur un état propre au test : les fichiers qu'on réécrit ──

    /** Un fichier invalide au rechargement est refusé avec ses constats, en refus, et les réglages en vigueur restent. L'état le montre refusé. */
    @GameTest
    fun reloadRefusesAnInvalidFile(helper: GameTestHelper) {
        val lab = Lab.open("reload-refuses-an-invalid-file", settings = """{ "language": "fr" }""")
        val player = Harness.player(helper)
        lab.writeSettings("{\n  \"language\": \"en\",\n  \"langage\": \"en\"\n}")

        val reload = Harness.record(lab.runtime) {
            helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player)), 0, "aucun fichier appliqué")
        }.filter { it.player === player }
        val status = Harness.keysSentTo(player, lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.asGameMaster(player)) }

        helper.assertValueEqual(reload.map { it.message.key }, REFUSED_KEYS, "la réponse du rechargement refusé")
        helper.assertValueEqual(reload.map { it.message.type }.toSet(), setOf(MessageType.REFUSAL), "un fichier refusé et ses constats sont des refus")
        helper.assertValueEqual(lab.runtime.settings.language, LanguageSetting.FRENCH, "la langue en vigueur est restée celle d'avant")
        helper.assertValueEqual(status.last(), ModMessages.STATUS_FILE_REFUSED.key, "l'état de settings.json après le refus")
        helper.succeed()
    }

    /** La console reçoit le refus d'un fichier comme un joueur, puis son application une fois le fichier corrigé. */
    @GameTest
    fun consoleSeesARefusalThenTheFix(helper: GameTestHelper) {
        val lab = Lab.open("console-sees-a-refusal-then-the-fix")
        lab.writeSettings("{\n  \"permission-source\": \"luckperms\"\n}")

        val refused = Harness.keysSentTo(null, lab.runtime) { helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)), 0, "aucun fichier appliqué") }
        lab.writeSettings("{\n  \"language\": \"en\"\n}")
        val applied = Harness.keysSentTo(null, lab.runtime) { helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)), 1, "un fichier appliqué") }

        helper.assertValueEqual(refused, REFUSED_KEYS, "la réponse du rechargement refusé, à la console")
        helper.assertValueEqual(applied, APPLIED_KEYS, "la réponse du rechargement corrigé, à la console")
        helper.assertValueEqual(lab.runtime.settings.language, LanguageSetting.ENGLISH, "la langue rechargée")
        helper.succeed()
    }

    /** Un réglage lu au seul démarrage se recharge, et la réponse dit qu'il attend le redémarrage : l'alias reste enregistré, la source des droits affichée reste celle du démarrage. */
    @GameTest
    fun reloadTellsWhatWaitsForTheRestart(helper: GameTestHelper) {
        val lab = Lab.open("reload-tells-what-waits-for-the-restart")
        val player = Harness.player(helper)
        lab.writeSettings("""{ "permission-source": "external", "mmc-alias": false }""")

        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player)) }.filter { it.player === player }.map { it.message }
        val status = Harness.record(lab.runtime) { lab.dispatcher.execute("mmc", Harness.asGameMaster(player)) }.filter { it.player === player }.map { it.message }

        val expected = listOf(
            ModMessages.RELOAD_FILE_APPLIED.with(GlobalSettings.FILE_NAME),
            ModMessages.RELOAD_RESTART_REQUIRED.with(GlobalSettings.PERMISSION_SOURCE_KEY),
            ModMessages.RELOAD_RESTART_REQUIRED.with(GlobalSettings.MMC_ALIAS_KEY),
        )
        helper.assertValueEqual(reload, expected, "la réponse du rechargement")
        helper.assertValueEqual(reload.last().type, MessageType.DISPLAY, "le réglage qui attend le redémarrage est un affichage")
        helper.assertValueEqual(status[1], ModMessages.STATUS_SOURCE.with(PermissionSourceSetting.INTERNAL.value), "la source des droits affichée reste celle du démarrage")
        helper.assertTrue(lab.registerCommands().root.getChild("mmc") != null, "le /reload du jeu garde l'alias du démarrage")
        helper.succeed()
    }

    /**
     * La journalisation de `reload` : la ligne d'un fichier appliqué se journalise, un refus non. Lancée par un joueur, la ligne part avec sa clé
     * et le repli de la langue de ce joueur, ce que lisent les autres opérateurs, et le journal du serveur, qui connaît les clés du mod par son
     * `en_us.json`, l'écrit en anglais. Lancée depuis la console, elle est un texte déjà résolu, dans la langue de la console.
     */
    @GameTest
    fun reloadIsLoggedLikeAnOperatorCommand(helper: GameTestHelper) {
        val lab = Lab.open("reload-is-logged-like-an-operator-command", settings = """{ "language": "fr" }""")
        val texts = TextCatalog.load()
        val player = Harness.player(helper)
        val applied = ModMessages.RELOAD_FILE_APPLIED.with(GlobalSettings.FILE_NAME).text

        val playerSource = Harness.RecordingSource()
        lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player).withSource(playerSource))
        val playerLine = playerSource.received.single()
        val playerContents = playerLine.contents as? TranslatableContents ?: throw helper.assertionException("la ligne d'un joueur part sans sa clé de traduction")
        helper.assertValueEqual(playerSource.journalRequests, 1, "la ligne d'un fichier appliqué se journalise, lancée par un joueur")
        helper.assertValueEqual(playerContents.key, applied.key, "la clé de la ligne journalisée")
        val fallback = playerContents.fallback ?: throw helper.assertionException("la ligne d'un joueur part sans texte de repli")
        helper.assertValueEqual(fallback, texts.pattern(Language.FRENCH, applied.key), "le repli que lisent les autres opérateurs, dans la langue du joueur")
        helper.assertValueEqual(playerLine.string, texts.resolve(Language.ENGLISH, applied), "la ligne telle que le journal du serveur l'écrit")

        val consoleSource = Harness.RecordingSource()
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper).withSource(consoleSource))
        val consoleLine = consoleSource.received.single()
        helper.assertValueEqual(consoleSource.journalRequests, 1, "la ligne d'un fichier appliqué se journalise, lancée depuis la console")
        helper.assertFalse(consoleLine.contents is TranslatableContents, "la ligne de la console est un texte résolu, sans clé")
        helper.assertValueEqual(consoleLine.string, texts.resolve(Language.FRENCH, applied), "la ligne de la console, dans la langue de la console")

        lab.writeSettings("{\n  \"langage\": \"fr\"\n}")
        val refusedSource = Harness.RecordingSource()
        lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player).withSource(refusedSource))
        helper.assertValueEqual(refusedSource.received.size, 2, "le refus et son constat sont reçus")
        helper.assertValueEqual(refusedSource.journalRequests, 0, "un refus ne se journalise pas")
        helper.succeed()
    }

    /** L'alias suit le réglage `mmc-alias` du démarrage : désactivé, `/mmc` n'est pas enregistré, et le réactiver n'y change rien avant le redémarrage. */
    @GameTest
    fun aliasFollowsItsStartupSetting(helper: GameTestHelper) {
        val lab = Lab.open("alias-follows-its-startup-setting", settings = """{ "mmc-alias": false }""")

        helper.assertTrue(lab.dispatcher.root.getChild("manymanycommands") != null, "/manymanycommands est enregistrée")
        helper.assertTrue(lab.dispatcher.root.getChild("mmc") == null, "/mmc n'est pas enregistré quand son réglage est inactif")
        Harness.assertUnknownCommand(helper, lab.dispatcher, Harness.console(helper), "mmc")

        lab.writeSettings("""{ "mmc-alias": true }""")
        val reload = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)) }

        helper.assertValueEqual(reload, listOf(ModMessages.RELOAD_FILE_APPLIED.key, ModMessages.RELOAD_RESTART_REQUIRED.key), "la réponse du rechargement")
        helper.assertTrue(lab.registerCommands().root.getChild("mmc") == null, "/mmc attend le redémarrage pour revenir")
        helper.succeed()
    }

    /**
     * Des réglages invalides au démarrage : le mod démarre sur les réglages livrés, l'état montre le fichier refusé, et l'annonce va au joueur qui se
     * connecte avec le nœud `manymanycommands.status`, pas aux autres. Le fichier corrigé puis rechargé, plus personne ne la reçoit.
     */
    @GameTest
    fun invalidSettingsAtStartupAreAnnouncedOnJoin(helper: GameTestHelper) {
        val lab = Lab.open("invalid-settings-at-startup-are-announced-on-join", settings = "{\n  \"language\": \"klingon\"\n}")
        val server = helper.level.server
        val operator = Harness.player(helper)
        val visitor = Harness.player(helper)
        // Le niveau se donne : le serveur GameTest n'accorde aucun niveau à un opérateur inscrit sans le sien.
        server.playerList.op(operator.nameAndId(), Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty())
        try {
            helper.assertValueEqual(lab.runtime.settings, GlobalSettings(), "les réglages livrés s'appliquent")
            val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.console(helper)) }
            helper.assertValueEqual(status.last(), ModMessages.STATUS_FILE_REFUSED.key, "l'état de settings.json")

            val announced = Harness.record(lab.runtime) {
                lab.runtime.greet(operator)
                lab.runtime.greet(visitor)
            }
            helper.assertValueEqual(announced, listOf(Delivery(operator, ModMessages.STARTUP_INVALID_SETTINGS.with())), "l'annonce à la connexion")
            helper.assertValueEqual(announced.single().message.type, MessageType.REFUSAL, "l'annonce est un refus")

            lab.writeSettings("{\n  \"language\": \"fr\"\n}")
            lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
            helper.assertValueEqual(Harness.record(lab.runtime) { lab.runtime.greet(operator) }, emptyList(), "l'annonce après le rechargement du fichier corrigé")
        } finally {
            server.playerList.deop(operator.nameAndId())
        }
        helper.succeed()
    }
}
