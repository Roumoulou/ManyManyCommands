// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.ManyManyCommands
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.commands.ModCommands
import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

/** Un message qu'un test a vu passer : le joueur qui le reçoit, `null` pour la console. */
data class Delivery(val player: ServerPlayer?, val message: Message)

/**
 * Le harnais des gametests du mod.
 *
 * Les tests d'un run partagent un serveur. Celui qui ne fait que lire joue sur l'état du serveur ([serverRuntime]) et sur ses commandes. Celui qui
 * réécrit un fichier de réglages ouvre le sien ([Lab]) : il ne touche jamais aux fichiers de réglages du serveur.
 */
object Harness {

    /** Les tables des cinq fichiers de règles, dans l'ordre où l'état et le rechargement les donnent. */
    val RULES_TABLES: List<RulesTable> = listOf(HomesRules, WarpsRules, BackRules, WildRules, TpaRules)

    /** Les six fichiers de réglages, par le nom que les messages leur donnent : `settings.json`, puis les cinq fichiers de règles. */
    val SETTINGS_FILES: List<String> = listOf(GlobalSettings.FILE_NAME) + RULES_TABLES.map { it.fileName }

    /** Les fichiers de données du monde, par le nom que les messages leur donnent, dans l'ordre où l'état et le rechargement les donnent. */
    val DATA_FILES: List<String> = listOf(DataRules.fileName(HomesRules.family), DataRules.fileName(BackRules.family))

    /** Tous les fichiers du mod, dans l'ordre où l'état et le rechargement les donnent. */
    val FILES: List<String> = SETTINGS_FILES + DATA_FILES

    /** L'état du mod pour le serveur GameTest, ouvert par le point d'entrée avant son démarrage. */
    fun serverRuntime(helper: GameTestHelper): ModRuntime = ManyManyCommands.runtime ?: throw helper.assertionException("le serveur GameTest n'a pas d'état du mod")

    /** Les commandes du serveur GameTest, celles que le point d'entrée a enregistrées. */
    fun serverDispatcher(helper: GameTestHelper): CommandDispatcher<CommandSourceStack> = helper.level.server.commands.dispatcher

    /** Un joueur simulé, connecté au serveur : chaque test a le sien. Il n'est pas opérateur. */
    fun player(helper: GameTestHelper): ServerPlayer = helper.makeMockServerPlayerInLevel()

