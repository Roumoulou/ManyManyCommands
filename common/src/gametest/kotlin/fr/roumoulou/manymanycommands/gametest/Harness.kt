// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.roumoulou.manymanycommands.ManyManyCommands
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.commands.ModCommands
import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import java.nio.file.Path
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
 * réécrit un fichier de réglages ouvre le sien ([Lab]) : il ne touche jamais au `settings.json` du serveur.
 */
object Harness {

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
     * Un état du mod propre à un test, avec ses commandes : ouvert sur un dossier à lui, sous le dossier des configurations du serveur, que chaque
     * run remet à neuf.
     */
    class Lab private constructor(val directory: Path, permissionSource: PermissionSource) {

        companion object {

            /**
             * @param name le nom du dossier, propre au test
             * @param settings le contenu de `settings.json` avant l'ouverture, `null` pour laisser le mod le créer
             */
            @OptIn(ExperimentalPathApi::class)
            fun open(name: String, settings: String? = null, permissionSource: PermissionSource = PermissionSource.SILENT): Lab {
                val directory = FabricLoader.getInstance().configDir.resolve("manymanycommands-gametest").resolve(name)
                directory.deleteRecursively()
                directory.createDirectories()
                if (settings != null) directory.resolve(GlobalSettings.FILE_NAME).writeText(settings)
                return Lab(directory, permissionSource)
            }
        }

        val runtime: ModRuntime = ModRuntime.start(directory, version = "0.0.0+gametest", permissionSource = permissionSource)

        /** Les commandes telles que le jeu les enregistre pour cet état. */
        val dispatcher: CommandDispatcher<CommandSourceStack> = registerCommands()

        /** Ce que fait le `/reload` du jeu : un répartiteur neuf, pour le même état. */
        fun registerCommands(): CommandDispatcher<CommandSourceStack> = CommandDispatcher<CommandSourceStack>().also { ModCommands.register(it, runtime) }

        fun writeSettings(json: String) = directory.resolve(GlobalSettings.FILE_NAME).writeText(json)
    }
}
