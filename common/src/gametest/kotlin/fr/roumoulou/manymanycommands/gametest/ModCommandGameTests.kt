// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.homes.HomesData
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.level.storage.LevelResource
import java.util.Optional
import kotlin.io.path.exists

/**
 * `/manymanycommands`, `reload` et l'alias `/mmc` (`manymanycommands.md` des specs), joués par un joueur simulé et par la console : chaque
 * situation du tableau des messages a son test, reconnue à sa clé.
 */
class ModCommandGameTests {

    private companion object {
        /** Tous les fichiers du mod : les six fichiers de réglages, puis les données du monde. */
        val FILES = Harness.FILES
        val STATUS_KEYS = listOf(ModMessages.STATUS_VERSION.key, ModMessages.STATUS_SOURCE.key) + FILES.map { ModMessages.STATUS_FILE_VALID.key }
        val APPLIED_KEYS = FILES.map { ModMessages.RELOAD_FILE_APPLIED.key }

        /** `settings.json` refusé pour un constat, les autres fichiers appliqués. */
        val SETTINGS_REFUSED_KEYS = listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key) + APPLIED_KEYS.drop(1)

        /** La place de la ligne de `settings.json` dans l'état, après la version et la source des droits. */
        const val SETTINGS_STATUS_LINE = 2
    }

    // ── Sur le serveur lui-même : ses commandes, son état, son premier lancement ──

    /** Le premier lancement du mod écrit ses six fichiers de réglages dans le dossier des configurations du serveur, et le serveur démarre sur les réglages livrés. */
    @GameTest
    fun firstLaunchWritesTheSettingsFiles(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val modDirectory = FabricLoader.getInstance().configDir.resolve(MOD_ID)

        Harness.SETTINGS_FILES.forEach { helper.assertTrue(modDirectory.resolve(it).exists(), "le premier lancement n'a pas écrit $it") }
        helper.assertValueEqual(runtime.startupSettings, GlobalSettings(), "les réglages du démarrage sont les réglages livrés")
        Harness.RULES_TABLES.forEach { helper.assertValueEqual(runtime.startupRules(it), it.shipped, "les règles du démarrage de ${it.family} sont les règles livrées") }
        helper.assertFalse(runtime.hasShippedSettingsInPlace, "les fichiers créés par le mod sont valides")
        helper.succeed()
    }

    /**
     * Le démarrage du serveur ouvre les fichiers de données dans le dossier du mod de son monde : `homes.json` y naît vide et disponible, et
     * l'état comme le rechargement le donnent sous `world/`, après les fichiers de réglages.
     */
    @GameTest
    fun serverStartOpensTheDataFilesOfTheWorld(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val worldRoot = helper.level.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
        val homesFile = worldRoot.resolve(MOD_ID).resolve("homes.json")

        val homes = runtime.worldData?.homes ?: throw helper.assertionException("le démarrage du serveur n'a pas ouvert les données du monde")
        helper.assertTrue(homesFile.exists(), "homes.json n'a pas été créé sous le dossier du monde : $homesFile")
        helper.assertTrue(homes.isAvailable, "les homes sont disponibles sur un fichier né vide")
        helper.assertValueEqual(homes.content ?: throw helper.assertionException("le fichier né vide n'a pas de contenu"), HomesData(), "le fichier né vide ne tient aucun joueur")
        val status = Harness.keysSentTo(null, runtime) { Harness.serverDispatcher(helper).execute("manymanycommands", Harness.console(helper)) }
        val reload = Harness.record(runtime) { Harness.serverDispatcher(helper).execute("manymanycommands reload", Harness.console(helper)) }.map { it.message }
        helper.assertValueEqual(status.size, SETTINGS_STATUS_LINE + FILES.size, "l'état donne tous les fichiers")
        helper.assertValueEqual(reload.takeLast(2), listOf(ModMessages.RELOAD_FILE_APPLIED.with("world/homes.json"), ModMessages.RELOAD_FILE_APPLIED.with("world/back.json")), "le rechargement donne les données du monde en dernier, sous world/")
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
        helper.assertValueEqual(deliveries[SETTINGS_STATUS_LINE].message, ModMessages.STATUS_FILE_VALID.with(GlobalSettings.FILE_NAME), "l'état de settings.json")
        helper.succeed()
    }

    /**
     * L'état et le rechargement donnent chaque fichier du mod : `settings.json`, puis les cinq fichiers de règles, chacun sous son chemin dans
     * le dossier du mod, puis les données du monde sous `world/`.
     */
    @GameTest
    fun statusAndReloadGiveEveryFile(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)
        val names = listOf("settings.json", "rules/homes.json", "rules/warps.json", "rules/back.json", "rules/wild.json", "rules/tpa.json", "world/homes.json", "world/back.json")

        val status = Harness.record(runtime) { dispatcher.execute("manymanycommands", Harness.asGameMaster(player)) }.filter { it.player === player }.map { it.message }
        val reload = Harness.record(runtime) { dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player)) }.filter { it.player === player }.map { it.message }

        helper.assertValueEqual(status.drop(SETTINGS_STATUS_LINE), names.map { ModMessages.STATUS_FILE_VALID.with(it) }, "les fichiers de l'état")
        helper.assertValueEqual(reload, names.map { ModMessages.RELOAD_FILE_APPLIED.with(it) }, "les fichiers du rechargement")
        helper.succeed()
    }

    /** L'état et le rechargement s'exécutent depuis la console, qui reçoit les mêmes situations. */
    @GameTest
    fun statusAndReloadAnswerTheConsole(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)

        val status = Harness.keysSentTo(null, runtime) { helper.assertValueEqual(dispatcher.execute("manymanycommands", Harness.console(helper)), 1, "le résultat de l'état, à la console") }
        val reload = Harness.keysSentTo(null, runtime) { helper.assertValueEqual(dispatcher.execute("manymanycommands reload", Harness.console(helper)), FILES.size, "le résultat du rechargement, à la console") }

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
            helper.assertValueEqual(Harness.serverDispatcher(helper).execute("manymanycommands reload", Harness.asGameMaster(player)), FILES.size, "le résultat de /manymanycommands reload")
        }.filter { it.player === player }

        helper.assertValueEqual(deliveries.map { it.message }, FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) }, "la réponse du rechargement")
        helper.assertValueEqual(deliveries.map { it.message.type }.toSet(), setOf(MessageType.SUCCESS), "un fichier appliqué est un succès")
        helper.succeed()
    }

    /** `/mmc` vaut `/manymanycommands`, pour toutes ses sous-commandes : mêmes résultats, mêmes situations. */
    @GameTest
    fun aliasAnswersLikeTheCommand(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)

        val status = Harness.keysSentTo(player, runtime) { helper.assertValueEqual(dispatcher.execute("mmc", Harness.asGameMaster(player)), 1, "le résultat de /mmc") }
        val reload = Harness.keysSentTo(player, runtime) { helper.assertValueEqual(dispatcher.execute("mmc reload", Harness.asGameMaster(player)), FILES.size, "le résultat de /mmc reload") }

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

    /** Un fichier invalide au rechargement est refusé avec ses constats, en refus, et les réglages en vigueur restent. Les autres fichiers s'appliquent, et l'état le montre refusé. */
    @GameTest
    fun reloadRefusesAnInvalidFile(helper: GameTestHelper) {
        val lab = Lab.open("reload-refuses-an-invalid-file", settings = """{ "language": "fr" }""")
        val player = Harness.player(helper)
        lab.writeSettings("{\n  \"language\": \"en\",\n  \"langage\": \"en\"\n}")

        val reload = Harness.record(lab.runtime) {
            helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player)), FILES.size - 1, "les fichiers appliqués, tous sauf settings.json")
        }.filter { it.player === player }
        val status = Harness.keysSentTo(player, lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.asGameMaster(player)) }

        helper.assertValueEqual(reload.map { it.message.key }, SETTINGS_REFUSED_KEYS, "la réponse du rechargement refusé")
        helper.assertValueEqual(reload.take(2).map { it.message.type }.toSet(), setOf(MessageType.REFUSAL), "un fichier refusé et ses constats sont des refus")
        helper.assertValueEqual(lab.runtime.settings.language, LanguageSetting.FRENCH, "la langue en vigueur est restée celle d'avant")
        helper.assertValueEqual(status[SETTINGS_STATUS_LINE], ModMessages.STATUS_FILE_REFUSED.key, "l'état de settings.json après le refus")
        helper.succeed()
    }

    /**
     * Un fichier de règles invalide au rechargement est refusé à sa place parmi les six, avec son constat, son chemin et sa ligne. Ses règles en
     * vigueur restent, les autres fichiers s'appliquent, et l'état le montre refusé.
     */
    @GameTest
    fun reloadRefusesAnInvalidRulesFile(helper: GameTestHelper) {
        val lab = Lab.open("reload-refuses-an-invalid-rules-file", rules = mapOf(WildRules to """{ "rules": { "default": { "cooldown": 60 } } }"""))
        val player = Harness.player(helper)
        lab.writeRules(WildRules, "{\n  \"rules\": {\n    \"default\": {\n      \"cooldown\": -60\n    }\n  }\n}")

        val reload = Harness.record(lab.runtime) {
            helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player)), FILES.size - 1, "les fichiers appliqués, tous sauf rules/wild.json")
        }.filter { it.player === player }.map { it.message }
        val status = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.asGameMaster(player)) }.filter { it.player === player }.map { it.message }

        val wild = FILES.indexOf(WildRules.fileName)
        helper.assertValueEqual(reload.take(wild), FILES.take(wild).map { ModMessages.RELOAD_FILE_APPLIED.with(it) }, "les fichiers d'avant s'appliquent")
        helper.assertValueEqual(reload[wild], ModMessages.RELOAD_FILE_REFUSED.with(WildRules.fileName), "le fichier de règles refusé")
        helper.assertValueEqual(reload[wild + 1].key, ModMessages.RELOAD_FINDING.key, "son constat, avec son chemin et sa ligne")
        helper.assertValueEqual(reload[wild + 1].text.arguments.take(2), listOf<Any>("rules[default].cooldown", 4), "le chemin et la ligne du constat")
        helper.assertValueEqual(reload.drop(wild + 2), FILES.drop(wild + 1).map { ModMessages.RELOAD_FILE_APPLIED.with(it) }, "les fichiers d'après s'appliquent")
        helper.assertValueEqual(lab.runtime.rules(WildRules).default[WildRules.cooldown], 60L, "le cooldown en vigueur est resté celui d'avant")
        helper.assertValueEqual(status[SETTINGS_STATUS_LINE + wild], ModMessages.STATUS_FILE_REFUSED.with(WildRules.fileName), "l'état de rules/wild.json après le refus")
        helper.succeed()
    }

    /** La console reçoit le refus d'un fichier comme un joueur, puis son application une fois le fichier corrigé. */
    @GameTest
    fun consoleSeesARefusalThenTheFix(helper: GameTestHelper) {
        val lab = Lab.open("console-sees-a-refusal-then-the-fix")
        lab.writeSettings("{\n  \"permission-source\": \"luckperms\"\n}")

        val refused = Harness.keysSentTo(null, lab.runtime) { helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)), FILES.size - 1, "les fichiers appliqués, tous sauf settings.json") }
        lab.writeSettings("{\n  \"language\": \"en\"\n}")
        val applied = Harness.keysSentTo(null, lab.runtime) { helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)), FILES.size, "tous les fichiers appliqués") }

        helper.assertValueEqual(refused, SETTINGS_REFUSED_KEYS, "la réponse du rechargement refusé, à la console")
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

        val expected = FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) } + listOf(
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
     * L'activation d'une famille et celle de ses alias se rechargent, et la réponse dit qu'elles attendent le redémarrage, chacune avec son
     * fichier : cinq fichiers portent `enabled`. Les règles du démarrage ne bougent pas, et chaque rechargement le redit tant que l'écart dure.
     */
    @GameTest
    fun reloadTellsWhichFamilySwitchesWaitForTheRestart(helper: GameTestHelper) {
        val lab = Lab.open("reload-tells-which-family-switches-wait-for-the-restart")
        val player = Harness.player(helper)
        lab.writeRules(HomesRules, """{ "enabled": false, "aliases": false, "rules": { "default": { "max-homes": 9 } } }""")
        lab.writeRules(BackRules, """{ "enabled": false }""")

        val reload = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player)) }.filter { it.player === player }.map { it.message }
        val again = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)) }.filter { it.player == null }.map { it.message }

        val expected = FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) } + listOf(
            ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with(HomesRules.fileName, HomesRules.enabled.key),
            ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with(HomesRules.fileName, HomesRules.aliases.key),
            ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with(BackRules.fileName, BackRules.enabled.key),
        )
        helper.assertValueEqual(reload, expected, "la réponse du rechargement")
        helper.assertValueEqual(again, expected, "la réponse du rechargement suivant, à la console")
        helper.assertValueEqual(reload.last().type, MessageType.DISPLAY, "le réglage qui attend le redémarrage est un affichage")
        helper.assertValueEqual(lab.runtime.startupRules(HomesRules).settings[HomesRules.enabled], true, "l'activation des homes au démarrage")
        helper.assertValueEqual(lab.runtime.rules(HomesRules).default[HomesRules.maxHomes], 9L, "la limite de homes, elle, prend effet au rechargement")
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
        helper.assertValueEqual(playerSource.received.size, FILES.size, "une ligne par fichier appliqué, lancée par un joueur")
        val playerLine = playerSource.received.first()
        val playerContents = playerLine.contents as? TranslatableContents ?: throw helper.assertionException("la ligne d'un joueur part sans sa clé de traduction")
        helper.assertValueEqual(playerSource.journalRequests, FILES.size, "la ligne de chaque fichier appliqué se journalise, lancée par un joueur")
        helper.assertValueEqual(playerContents.key, applied.key, "la clé de la ligne journalisée")
        val fallback = playerContents.fallback ?: throw helper.assertionException("la ligne d'un joueur part sans texte de repli")
        helper.assertValueEqual(fallback, texts.pattern(Language.FRENCH, applied.key), "le repli que lisent les autres opérateurs, dans la langue du joueur")
        helper.assertValueEqual(playerLine.string, texts.resolve(Language.ENGLISH, applied), "la ligne telle que le journal du serveur l'écrit")

        val consoleSource = Harness.RecordingSource()
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper).withSource(consoleSource))
        val consoleLine = consoleSource.received.first()
        helper.assertValueEqual(consoleSource.journalRequests, FILES.size, "la ligne de chaque fichier appliqué se journalise, lancée depuis la console")
        helper.assertFalse(consoleLine.contents is TranslatableContents, "la ligne de la console est un texte résolu, sans clé")
        helper.assertValueEqual(consoleLine.string, texts.resolve(Language.FRENCH, applied), "la ligne de la console, dans la langue de la console")

        lab.writeSettings("{\n  \"langage\": \"fr\"\n}")
        val refusedSource = Harness.RecordingSource()
        lab.dispatcher.execute("manymanycommands reload", Harness.asGameMaster(player).withSource(refusedSource))
        helper.assertValueEqual(refusedSource.received.size, SETTINGS_REFUSED_KEYS.size, "le refus, son constat et les fichiers appliqués sont reçus")
        helper.assertValueEqual(refusedSource.journalRequests, FILES.size - 1, "le refus et son constat ne se journalisent pas, les fichiers appliqués si")
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

        helper.assertValueEqual(reload, APPLIED_KEYS + ModMessages.RELOAD_RESTART_REQUIRED.key, "la réponse du rechargement")
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
            helper.assertValueEqual(status[SETTINGS_STATUS_LINE], ModMessages.STATUS_FILE_REFUSED.key, "l'état de settings.json")

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

    /** Un fichier de règles invalide au démarrage laisse ses règles livrées en place, et s'annonce comme `settings.json` au joueur qui a le nœud de l'état. */
    @GameTest
    fun invalidRulesAtStartupLeaveTheShippedRules(helper: GameTestHelper) {
        val lab = Lab.open("invalid-rules-at-startup-leave-the-shipped-rules", rules = mapOf(HomesRules to "{\n  \"rules\": {\n    \"Vip\": {}\n  }\n}"))
        val server = helper.level.server
        val operator = Harness.player(helper)
        server.playerList.op(operator.nameAndId(), Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty())
        try {
            helper.assertValueEqual(lab.runtime.rules(HomesRules), HomesRules.shipped, "les règles livrées des homes s'appliquent")
            val status = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.console(helper)) }.map { it.message }
            helper.assertValueEqual(status[SETTINGS_STATUS_LINE + FILES.indexOf(HomesRules.fileName)], ModMessages.STATUS_FILE_REFUSED.with(HomesRules.fileName), "l'état de rules/homes.json")

            val announced = Harness.record(lab.runtime) { lab.runtime.greet(operator) }
            helper.assertValueEqual(announced, listOf(Delivery(operator, ModMessages.STARTUP_INVALID_SETTINGS.with())), "l'annonce à la connexion")
        } finally {
            server.playerList.deop(operator.nameAndId())
        }
        helper.succeed()
    }
}
