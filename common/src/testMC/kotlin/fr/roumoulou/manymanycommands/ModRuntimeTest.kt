// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.homes.HomesData
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsData
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * L'état du mod ouvert sur un dossier à lui : ses six fichiers de réglages et le fichier de données de son monde, le fichier invalide au
 * démarrage et au rechargement (cahier des charges, chapitre 2.7.3), et les réglages qui attendent le redémarrage (`manymanycommands.md` des
 * specs, chapitre 2).
 */
class ModRuntimeTest {

    companion object {

        private const val VERSION = "0.0.0+test"

        /** Les six fichiers de réglages, dans l'ordre où l'état et le rechargement les donnent. */
        private val SETTINGS_FILES = listOf("settings.json", "rules/homes.json", "rules/warps.json", "rules/back.json", "rules/wild.json", "rules/tpa.json")

        /** Tous les fichiers : les réglages, puis les données du monde. */
        private val FILES = SETTINGS_FILES + listOf("world/homes.json", "world/warps.json", "world/back.json", "world/wild.json")

        private val TABLES = listOf(HomesRules, WarpsRules, BackRules, WildRules, TpaRules)

        private val STEVE: UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

        @JvmStatic
        @BeforeAll
        fun bootstrapTheGame() = GameBootstrap.ensure()
    }

    @TempDir
    lateinit var directory: Path

    private val settingsPath: Path get() = directory.resolve(GlobalSettings.FILE_NAME)

    /** Le dossier du mod dans celui du monde, et son fichier de données des homes. */
    private val worldDirectory: Path get() = directory.resolve("world").resolve("manymanycommands")

    private val homesPath: Path get() = worldDirectory.resolve("homes.json")

    /** L'état ouvert sur le dossier du test, son monde ouvert comme le fait le démarrage du serveur. */
    private fun start(): ModRuntime = ModRuntime.start(directory, VERSION).also { it.openWorld(worldDirectory) }

    private fun writeRules(family: String, json: String) {
        directory.resolve("rules").createDirectories().resolve("$family.json").writeText(json)
    }

    private fun writeHomes(json: String) {
        worldDirectory.createDirectories()
        homesPath.writeText(json)
    }

