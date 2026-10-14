// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import fr.roumoulou.manymanycommands.files.back.BackDataFile
import fr.roumoulou.manymanycommands.files.back.BackRulesFile
import fr.roumoulou.manymanycommands.files.groups.GroupsFile
import fr.roumoulou.manymanycommands.files.groups.MembershipsDataFile
import fr.roumoulou.manymanycommands.files.homes.HomesDataFile
import fr.roumoulou.manymanycommands.files.homes.HomesRulesFile
import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.files.players.PlayersDataFile
import fr.roumoulou.manymanycommands.files.settings.GlobalSettingsFile
import fr.roumoulou.manymanycommands.files.tpa.TpaDataFile
import fr.roumoulou.manymanycommands.files.tpa.TpaRulesFile
import fr.roumoulou.manymanycommands.files.warps.WarpsDataFile
import fr.roumoulou.manymanycommands.files.warps.WarpsRulesFile
import fr.roumoulou.manymanycommands.files.wild.WildDataFile
import fr.roumoulou.manymanycommands.files.wild.WildRulesFile
import fr.roumoulou.manymanycommands.game.message.Messenger
import fr.roumoulou.manymanycommands.game.permission.InternalPermissionSource
import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.game.permission.Rights
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.TeleportEngine
import fr.roumoulou.manymanycommands.rules.back.BackData
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.back.BackSource
import fr.roumoulou.manymanycommands.rules.back.ReturnPositions
import fr.roumoulou.manymanycommands.rules.back.ReturnRules
import fr.roumoulou.manymanycommands.rules.data.DataFileQuotas
import fr.roumoulou.manymanycommands.rules.data.NoHoldings
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.ModFile
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.groups.Groups
import fr.roumoulou.manymanycommands.rules.groups.GroupsReport
import fr.roumoulou.manymanycommands.rules.groups.Memberships
import fr.roumoulou.manymanycommands.rules.homes.HomesData
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.mod.FileSetting
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import fr.roumoulou.manymanycommands.rules.mod.ModReports
import fr.roumoulou.manymanycommands.rules.permission.RuleSetNode
import fr.roumoulou.manymanycommands.rules.players.PlayerLookup
import fr.roumoulou.manymanycommands.rules.players.PlayerRef
import fr.roumoulou.manymanycommands.rules.players.PlayerRegistry
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import fr.roumoulou.manymanycommands.rules.teleport.QuotaLedger
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsages
import fr.roumoulou.manymanycommands.rules.tpa.TpaData
import fr.roumoulou.manymanycommands.rules.tpa.TpaHoldings
import fr.roumoulou.manymanycommands.rules.tpa.TpaMessages
import fr.roumoulou.manymanycommands.rules.tpa.TpaRequests
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsData
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.TimedWindows
import fr.roumoulou.manymanycommands.rules.wild.WildData
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

/**
 * L'état du mod pour un serveur : ses fichiers de réglages, groupes compris, ses fichiers de données, ceux du serveur et ceux de son monde, ses
 * textes, l'envoi de ses messages, ses droits, son horloge et ses téléportations.
 *
 * Il s'ouvre sur le dossier qu'on lui donne et ne lit aucun chemin de lui-même : le point d'entrée lui donne le dossier du serveur, un test le
 * sien. Le dossier du monde lui arrive de même, au démarrage du serveur ([openWorld]). Chaque commande reçoit son `ModRuntime` à
 * l'enregistrement.
 *
 * @property clock l'heure réelle, pour tout ce qui mesure un temps. Une règle ne la lit jamais : elle reçoit l'instant, que le code de la couche
 * `game` prend ici. Un test y met une horloge qu'il tient, parce que le serveur GameTest tique sans cadence.
 */
class ModRuntime private constructor(val version: String, configDirectory: Path, permissionSource: PermissionSource?, val clock: Clock) {

    companion object {

        private val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

        /**
         * Ouvre l'état du mod sur [configDirectory], le dossier du mod dans celui des configurations, et y lit les fichiers de réglages : un
         * fichier qui manque est créé, un fichier invalide laisse ses réglages livrés en place et se dit en erreur au journal.
         *
         * @param version la version du mod, celle que l'état affiche
         * @param permissionSource la source des droits, ou `null` pour celle que choisit `permission-source` du démarrage : le système du mod
         * sous `internal`, et une source muette sous `external`, tant que le module de permissions de la Fabric API n'est pas branché
         * @param clock l'horloge de l'état, celle de la machine par défaut
         */
        fun start(configDirectory: Path, version: String, permissionSource: PermissionSource? = null, clock: Clock = Clock.systemUTC()): ModRuntime =
            ModRuntime(version, configDirectory, permissionSource, clock)
    }

