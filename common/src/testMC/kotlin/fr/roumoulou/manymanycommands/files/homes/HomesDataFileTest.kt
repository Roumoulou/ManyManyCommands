// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.homes

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.homes.HomesData
import fr.roumoulou.manymanycommands.rules.homes.PlayerHomes
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * `homes.json` sur un vrai fichier (cahier des charges, chapitres 2.7.1, 2.7.3 et 2.7.5) : sa création, sa forme, l'écriture à chaque
 * changement, chaque constat avec son chemin et sa ligne, le fichier fautif jamais réécrit.
 */
class HomesDataFileTest {

    private companion object {

        const val STEVE_ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
        const val ALEX_ID = "7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e"

        /** L'exemple du cahier des charges, chapitre 2.7.5. */
        val EXAMPLE = """
            {
              "schema-version": 1,
              "players": {
                "$STEVE_ID": {
                  "name": "Steve",
                  "default-home": "base",
                  "homes": {
                    "base": { "dimension": "minecraft:overworld", "x": 12.5, "y": 64.0, "z": -3.25, "yaw": 90.0, "pitch": 0.0 }
                  },
                  "quota": { "clock": "real", "window-start": 1759665600, "uses": 2 }
                }
              }
            }
        """.trimIndent()
    }

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("homes.json")

    private val steve = UUID.fromString(STEVE_ID)