    /** La source des commandes de [player] s'il avait le niveau des maîtres du jeu, sans l'inscrire aux opérateurs du serveur. */
    fun asGameMaster(player: ServerPlayer): CommandSourceStack = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)

    fun console(helper: GameTestHelper): CommandSourceStack = helper.level.server.createCommandSourceStack()

    /** Les messages que [runtime] envoie pendant [block], dans l'ordre. */
    fun record(runtime: ModRuntime, block: () -> Unit): List<Delivery> {
        val deliveries = mutableListOf<Delivery>()
        runtime.messenger.observe { player, message -> deliveries += Delivery(player, message) }.use { block() }
        return deliveries
    }

    /** Les clés des messages que [runtime] envoie à [player] pendant [block] ; à la console quand [player] est `null`. */
    fun keysSentTo(player: ServerPlayer?, runtime: ModRuntime, block: () -> Unit): List<String> = record(runtime, block).filter { it.player === player }.map { it.message.key }

    /** La commande n'existe pas pour [source] : le répartiteur la refuse comme une commande inconnue, et rien ne s'exécute. */
    fun assertUnknownCommand(helper: GameTestHelper, dispatcher: CommandDispatcher<CommandSourceStack>, source: CommandSourceStack, command: String) {
        try {
            dispatcher.execute(command, source)
        } catch (e: CommandSyntaxException) {
            helper.assertValueEqual(e.type, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), "le refus de /$command")
            return
        }
        throw helper.assertionException("/$command s'est exécutée alors qu'elle ne devait pas exister pour cette source")
    }

    /**
     * La sous-commande n'existe pas pour [source], qui a la commande : le répartiteur du jeu, qui connaît le début de la ligne, refuse ce qui
     * suit comme un argument incorrect, sa réponse à une sous-commande cachée, et rien ne s'exécute.
     */
    fun assertUnknownSubcommand(helper: GameTestHelper, dispatcher: CommandDispatcher<CommandSourceStack>, source: CommandSourceStack, command: String) {
        try {
            dispatcher.execute(command, source)
        } catch (e: CommandSyntaxException) {
            helper.assertValueEqual(e.type, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(), "le refus de /$command")
            return
        }
        throw helper.assertionException("/$command s'est exécutée alors que sa sous-commande ne devait pas exister pour cette source")
    }

    /**
     * Une source que le test met à la place de celle d'un joueur ou de la console (`withSource`) : elle garde ce que la source reçoit, et compte
     * les réponses que le jeu a voulu journaliser. Le jeu ne lui demande [shouldInformAdmins] que pour une réponse journalisée. Elle répond non :
     * rien ne part chez les opérateurs ni au journal du serveur.
     */
    class RecordingSource : CommandSource {

        val received = mutableListOf<Component>()

        var journalRequests = 0
            private set

        override fun sendSystemMessage(message: Component) {
            received += message
        }

        override fun acceptsSuccess(): Boolean = true

        override fun acceptsFailure(): Boolean = true

        override fun shouldInformAdmins(): Boolean {
            journalRequests++
            return false
        }
    }

    /**
     * Un état du mod propre à un test, avec ses commandes et son monde : ouvert sur un dossier à lui, sous le dossier des configurations du
     * serveur, que chaque run remet à neuf. Ses fichiers de données vivent dans `world\` de ce dossier, ouvert comme le fait le démarrage du
     * serveur.
     */
    class Lab private constructor(val directory: Path, permissionSource: PermissionSource, clock: Clock) {

        companion object {

            /**
             * @param name le nom du dossier, propre au test
             * @param settings le contenu de `settings.json` avant l'ouverture, `null` pour laisser le mod le créer
             * @param rules le contenu des fichiers de règles avant l'ouverture, par leur table : le mod crée ceux qui manquent
             * @param data le contenu des fichiers de données avant l'ouverture, par leur famille : le mod crée vides ceux qui manquent
             * @param clock l'horloge de l'état, celle de la machine quand le test ne mesure aucun temps
             */
            @OptIn(ExperimentalPathApi::class)
            fun open(
                name: String,
                settings: String? = null,
                rules: Map<RulesTable, String> = emptyMap(),
                data: Map<String, String> = emptyMap(),
                permissionSource: PermissionSource = PermissionSource.SILENT,
                clock: Clock = Clock.systemUTC(),
            ): Lab {
                val directory = directory(name)
                directory.deleteRecursively()
                directory.createDirectories()
                if (settings != null) directory.resolve(GlobalSettings.FILE_NAME).writeText(settings)
                rules.forEach { (table, json) -> write(directory.resolve(table.fileName), json) }
                data.forEach { (family, json) -> write(worldDirectory(directory).resolve("$family.json"), json) }
                return Lab(directory, permissionSource, clock)
            }

            /** Rouvre un état sur le dossier qu'un `open` du même [name] a laissé, fichiers compris, comme le fait un redémarrage du serveur. */
            fun reopen(name: String, permissionSource: PermissionSource = PermissionSource.SILENT, clock: Clock = Clock.systemUTC()): Lab = Lab(directory(name), permissionSource, clock)

            private fun directory(name: String): Path = FabricLoader.getInstance().configDir.resolve("manymanycommands-gametest").resolve(name)

            private fun worldDirectory(directory: Path): Path = directory.resolve("world")

            private fun write(file: Path, json: String) {
                file.parent.createDirectories()
                file.writeText(json)
            }
        }

        val runtime: ModRuntime = ModRuntime.start(directory, version = "0.0.0+gametest", permissionSource = permissionSource, clock = clock).also { it.openWorld(worldDirectory(directory)) }

        /** Les commandes telles que le jeu les enregistre pour cet état. */
        val dispatcher: CommandDispatcher<CommandSourceStack> = registerCommands()

        /** Ce que fait le `/reload` du jeu : un répartiteur neuf, pour le même état. */
        fun registerCommands(): CommandDispatcher<CommandSourceStack> = CommandDispatcher<CommandSourceStack>().also { ModCommands.register(it, runtime) }

        fun writeSettings(json: String) = directory.resolve(GlobalSettings.FILE_NAME).writeText(json)

        /** Réécrit le fichier de règles de [table]. */
        fun writeRules(table: RulesTable, json: String) = write(directory.resolve(table.fileName), json)

        /** Réécrit le fichier de données de [family], dans le monde de cet état. */
        fun writeData(family: String, json: String) = write(worldDirectory(directory).resolve("$family.json"), json)
    }
}