    // Les trois fichiers précèdent settingsFiles, qui les lit à son initialisation : l'ordre de déclaration est celui de l'initialisation.
    private val globalSettingsFile: SettingsFile<GlobalSettings> = GlobalSettingsFile.at(configDirectory)

    private val groupsFile: SettingsFile<Groups> = GroupsFile.at(configDirectory)

    /** Le fichier de règles de chaque famille, par sa table. */
    private val rulesFiles: Map<RulesTable, SettingsFile<FamilyRules>> = linkedMapOf(
        HomesRules to HomesRulesFile.at(configDirectory),
        WarpsRules to WarpsRulesFile.at(configDirectory),
        BackRules to BackRulesFile.at(configDirectory),
        WildRules to WildRulesFile.at(configDirectory),
        TpaRules to TpaRulesFile.at(configDirectory),
    )

    /** Les fichiers de réglages, dans l'ordre où l'état et le rechargement les donnent : `settings.json`, `groups.json`, puis les fichiers de règles. */
    val settingsFiles: List<SettingsFile<*>> = listOf(globalSettingsFile, groupsFile) + rulesFiles.values

    /** Les fichiers de données du serveur, dans `data\` du dossier du mod (cahier des charges, chapitre 2.7.1) : ouverts avec l'état, parce que leur dossier est connu avant le monde. */
    val serverData = ServerData(configDirectory)

    /** Les fichiers de données du monde, `null` tant qu'aucun monde n'est ouvert : le jeu enregistre les commandes avant de connaître son dossier. */
    var worldData: WorldData? = null
        private set

    /** Tous les fichiers du mod, dans l'ordre où l'état et le rechargement les donnent : les réglages, les données du serveur, puis les données du monde. */
    val files: List<ModFile> get() = settingsFiles + serverData.files + worldData?.files.orEmpty()

    val messenger = Messenger(TextCatalog.load(), language = { settings.language })

    /** Le contrôle des droits, sur la source qu'on a donnée, ou sur le système du mod, qui lit l'état au moment du verdict. */
    val rights = Rights(permissionSource ?: InternalPermissionSource(active = { usesInternalRights }, groups = { groups }, memberships = { serverData.memberships.content }))

    /** Le déroulé des téléportations : celles en attente et les cooldowns, en mémoire, et les plages de quota, conservées dans les fichiers de données du monde. */
    val teleports = TeleportEngine(messenger, rights, clock, WorldQuotas())

    /** Les positions de retour (`back.md` des specs, chapitre 2), en mémoire : elles s'effacent avec l'état, au redémarrage du serveur. */
    val returns = ReturnPositions()

    /** Les créneaux du tirage commun de `/wild timed` (`wild.md` des specs, chapitre 4), un par dimension, en mémoire : ils s'effacent de même. */
    val wildWindows = TimedWindows()

    /** Les demandes de téléportation en attente et l'intervalle entre deux demandes (`tpa.md` des specs, chapitres 2 et 4), en mémoire : ils s'effacent de même. */
    val requests = TpaRequests()

    /**
     * Les réglages globaux lus à l'ouverture. Les commandes s'enregistrent sur eux, et ceux qui ne prennent effet qu'au redémarrage du serveur
     * gardent leur valeur d'ici.
     */
    val startupSettings: GlobalSettings

    /** Les règles de chaque famille lues à l'ouverture, pour la même raison. */
    private val rulesAtStartup: Map<RulesTable, FamilyRules>

    /** Les réglages globaux en vigueur, que le rechargement remplace. */
    val settings: GlobalSettings get() = globalSettingsFile.inForce

    /** Les groupes en vigueur (`groups.md` des specs), que le rechargement remplace. */
    val groups: Groups get() = groupsFile.inForce

    /**
     * Le système du mod est la source des droits (cahier des charges, chapitre 2.4.5) : `permission-source` vaut `internal` au démarrage. Sous
     * `external`, les groupes et les permissions propres sont ignorés, et les commandes `group` et `permission` le disent.
     */
    val usesInternalRights: Boolean get() = startupSettings.permissionSource == PermissionSourceSetting.INTERNAL

    /** Les réglages livrés tiennent la place d'un fichier refusé au démarrage, qu'aucun rechargement n'a appliqué depuis. */
    val hasShippedSettingsInPlace: Boolean get() = settingsFiles.any { it.isShippedInPlace }

    init {
        settingsFiles.forEach { it.load() }
        startupSettings = settings
        rulesAtStartup = rulesFiles.mapValues { (_, file) -> file.inForce }
        invalidSettingsReport().forEach(::logError)
        serverData.files.filter { !it.isValid }.flatMap(ModReports::refusal).forEach(::logError)
    }

