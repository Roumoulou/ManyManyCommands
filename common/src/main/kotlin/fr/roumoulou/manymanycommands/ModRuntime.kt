// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.files.settings.GlobalSettingsFile
import fr.roumoulou.manymanycommands.game.message.Messenger
import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.game.permission.Rights
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import fr.roumoulou.manymanycommands.rules.mod.ModReports
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

/**
 * L'état du mod pour un serveur : ses fichiers de réglages, ses textes, l'envoi de ses messages et ses droits.
 *
 * Il s'ouvre sur le dossier qu'on lui donne et ne lit aucun chemin de lui-même : le point d'entrée lui donne le dossier du serveur, un test le
 * sien. Chaque commande reçoit son `ModRuntime` à l'enregistrement.
 */
class ModRuntime private constructor(val version: String, private val globalSettingsFile: SettingsFile<GlobalSettings>, permissionSource: PermissionSource) {

    companion object {

        /**
         * Ouvre l'état du mod sur [configDirectory], le dossier du mod dans celui des configurations, et y lit les fichiers de réglages : un
         * fichier qui manque est créé, un fichier invalide laisse ses réglages livrés en place et se dit en erreur au journal.
         *
         * @param version la version du mod, celle que l'état affiche
         * @param permissionSource la source des droits
         */
        fun start(configDirectory: Path, version: String, permissionSource: PermissionSource = PermissionSource.SILENT): ModRuntime =
            ModRuntime(version, GlobalSettingsFile.at(configDirectory), permissionSource)
    }

    /** Les fichiers de réglages, dans l'ordre où l'état et le rechargement les donnent. */
    val settingsFiles: List<SettingsFile<*>> = listOf(globalSettingsFile)

    val messenger = Messenger(TextCatalog.load(), language = { settings.language })

    val rights = Rights(permissionSource)

    /**
     * Les réglages globaux lus à l'ouverture. Les commandes s'enregistrent sur eux, et ceux qui ne prennent effet qu'au redémarrage du serveur
     * gardent leur valeur d'ici.
     */
    val startupSettings: GlobalSettings

    /** Les réglages globaux en vigueur, que le rechargement remplace. */
    val settings: GlobalSettings get() = globalSettingsFile.inForce

    /** Les réglages livrés tiennent la place d'un fichier refusé au démarrage, qu'aucun rechargement n'a appliqué depuis. */
    val hasShippedSettingsInPlace: Boolean get() = settingsFiles.any { it.isShippedInPlace }

    init {
        settingsFiles.forEach { it.load() }
        startupSettings = settings
        // Le journal du serveur ne nomme pas ses loggers : chaque ligne dit de quel mod elle vient.
        invalidSettingsReport().forEach { ManyManyCommands.LOGGER.error("[ManyManyCommands] {}", messenger.forJournal(it)) }
    }

    /** La réponse de `/manymanycommands`. */
    fun status(): List<Message> = ModReports.status(version, startupSettings.permissionSource, settingsFiles)

    /** Relit les fichiers, et rend la réponse de `/manymanycommands reload`. */
    fun reload(): List<Message> {
        settingsFiles.forEach { it.load() }
        return ModReports.reload(settingsFiles, settings.awaitingRestart(startupSettings))
    }

    /** À la connexion d'un joueur : les réglages invalides au démarrage s'annoncent à qui a le nœud `manymanycommands.status`. */
    fun greet(player: ServerPlayer) {
        if (hasShippedSettingsInPlace && rights.has(player.createCommandSourceStack(), ModNodes.STATUS)) messenger.tell(player, ModMessages.STARTUP_INVALID_SETTINGS.with())
    }

    /**
     * Ce que le journal reçoit en erreur à l'ouverture, dans la langue de la console : chaque fichier refusé avec ses constats, puis l'annonce des
     * réglages livrés. Vide quand aucun réglage livré ne tient la place d'un fichier.
     */
    fun invalidSettingsReport(): List<Message> {
        if (!hasShippedSettingsInPlace) return emptyList()
        return settingsFiles.filter { it.isShippedInPlace }.flatMap(ModReports::refusal) + ModMessages.STARTUP_INVALID_SETTINGS.with()
    }
}
