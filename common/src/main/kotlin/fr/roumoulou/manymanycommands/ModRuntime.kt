// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import fr.roumoulou.manymanycommands.files.back.BackRulesFile
import fr.roumoulou.manymanycommands.files.homes.HomesRulesFile
import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.files.settings.GlobalSettingsFile
import fr.roumoulou.manymanycommands.files.tpa.TpaRulesFile
import fr.roumoulou.manymanycommands.files.warps.WarpsRulesFile
import fr.roumoulou.manymanycommands.files.wild.WildRulesFile
import fr.roumoulou.manymanycommands.game.message.Messenger
import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.game.permission.Rights
import fr.roumoulou.manymanycommands.game.teleport.TeleportEngine
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.mod.FileSetting
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import fr.roumoulou.manymanycommands.rules.mod.ModReports
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.time.Clock

/**
 * L'état du mod pour un serveur : ses fichiers de réglages, ses textes, l'envoi de ses messages, ses droits, son horloge et ses téléportations.
 *
 * Il s'ouvre sur le dossier qu'on lui donne et ne lit aucun chemin de lui-même : le point d'entrée lui donne le dossier du serveur, un test le
 * sien. Chaque commande reçoit son `ModRuntime` à l'enregistrement.
 *
 * @property clock l'heure réelle, pour tout ce qui mesure un temps. Une règle ne la lit jamais : elle reçoit l'instant, que le code de la couche
 * `game` prend ici. Un test y met une horloge qu'il tient, parce que le serveur GameTest tique sans cadence.
 */
class ModRuntime private constructor(val version: String, configDirectory: Path, permissionSource: PermissionSource, val clock: Clock) {

    companion object {

        /**
         * Ouvre l'état du mod sur [configDirectory], le dossier du mod dans celui des configurations, et y lit les fichiers de réglages : un
         * fichier qui manque est créé, un fichier invalide laisse ses réglages livrés en place et se dit en erreur au journal.
         *
         * @param version la version du mod, celle que l'état affiche
         * @param permissionSource la source des droits
         * @param clock l'horloge de l'état, celle de la machine par défaut
         */
        fun start(configDirectory: Path, version: String, permissionSource: PermissionSource = PermissionSource.SILENT, clock: Clock = Clock.systemUTC()): ModRuntime =
            ModRuntime(version, configDirectory, permissionSource, clock)
    }

    private val globalSettingsFile: SettingsFile<GlobalSettings> = GlobalSettingsFile.at(configDirectory)

    /** Le fichier de règles de chaque famille, par sa table. */
    private val rulesFiles: Map<RulesTable, SettingsFile<FamilyRules>> = linkedMapOf(
        HomesRules to HomesRulesFile.at(configDirectory),
        WarpsRules to WarpsRulesFile.at(configDirectory),
        BackRules to BackRulesFile.at(configDirectory),
        WildRules to WildRulesFile.at(configDirectory),
        TpaRules to TpaRulesFile.at(configDirectory),
    )

    /** Les fichiers de réglages, dans l'ordre où l'état et le rechargement les donnent. */
    val settingsFiles: List<SettingsFile<*>> = listOf(globalSettingsFile) + rulesFiles.values

    val messenger = Messenger(TextCatalog.load(), language = { settings.language })

    val rights = Rights(permissionSource)

    /** Le déroulé des téléportations : celles en attente, les cooldowns et les plages de quota, que le serveur garde en mémoire. */
    val teleports = TeleportEngine(messenger, rights, clock)

    /**
     * Les réglages globaux lus à l'ouverture. Les commandes s'enregistrent sur eux, et ceux qui ne prennent effet qu'au redémarrage du serveur
     * gardent leur valeur d'ici.
     */
    val startupSettings: GlobalSettings

    /** Les règles de chaque famille lues à l'ouverture, pour la même raison. */
    private val rulesAtStartup: Map<RulesTable, FamilyRules>

    /** Les réglages globaux en vigueur, que le rechargement remplace. */
    val settings: GlobalSettings get() = globalSettingsFile.inForce

    /** Les réglages livrés tiennent la place d'un fichier refusé au démarrage, qu'aucun rechargement n'a appliqué depuis. */
    val hasShippedSettingsInPlace: Boolean get() = settingsFiles.any { it.isShippedInPlace }

    init {
        settingsFiles.forEach { it.load() }
        startupSettings = settings
        rulesAtStartup = rulesFiles.mapValues { (_, file) -> file.inForce }
        // Le journal du serveur ne nomme pas ses loggers : chaque ligne dit de quel mod elle vient.
        invalidSettingsReport().forEach { ManyManyCommands.LOGGER.error("[ManyManyCommands] {}", messenger.forJournal(it)) }
    }

    /** Les règles en vigueur de la famille de [table], que le rechargement remplace. */
    fun rules(table: RulesTable): FamilyRules = rulesFiles.getValue(table).inForce

    /** Les règles de la famille de [table] lues à l'ouverture : son activation et celle de ses alias gardent leur valeur d'ici. */
    fun startupRules(table: RulesTable): FamilyRules = rulesAtStartup.getValue(table)

    /** Le jeu de règles de [source] dans la famille de [table] (cahier des charges, chapitre 2.4.3), pris dans les règles en vigueur. */
    fun ruleSet(source: CommandSourceStack, table: RulesTable): RuleValues = table.setFor(rules(table)) { rights.has(source, it) }

    /** La réponse de `/manymanycommands`. */
    fun status(): List<Message> = ModReports.status(version, startupSettings.permissionSource, settingsFiles)

    /** Relit les fichiers, et rend la réponse de `/manymanycommands reload`. */
    fun reload(): List<Message> {
        settingsFiles.forEach { it.load() }
        val awaitingRestartInRules = rulesFiles.flatMap { (table, file) -> table.awaitingRestart(file.inForce, startupRules(table)).map { FileSetting(file.name, it) } }
        return ModReports.reload(settingsFiles, settings.awaitingRestart(startupSettings), awaitingRestartInRules)
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