    /** Les règles en vigueur de la famille de [table], que le rechargement remplace. */
    fun rules(table: RulesTable): FamilyRules = rulesFiles.getValue(table).inForce

    /** Les règles de la famille de [table] lues à l'ouverture : son activation et celle de ses alias gardent leur valeur d'ici. */
    fun startupRules(table: RulesTable): FamilyRules = rulesAtStartup.getValue(table)

    /** Le jeu de règles de [source] dans la famille de [table] (cahier des charges, chapitre 2.4.3), pris dans les règles en vigueur. */
    fun ruleSet(source: CommandSourceStack, table: RulesTable): RuleValues = table.setFor(rules(table)) { rights.has(source, it) }

    /**
     * Ouvre les fichiers de données dans [worldDirectory], le dossier du mod dans celui du monde, et les lit : un fichier qui manque est créé
     * vide, un fichier invalide rend sa famille indisponible et se dit en erreur au journal, avec ses constats.
     */
    fun openWorld(worldDirectory: Path) {
        val data = WorldData(worldDirectory)
        worldData = data
        data.files.filter { !it.isValid }.flatMap(ModReports::refusal).forEach(::logError)
    }

    /**
     * À la connexion de [player] sous [name] (cahier des charges, chapitre 2.7.2) : le registre le note, à l'instant de l'état, et l'écrit tout de
     * suite. Un registre invalide ne s'écrit pas.
     */
    fun recordJoin(player: UUID, name: String) {
        val registry = serverData.players.content ?: return
        serverData.players.update(registry.joined(player, name, clock.instant()))
    }

    /**
     * Le joueur que [name] désigne dans une commande (cahier des charges, chapitre 2.7.2) : le joueur connecté qui porte ce nom, sinon celui du
     * registre dont c'est le dernier nom connu, `null` quand personne ne le porte.
     */
    fun resolvePlayer(server: MinecraftServer, name: String): PlayerRef? = PlayerLookup.resolve(name, server.playerList.players.map { PlayerRef(it.uuid, it.scoreboardName) }, serverData.players.content)

    /** Le jeu que nomme [ruleSet] existe dans les règles en vigueur de sa famille. */
    fun hasRuleSet(ruleSet: RuleSetNode): Boolean = ruleSet.set in ruleSetsOf(ruleSet.family)

    /** Les noms des nœuds des jeux de règles en vigueur, famille par famille, dans l'ordre des fichiers : ce que `<node>` propose en plus des nœuds fixes. */
    fun ruleSetNodeNames(): List<String> = rulesFiles.flatMap { (table, file) -> file.inForce.sets.keys.map { table.setNode(it).name } }

    /** Les jeux de règles en vigueur de la famille [family], vide pour une famille sans fichier de règles. */
    private fun ruleSetsOf(family: String): Set<String> = rulesFiles.entries.firstOrNull { it.key.family == family }?.value?.inForce?.sets?.keys.orEmpty()

    /**
     * L'endroit [from] que [player] vient de quitter devient sa position de retour, si [source] l'enregistre dans son jeu de règles du retour
     * (`back.md` des specs, chapitre 2). Le jeu est celui du joueur lui-même, et non celui de la source d'une commande.
     */
    fun recordReturn(player: ServerPlayer, source: BackSource, from: Location) {
        if (ReturnRules.records(ruleSet(player.createCommandSourceStack(), BackRules), source)) returns.record(player.uuid, from.toPosition())
    }

    /**
     * À chaque tick du serveur (`tpa.md` des specs, chapitre 2) : les demandes de téléportation d'un joueur qui n'est plus connecté tombent, et
     * l'autre joueur de chacune l'apprend ; puis les demandes expirées tombent, et leurs deux joueurs l'apprennent. La déconnexion se constate
     * ici, comme le moteur la constate pour une téléportation en attente, et non par un événement du jeu.
     */
    fun tickRequests(server: MinecraftServer) {
        val players = server.playerList
        requests.pending.forEach { request ->
            val gone = listOf(request.requester, request.recipient).firstOrNull { players.getPlayer(it) == null } ?: return@forEach
            requests.drop(gone)
            players.getPlayer(request.other(gone))?.let { messenger.tell(it, TpaMessages.DISCONNECTED.with(request.nameOf(gone))) }
        }
        requests.expire(clock.instant()).forEach { request ->
            players.getPlayer(request.requester)?.let { messenger.tell(it, TpaMessages.EXPIRED_REQUESTER.with(request.recipientName)) }
            players.getPlayer(request.recipient)?.let { messenger.tell(it, TpaMessages.EXPIRED_RECIPIENT.with(request.requesterName)) }
        }
    }

    /** La réponse de `/manymanycommands` : la version, la source des droits, chaque fichier, puis ce qui manque aux groupes. */
    fun status(): List<Message> = ModReports.status(version, startupSettings.permissionSource, files) + groupsReport()