    private val base = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 2)

    private val unknown = CommonMessages.FINDING_UNKNOWN_KEY.with()

    private fun read(json: String): FileReading<HomesData> {
        path.parent.createDirectories()
        path.writeText(json)
        return HomesDataFile.read(path)
    }

    private fun data(json: String): HomesData {
        val reading = read(json)
        assertTrue(reading is FileReading.Applied, "The file must apply, was $reading")
        return (reading as FileReading.Applied).content
    }

    private fun refusal(json: String): List<FileFinding> {
        val reading = read(json)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    private fun json(): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

    private fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun wrongNature(nature: fr.roumoulou.manymanycommands.rules.message.Fragment) = CommonMessages.FINDING_WRONG_NATURE.with(nature.with())

    // ── La création ──

    @Test
    fun `le fichier qui manque est créé vide, dans le dossier du mod du monde`() {
        val file = HomesDataFile.at(directory)

        val reading = file.load()

        assertEquals("world/homes.json", file.name)
        assertEquals(FileReading.Applied(HomesData()), reading)
        assertTrue(file.isAvailable)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json())
    }

    @Test
    fun `le dossier du monde est créé avec lui, espaces et accents compris`() {
        val nested = directory.resolve("monde de l'été").resolve("many many commands")

        assertEquals(FileReading.Applied(HomesData()), HomesDataFile.at(nested).load())
        assertTrue(nested.resolve("homes.json").exists())
    }

    // ── La lecture ──

    @Test
    fun `l'exemple du cahier des charges s'applique`() {
        val data = data(EXAMPLE)

        assertEquals(PlayerEntry("Steve", PlayerHomes(mapOf("base" to base), designated = "base"), window), data[steve])
    }

    @Test
    fun `un joueur sans homes écrits n'en a aucun, sans home désigné ni plage, et sans dimension abrégée`() {
        val data = data("""{ "players": { "$STEVE_ID": { "name": "Steve" }, "$ALEX_ID": { "homes": { "mine": { "dimension": "the_nether", "x": 0, "y": 70, "z": 0, "yaw": 0, "pitch": 0 } } } } }""")

        assertEquals(PlayerEntry("Steve", PlayerHomes()), data[steve])
        assertEquals(PlayerEntry(null, PlayerHomes(mapOf("mine" to Position("minecraft:the_nether", 0.0, 70.0, 0.0, 0f, 0f)))), data[UUID.fromString(ALEX_ID)])
    }

    @Test
    fun `un fichier valide n'est pas réécrit par sa lecture, sa mise en forme reste celle de l'administrateur`() {
        val handwritten = "{\n\t\"players\":{},\n\n\t\"schema-version\":   1\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(handwritten)

        HomesDataFile.at(directory).load()

        assertArrayEquals(handwritten, path.readBytes())
    }

    // ── L'écriture ──

    @Test
    fun `un changement s'écrit tout de suite, sous la forme du cahier des charges`() {
        val file = HomesDataFile.at(directory)
        file.load()

        file.update(HomesData().with(steve, PlayerEntry("Steve", PlayerHomes(mapOf("base" to base), designated = "base"), window)))

        assertEquals(jsonObjectOf(EXAMPLE), json())
        assertEquals(listOf("name", "default-home", "homes", "quota"), json().getValue("players").jsonObject.getValue(STEVE_ID).jsonObject.keys.toList())
    }

    @Test
    fun `une clé dont la valeur manque ne s'écrit pas`() {
        val file = HomesDataFile.at(directory)
        file.load()

        file.update(HomesData().with(steve, PlayerEntry("Steve", PlayerHomes(mapOf("base" to base)))))

        assertEquals(listOf("name", "homes"), json().getValue("players").jsonObject.getValue(STEVE_ID).jsonObject.keys.toList())
    }

    @Test
    fun `une entrée qui ne porte que son nom ne s'écrit pas`() {
        val file = HomesDataFile.at(directory)
        file.load()
        file.update(HomesData().with(steve, PlayerEntry("Steve", PlayerHomes(mapOf("base" to base)))))

        file.update(HomesData().with(steve, PlayerEntry("Steve", PlayerHomes())))

        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json())
    }

    @Test
    fun `ce qui est écrit se relit tel quel`() {
        val file = HomesDataFile.at(directory)
        file.load()
        val data = HomesData().with(steve, PlayerEntry("Steve", PlayerHomes(mapOf("base" to base, "Mine" to Position("mymod:moon", -1.5, 2.0, 3.0, -45f, 10f)), designated = "Mine"), QuotaUsage(QuotaClock.PLAYTIME, 1_200, 1)))

        file.update(data)

        assertEquals(FileReading.Applied(data), HomesDataFile.read(path))
        assertEquals(FileReading.Applied(data), HomesDataFile.at(directory).load())
    }

    // ── La structure : la clé inconnue, la valeur d'une autre nature ──

    @Test
    fun `une clé inconnue refuse le fichier, à tous les niveaux, à son chemin et à sa ligne`() {
        val findings = refusal(
            """
            {
              "players": {
                "$STEVE_ID": {
                  "home": {},
                  "homes": {
                    "base": {
                      "dimension": "minecraft:overworld",
                      "x": 1.0,
                      "y": 2.0,
                      "z": 3.0,
                      "yaw": 0.0,
                      "pitch": 0.0,
                      "alpha": 1
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(FileFinding("players[$STEVE_ID].home", 4, unknown), FileFinding("players[$STEVE_ID].homes[base].alpha", 13, unknown)), findings)
    }

    @Test
    fun `une valeur d'une autre nature refuse le fichier, avec la nature attendue`() {
        val findings = refusal(
            """
            {
              "schema-version": "1",
              "players": {
                "$STEVE_ID": {
                  "homes": null,
                  "quota": {
                    "clock": "real",
                    "window-start": 10,
                    "uses": 2.5
                  }
                },
                "$ALEX_ID": {
                  "homes": {
                    "base": {
                      "dimension": 7,
                      "x": "12"
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val expected = listOf(
            FileFinding("schema-version", 2, wrongNature(CommonMessages.NATURE_INTEGER)),
            FileFinding("players[$STEVE_ID].homes", 5, wrongNature(CommonMessages.NATURE_OBJECT)),
            FileFinding("players[$STEVE_ID].quota.uses", 9, wrongNature(CommonMessages.NATURE_INTEGER)),
            FileFinding("players[$ALEX_ID].homes[base].dimension", 15, wrongNature(CommonMessages.NATURE_TEXT)),
            FileFinding("players[$ALEX_ID].homes[base].x", 16, wrongNature(CommonMessages.NATURE_NUMBER)),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `les joueurs s'écrivent dans un objet`() {
        assertEquals(FileFinding("players", 2, wrongNature(CommonMessages.NATURE_OBJECT)), refusal("{\n  \"players\": []\n}").single())
    }

    // ── Les valeurs admises ──

    @Test
    fun `un UUID mal formé refuse le fichier, à l'entrée du joueur`() {
        val finding = refusal("{\n  \"players\": {\n    \"Steve\": {\n      \"homes\": {}\n    }\n  }\n}").single()

        assertEquals(FileFinding("players[Steve]", 3, CommonMessages.FINDING_NOT_ALLOWED.with("Steve", CommonMessages.ALLOWED_PLAYER_ID.with())), finding)
    }

    @Test
    fun `un nom de home invalide, une position fautive, un home par défaut inconnu et une plage fautive refusent le fichier, chacun à sa ligne`() {
        val findings = refusal(
            """
            {
              "players": {
                "$STEVE_ID": {
                  "default-home": "mine",
                  "homes": {
                    "ma maison": {
                      "dimension": "minecraft:overworld",
                      "x": 1.0,
                      "y": 2.0,
                      "z": 3.0,
                      "yaw": 0.0,
                      "pitch": 0.0
                    },
                    "base": {
                      "dimension": "The Nether",
                      "y": 2.0,
                      "z": 3.0,
                      "yaw": 0.0,
                      "pitch": 0.0
                    }
                  },
                  "quota": {
                    "clock": "lunar",
                    "window-start": -5,
                    "uses": -1
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val entry = "players[$STEVE_ID]"
        val atLeastZero = CommonMessages.ALLOWED_AT_LEAST.with(0L)
        val expected = listOf(
            FileFinding("$entry.homes[ma maison]", 6, CommonMessages.FINDING_NOT_ALLOWED.with("ma maison", CommonMessages.ALLOWED_NAME.with())),
            FileFinding("$entry.homes[base].dimension", 15, CommonMessages.FINDING_NOT_ALLOWED.with("The Nether", CommonMessages.ALLOWED_DIMENSION.with())),
            FileFinding("$entry.homes[base]", 14, CommonMessages.FINDING_MISSING_KEY.with("x")),
            FileFinding("$entry.default-home", 4, CommonMessages.FINDING_NOT_ALLOWED.with("mine", "ma maison, base")),
            FileFinding("$entry.quota.clock", 23, CommonMessages.FINDING_NOT_ALLOWED.with("lunar", "real, playtime")),
            FileFinding("$entry.quota.window-start", 24, CommonMessages.FINDING_NOT_ALLOWED.with(-5L, atLeastZero)),
            FileFinding("$entry.quota.uses", 25, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, atLeastZero)),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `une plage sans ses trois clés refuse le fichier, à la ligne de la plage`() {
        val finding = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"quota\": {\n        \"uses\": 2\n      }\n    }\n  }\n}")

        val entry = "players[$STEVE_ID]"
        assertEquals(listOf(FileFinding("$entry.quota", 4, CommonMessages.FINDING_MISSING_KEY.with("clock")), FileFinding("$entry.quota", 4, CommonMessages.FINDING_MISSING_KEY.with("window-start"))), finding)
    }

    @Test
    fun `un fichier plus récent que le mod est refusé`() {
        assertEquals(FileFinding("schema-version", 2, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1)), refusal("{\n  \"schema-version\": 2\n}").single())
    }

    // ── Le JSON strict ──

    @Test
    fun `un JSON mal formé et une clé déclarée deux fois refusent le fichier, par le message du lecteur`() {
        val malformed = refusal("{\n  \"players\" {}\n}").single()
        val duplicated = refusal("{\n  \"players\": {},\n  \"players\": {}\n}").single()

        assertNull(malformed.path)
        assertEquals(CommonMessages.FINDING_UNREADABLE.key, malformed.text.key)
        assertEquals(CommonMessages.FINDING_UNREADABLE.key, duplicated.text.key)
        assertEquals(3, duplicated.line)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et rend les homes indisponibles`() {
        val faulty = "{\n  \"players\": { \"Steve\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        repeat(2) { HomesDataFile.read(path) }
        val file = HomesDataFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertFalse(file.isAvailable)
        assertFalse(file.isValid)
    }
}