    /** La ligne de chaque fichier dans la réponse du rechargement, quand tous s'appliquent sauf [refused]. */
    private fun reloadKeys(refused: String? = null): List<String> = FILES.flatMap { if (it == refused) listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key) else listOf(ModMessages.RELOAD_FILE_APPLIED.key) }

    private fun statusOf(runtime: ModRuntime, file: String): Message = runtime.status()[2 + FILES.indexOf(file)]

    // ── L'ouverture ──

    @Test
    fun `l'ouverture crée les six fichiers de réglages et applique les réglages livrés`() {
        val runtime = start()

        SETTINGS_FILES.forEach { assertTrue(directory.resolve(it).exists(), "$it must be created") }
        assertEquals(SETTINGS_FILES, runtime.settingsFiles.map { it.name })
        assertEquals(GlobalSettings(), runtime.settings)
        assertEquals(GlobalSettings(), runtime.startupSettings)
        TABLES.forEach { table ->
            assertEquals(table.shipped, runtime.rules(table), "The rules of ${table.family}")
            assertEquals(table.shipped, runtime.startupRules(table), "The startup rules of ${table.family}")
        }
        assertFalse(runtime.hasShippedSettingsInPlace)
        assertEquals(emptyList<Any>(), runtime.invalidSettingsReport())
    }

    @Test
    fun `l'ouverture lit les fichiers déjà là`() {
        settingsPath.writeText("""{ "language": "fr", "mmc-alias": false }""")
        writeRules("homes", """{ "aliases": false, "rules": { "default": { "max-homes": 5 } } }""")

        val runtime = start()

        assertEquals(GlobalSettings(language = LanguageSetting.FRENCH, mmcAlias = false), runtime.startupSettings)
        assertEquals(false, runtime.startupRules(HomesRules).settings[HomesRules.aliases])
        assertEquals(5L, runtime.rules(HomesRules).default[HomesRules.maxHomes])
        assertEquals(WarpsRules.shipped, runtime.rules(WarpsRules))
    }

    @Test
    fun `l'état donne la version, la source des droits et les dix fichiers, les données du monde en dernier`() {
        val expected = listOf(ModMessages.STATUS_VERSION.with(VERSION), ModMessages.STATUS_SOURCE.with("internal")) + FILES.map { ModMessages.STATUS_FILE_VALID.with(it) }

        assertEquals(expected, start().status())
    }

    @Test
    fun `le rechargement donne une ligne par fichier, les dix appliqués`() {
        assertEquals(FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) }, start().reload())
    }

    // ── Le monde et ses données ──

    @Test
    fun `tant qu'aucun monde n'est ouvert, l'état ne donne que les fichiers de réglages`() {
        val runtime = ModRuntime.start(directory, VERSION)

        assertNull(runtime.worldData)
        assertEquals(SETTINGS_FILES, runtime.files.map { it.name })
        assertEquals(SETTINGS_FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) }, runtime.reload())
    }

    @Test
    fun `l'ouverture du monde crée les fichiers de données vides, dans le dossier du mod du monde, et les donne sous world`() {
        val runtime = start()

        assertTrue(homesPath.exists(), "homes.json must be created in the mod's folder of the world")
        assertTrue(worldDirectory.resolve("warps.json").exists(), "warps.json must be created in the world folder of the mod")
        assertTrue(worldDirectory.resolve("back.json").exists(), "back.json must be created in the mod's folder of the world")
        assertTrue(worldDirectory.resolve("wild.json").exists(), "wild.json must be created in the world folder of the mod")
        assertEquals(FILES, runtime.files.map { it.name })
        assertEquals(HomesData(), runtime.worldData?.homes?.content)
        assertEquals(WarpsData(), runtime.worldData?.warps?.content)
        assertEquals(true, runtime.worldData?.back?.isAvailable)
        assertEquals(true, runtime.worldData?.wild?.isAvailable)
        assertFalse(runtime.hasShippedSettingsInPlace, "A data file is not a settings file: nothing shipped stands in its place")
    }

    @Test
    fun `l'ouverture du monde lit le fichier déjà là, et un changement s'y écrit tout de suite`() {
        writeHomes("""{ "players": { "$STEVE": { "name": "Steve", "homes": { "base": { "dimension": "minecraft:overworld", "x": 1.0, "y": 2.0, "z": 3.0, "yaw": 0.0, "pitch": 0.0 } } } } }""")
        val runtime = start()
        val homes = runtime.worldData?.homes ?: error("The world must be open")
        val base = Position("minecraft:overworld", 1.0, 2.0, 3.0, 0f, 0f)
        assertEquals(PlayerHomes(mapOf("base" to base)), homes.content?.get(STEVE)?.holdings)

        homes.update(homes.content!!.with(STEVE, PlayerEntry("Steve", PlayerHomes(mapOf("base" to base, "mine" to base)))))

        assertTrue(homesPath.readText().contains("\"mine\""), "The change must be on the disk at once")
        assertEquals(homes.content, ModRuntime.start(directory, VERSION).also { it.openWorld(worldDirectory) }.worldData?.homes?.content, "Another state reads what was written")
    }

    @Test
    fun `des données invalides à l'ouverture rendent la famille indisponible, l'état le montre refusé et les réglages ne bougent pas`() {
        writeHomes("{\n  \"players\": {\n    \"Steve\": {}\n  }\n}")

        val runtime = start()

        val homes = runtime.worldData?.homes ?: error("The world must be open")
        assertFalse(homes.isAvailable)
        assertNull(homes.content)
        assertEquals(ModMessages.STATUS_FILE_REFUSED.with("world/homes.json"), statusOf(runtime, "world/homes.json"))
        assertFalse(runtime.hasShippedSettingsInPlace)
        assertEquals(emptyList<Any>(), runtime.invalidSettingsReport(), "The data file is not announced as invalid settings")
    }

    @Test
    fun `corrigées puis rechargées, les données reviennent et la famille est remise en service, parce que le disque fait foi`() {
        writeHomes("{\n  \"players\": {\n    \"Steve\": {}\n  }\n}")
        val runtime = start()

        writeHomes("""{ "players": { "$STEVE": { "homes": { "base": { "dimension": "minecraft:overworld", "x": 1.0, "y": 2.0, "z": 3.0, "yaw": 0.0, "pitch": 0.0 } } } } }""")
        val answer = runtime.reload()

        assertEquals(reloadKeys(), answer.map { it.key })
        val homes = runtime.worldData?.homes ?: error("The world must be open")
        assertTrue(homes.isAvailable)
        assertEquals(listOf("base"), homes.content?.get(STEVE)?.holdings?.homes?.keys?.toList())
        assertEquals(ModMessages.STATUS_FILE_VALID.with("world/homes.json"), statusOf(runtime, "world/homes.json"))
    }

    @Test
    fun `invalides au rechargement, les données en mémoire s'effacent et la famille devient indisponible`() {
        val runtime = start()
        val homes = runtime.worldData?.homes ?: error("The world must be open")
        homes.update(HomesData().with(STEVE, PlayerEntry("Steve", PlayerHomes(mapOf("base" to Position("minecraft:overworld", 1.0, 2.0, 3.0, 0f, 0f))))))

        writeHomes("{\n  \"players\": {\n    \"$STEVE\": {\n      \"default-home\": \"mine\"\n    }\n  }\n}")
        val answer = runtime.reload()

        assertEquals(reloadKeys(refused = "world/homes.json"), answer.map { it.key })
        assertEquals(listOf<Any>("players[$STEVE].default-home", 4), answer.first { it.key == ModMessages.RELOAD_FINDING.key }.text.arguments.take(2))
        assertFalse(homes.isAvailable, "The file on the disk prevails: the homes kept in memory are gone")
        assertEquals(ModMessages.STATUS_FILE_REFUSED.with("world/homes.json"), statusOf(runtime, "world/homes.json"))
    }

    @Test
    fun `le fichier de données supprimé renaît vide au rechargement`() {
        val runtime = start()

        homesPath.deleteExisting()
        val answer = runtime.reload()

        assertTrue(homesPath.exists())
        assertEquals(reloadKeys(), answer.map { it.key })
        assertEquals(HomesData(), runtime.worldData?.homes?.content)
        assertEquals(WarpsData(), runtime.worldData?.warps?.content)
    }

    // ── Le fichier invalide ──

    @Test
    fun `invalide au démarrage, le fichier laisse les réglages livrés en place et le journal le dit`() {
        settingsPath.writeText("{\n  \"language\": \"de\"\n}")

        val runtime = start()

        assertEquals(GlobalSettings(), runtime.settings)
        assertTrue(runtime.hasShippedSettingsInPlace)
        val report = listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key, ModMessages.STARTUP_INVALID_SETTINGS.key)
        assertEquals(report, runtime.invalidSettingsReport().map { it.key })
        assertEquals(ModMessages.STATUS_FILE_REFUSED.with(GlobalSettings.FILE_NAME), statusOf(runtime, GlobalSettings.FILE_NAME))
    }

    @Test
    fun `un fichier de règles invalide au démarrage laisse ses règles livrées, et les autres fichiers s'appliquent`() {
        writeRules("homes", "{\n  \"rules\": {\n    \"default\": {\n      \"max-homes\": -1\n    }\n  }\n}")
        writeRules("warps", """{ "rules": { "default": { "delay": 0 } } }""")

        val runtime = start()

        assertEquals(HomesRules.shipped, runtime.rules(HomesRules))
        assertEquals(0L, runtime.rules(WarpsRules).default[WarpsRules.delay])
        assertTrue(runtime.hasShippedSettingsInPlace)
        val report = listOf(ModMessages.RELOAD_FILE_REFUSED.key, ModMessages.RELOAD_FINDING.key, ModMessages.STARTUP_INVALID_SETTINGS.key)
        assertEquals(report, runtime.invalidSettingsReport().map { it.key })
        assertEquals(ModMessages.RELOAD_FILE_REFUSED.with("rules/homes.json"), runtime.invalidSettingsReport().first())
        assertEquals(ModMessages.STATUS_FILE_REFUSED.with("rules/homes.json"), statusOf(runtime, "rules/homes.json"))
        assertEquals(ModMessages.STATUS_FILE_VALID.with("rules/warps.json"), statusOf(runtime, "rules/warps.json"))
    }

    @Test
    fun `corrigé puis rechargé, le fichier s'applique et les réglages livrés s'effacent`() {
        settingsPath.writeText("{\n  \"language\": \"de\"\n}")
        val runtime = start()

        settingsPath.writeText("{\n  \"language\": \"fr\"\n}")
        val answer = runtime.reload()

        assertEquals(reloadKeys(), answer.map { it.key })
        assertEquals(LanguageSetting.FRENCH, runtime.settings.language)
        assertFalse(runtime.hasShippedSettingsInPlace)
        assertEquals(ModMessages.STATUS_FILE_VALID.with(GlobalSettings.FILE_NAME), statusOf(runtime, GlobalSettings.FILE_NAME))
    }

    @Test
    fun `invalide au rechargement, le fichier est refusé et les réglages en vigueur restent`() {
        settingsPath.writeText("""{ "language": "fr" }""")
        val runtime = start()

        settingsPath.writeText("{\n  \"language\": \"fr\",\n  \"langage\": \"en\"\n}")
        val answer = runtime.reload()

        assertEquals(reloadKeys(refused = GlobalSettings.FILE_NAME), answer.map { it.key })
        assertEquals(LanguageSetting.FRENCH, runtime.settings.language)
        assertFalse(runtime.hasShippedSettingsInPlace, "The settings in force are those of the file read at startup, not the shipped ones")
        assertEquals(ModMessages.STATUS_FILE_REFUSED.with(GlobalSettings.FILE_NAME), statusOf(runtime, GlobalSettings.FILE_NAME))
    }

    @Test
    fun `un fichier de règles invalide au rechargement est refusé, ses règles en vigueur restent et les autres fichiers s'appliquent`() {
        writeRules("wild", """{ "rules": { "default": { "cooldown": 60 } } }""")
        val runtime = start()

        writeRules("wild", "{\n  \"rules\": {\n    \"default\": {\n      \"cooldown\": \"une heure\"\n    }\n  }\n}")
        writeRules("tpa", """{ "request-lifetime": 30 }""")
        val answer = runtime.reload()

        assertEquals(reloadKeys(refused = "rules/wild.json"), answer.map { it.key })
        assertEquals(60L, runtime.rules(WildRules).default[WildRules.cooldown])
        assertEquals(30L, runtime.rules(TpaRules).settings[TpaRules.requestLifetime])
        assertFalse(runtime.hasShippedSettingsInPlace)
    }

    @Test
    fun `un fichier supprimé renaît au rechargement, avec les réglages livrés`() {
        settingsPath.writeText("""{ "language": "fr" }""")
        writeRules("back", """{ "rules": { "default": { "delay": 0 } } }""")
        val runtime = start()

        settingsPath.deleteExisting()
        directory.resolve("rules/back.json").deleteExisting()
        val answer = runtime.reload()

        assertTrue(settingsPath.exists())
        assertTrue(directory.resolve("rules/back.json").exists())
        assertEquals(reloadKeys(), answer.map { it.key })
        assertEquals(GlobalSettings(), runtime.settings)
        assertEquals(BackRules.shipped, runtime.rules(BackRules))
    }

    // ── Ce qui change à chaud, et ce qui attend le redémarrage ──

    @Test
    fun `le rechargement dit le réglage qui attend le redémarrage, et les réglages du démarrage ne bougent pas`() {
        val runtime = start()

        settingsPath.writeText("""{ "permission-source": "external", "mmc-alias": false }""")
        val answer = runtime.reload()

        val expected = FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) } + listOf(ModMessages.RELOAD_RESTART_REQUIRED.with("permission-source"), ModMessages.RELOAD_RESTART_REQUIRED.with("mmc-alias"))
        assertEquals(expected, answer)
        assertEquals(GlobalSettings(), runtime.startupSettings)
        assertEquals(PermissionSourceSetting.EXTERNAL, runtime.settings.permissionSource)
        assertEquals(ModMessages.STATUS_SOURCE.with("internal"), runtime.status()[1], "The status shows the permission source of the startup, the one in effect")
    }

    @Test
    fun `l'activation d'une famille et celle de ses alias attendent le redémarrage, chacune dite avec son fichier`() {
        val runtime = start()

        settingsPath.writeText("""{ "mmc-alias": false }""")
        writeRules("homes", """{ "enabled": false, "aliases": false }""")
        writeRules("back", """{ "enabled": false }""")
        val answer = runtime.reload()

        val expected = FILES.map { ModMessages.RELOAD_FILE_APPLIED.with(it) } + listOf(
            ModMessages.RELOAD_RESTART_REQUIRED.with("mmc-alias"),
            ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with("rules/homes.json", "enabled"),
            ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with("rules/homes.json", "aliases"),
            ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with("rules/back.json", "enabled"),
        )
        assertEquals(expected, answer)
        assertEquals(true, runtime.startupRules(HomesRules).settings[HomesRules.enabled], "The family stays enabled until the restart")
        assertEquals(false, runtime.rules(HomesRules).settings[HomesRules.enabled])
    }

    @Test
    fun `tant que le réglage attend, chaque rechargement le redit`() {
        val runtime = start()
        settingsPath.writeText("""{ "mmc-alias": false }""")
        writeRules("warps", """{ "aliases": false }""")

        runtime.reload()

        assertEquals(reloadKeys() + listOf(ModMessages.RELOAD_RESTART_REQUIRED.key, ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.key), runtime.reload().map { it.key })
    }

    @Test
    fun `un jeu de règles et un réglage du serveur rechargés prennent effet tout de suite, sans rien attendre`() {
        val runtime = start()

        writeRules("homes", """{ "rules": { "default": { "max-homes": 1 }, "vip": { "max-homes": 20 } } }""")
        writeRules("wild", """{ "timed-window": 45 }""")
        val answer = runtime.reload()

        assertEquals(reloadKeys(), answer.map { it.key })
        assertEquals(1L, runtime.rules(HomesRules).default[HomesRules.maxHomes])
        assertEquals(20L, runtime.rules(HomesRules).sets.getValue("vip")[HomesRules.maxHomes])
        assertEquals(45L, runtime.rules(WildRules).settings[WildRules.timedWindow])
        assertEquals(HomesRules.shipped, runtime.startupRules(HomesRules))
    }

    @Test
    fun `la langue rechargée prend effet tout de suite`() {
        settingsPath.writeText("""{ "language": "fr" }""")
        val runtime = start()
        assertEquals(Language.FRENCH, runtime.messenger.consoleLanguage)

        settingsPath.writeText("""{ "language": "en" }""")
        runtime.reload()

        assertEquals(Language.ENGLISH, runtime.messenger.consoleLanguage)
    }

    // ── L'horloge ──

    @Test
    fun `l'état porte l'horloge qu'on lui donne, celle de la machine par défaut`() {
        val noon = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)

        val runtime = ModRuntime.start(directory, VERSION, clock = noon)

        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), runtime.clock.instant())
        val before = Instant.now()
        assertFalse(start().clock.instant().isBefore(before), "Without a clock given, the state reads the time of the machine")
    }

    @Test
    fun `le journal reçoit le refus dans la langue de la console`() {
        settingsPath.writeText("{\n  \"language\": \"de\"\n}")
        val runtime = start()

        val lines = runtime.invalidSettingsReport().map(runtime.messenger::forJournal)

        assertEquals(3, lines.size)
        assertTrue(lines.none { it.contains("manymanycommands.mod.") || it.contains("%s") }, "Every line must be a resolved text, was $lines")
        assertTrue(lines.first().startsWith(GlobalSettings.FILE_NAME), "The first line names the file, was '${lines.first()}'")
    }
}