    /** Relit tous les fichiers, et rend la réponse de `/manymanycommands reload`, ce qui manque aux groupes en dernier. Pour les données, le fichier du disque fait foi. */
    fun reload(): List<Message> {
        files.forEach { it.load() }
        val awaitingRestartInRules = rulesFiles.flatMap { (table, file) -> table.awaitingRestart(file.inForce, startupRules(table)).map { FileSetting(file.name, it) } }
        return ModReports.reload(files, settings.awaitingRestart(startupSettings), awaitingRestartInRules) + groupsReport()
    }

    /** Les groupes absents des réglages où des joueurs restent inscrits, et les jeux nommés absents des règles de leur famille (`groups.md` des specs, chapitre 6). */
    private fun groupsReport(): List<Message> = GroupsReport.lines(groups, serverData.memberships.content, ::ruleSetsOf)

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

    /** Le journal du serveur ne nomme pas ses loggers : chaque ligne dit de quel mod elle vient. */
    private fun logError(message: Message) = LOGGER.error("[ManyManyCommands] {}", messenger.forJournal(message))

    /** Les fichiers de données du serveur (cahier des charges, chapitre 2.7.1), lus à l'ouverture de l'état : ce qui appartient au joueur sur le serveur, et survit à un changement de monde. */
    class ServerData internal constructor(directory: Path) {

        /** `data\players.json` : le registre des joueurs. */
        val players: DataFile<PlayerRegistry> = PlayersDataFile.at(directory)

        /** `data\memberships.json` : l'inscription et les permissions propres de chaque joueur. */
        val memberships: DataFile<Memberships> = MembershipsDataFile.at(directory)

        /** Les fichiers de données du serveur, dans l'ordre où l'état et le rechargement les donnent, celui du cahier des charges. */
        val files: List<DataFile<*>> = listOf(players, memberships)

        init {
            files.forEach { it.load() }
        }
    }

    /** Les fichiers de données d'un monde (cahier des charges, chapitre 2.7.1) : chaque famille y tient les siens, lus à l'ouverture. */
    class WorldData internal constructor(directory: Path) {

        /** `homes.json` : les homes et la plage de quota de chaque joueur. */
        val homes: DataFile<HomesData> = HomesDataFile.at(directory)

        /** `warps.json` : les warps du serveur, et la plage de quota de chaque joueur. */
        val warps: DataFile<WarpsData> = WarpsDataFile.at(directory)

        /** `back.json` : la plage de quota de chaque joueur, et rien d'autre. */
        val back: DataFile<BackData> = BackDataFile.at(directory)

        /** `wild.json` : la plage de quota de chaque joueur, et rien d'autre. */
        val wild: DataFile<WildData> = WildDataFile.at(directory)

        /** `tpa.json` : la réception des demandes et la plage de quota de chaque joueur. */
        val tpa: DataFile<TpaData> = TpaDataFile.at(directory)

        /** Les fichiers de données, dans l'ordre où l'état et le rechargement les donnent, celui du cahier des charges. */
        val files: List<DataFile<*>> = listOf(homes, warps, back, wild, tpa)

        /** Les plages de quota conservées dans un fichier de données, par famille : celles des familles qui ont le leur. */
        val quotas: Map<String, DataFileQuotas<*>> = mapOf(
            HomesRules.family to DataFileQuotas.ofFamily(homes, PlayerHomes()),
            WarpsRules.family to DataFileQuotas(warps, { data, player -> data.players[player]?.quota }, { data, player, name, usage -> data.copy(players = data.players.withQuota(player, name, usage, NoHoldings)) }),
            BackRules.family to DataFileQuotas.ofFamily(back, NoHoldings),
            WildRules.family to DataFileQuotas.ofFamily(wild, NoHoldings),
            TpaRules.family to DataFileQuotas.ofFamily(tpa, TpaHoldings()),
        )

        init {
            files.forEach { it.load() }
        }
    }

    /**
     * Les plages de quota des joueurs (cahier des charges, chapitre 2.5.9) : dans le fichier de données de la famille quand le monde en tient un,
     * où elles survivent au redémarrage du serveur, et en mémoire sinon.
     */
    private inner class WorldQuotas : QuotaLedger {

        private val memory = QuotaUsages()

        override fun usage(player: UUID, family: String): QuotaUsage? {
            val stored = worldData?.quotas?.get(family) ?: return memory.usage(player, family)
            return stored.usage(player)
        }

        override fun record(player: UUID, playerName: String, family: String, usage: QuotaUsage) {
            val stored = worldData?.quotas?.get(family) ?: return memory.record(player, playerName, family, usage)
            stored.record(player, playerName, usage)
        }
    }
}
