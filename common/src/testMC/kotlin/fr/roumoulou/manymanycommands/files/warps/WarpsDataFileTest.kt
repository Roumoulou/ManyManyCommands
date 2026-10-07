// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.warps

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.data.NoHoldings
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyData
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import fr.roumoulou.manymanycommands.rules.warps.Warp
import fr.roumoulou.manymanycommands.rules.warps.Warps
import fr.roumoulou.manymanycommands.rules.warps.WarpsData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * `warps.json` sur un vrai fichier (cahier des charges, chapitres 2.7.1, 2.7.3 et 2.7.5) : sa création, sa forme, les warps avant les joueurs,
 * l'écriture à chaque changement, chaque constat avec son chemin et sa ligne, le fichier fautif jamais réécrit.
 */
class WarpsDataFileTest {

    private companion object {

        const val STEVE_ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5"

        /** Un warp ouvert, un warp réservé, et la plage d'un joueur, sous la forme du cahier des charges. */
        val EXAMPLE = """
            {
              "schema-version": 1,
              "warps": {
                "spawn": { "dimension": "minecraft:overworld", "x": 12.5, "y": 64.0, "z": -3.25, "yaw": 90.0, "pitch": 0.0 },
                "staff": { "dimension": "minecraft:the_nether", "x": 0.5, "y": 70.0, "z": 0.5, "yaw": 0.0, "pitch": 0.0, "key": "staff" }
              },
              "players": {
                "$STEVE_ID": {
                  "name": "Steve",
                  "quota": { "clock": "real", "window-start": 1759665600, "uses": 2 }
                }
              }
            }
        """.trimIndent()
    }

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("warps.json")

    private val steve = UUID.fromString(STEVE_ID)

    private val spawn = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val staff = Position("minecraft:the_nether", 0.5, 70.0, 0.5, 0f, 0f)

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 2)

    /** Ce que [EXAMPLE] donne. */
    private val example = WarpsData(Warps(mapOf("spawn" to Warp(spawn), "staff" to Warp(staff, "staff"))), QuotaOnlyData(mapOf(steve to PlayerEntry("Steve", NoHoldings, window))))

    private val unknown = CommonMessages.FINDING_UNKNOWN_KEY.with()

    private fun read(json: String): FileReading<WarpsData> {
        path.parent.createDirectories()
        path.writeText(json)
        return WarpsDataFile.read(path)
    }

    private fun data(json: String): WarpsData {
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

    // ── La création ──

    @Test
    fun `le fichier qui manque est créé vide, dans le dossier du mod du monde, les warps avant les joueurs`() {
        val file = WarpsDataFile.at(directory)

        val reading = file.load()

        assertEquals("world/warps.json", file.name)
        assertEquals(FileReading.Applied(WarpsData()), reading)
        assertTrue(file.isAvailable)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "warps": {}, "players": {} }"""), json())
        assertEquals(listOf("schema-version", "warps", "players"), json().keys.toList())
    }

    // ── La lecture ──

    @Test
    fun `l'exemple s'applique, le warp réservé avec sa clé, le joueur avec sa plage`() {
        assertEquals(example, data(EXAMPLE))
    }

    @Test
    fun `un warp sans clé est ouvert à tous, et une dimension abrégée prend son identifiant complet`() {
        val data = data("""{ "warps": { "mine": { "dimension": "the_nether", "x": 0, "y": 70, "z": 0, "yaw": 0, "pitch": 0 } } }""")

        assertEquals(Warps(mapOf("mine" to Warp(Position("minecraft:the_nether", 0.0, 70.0, 0.0, 0f, 0f)))), data.warps)
        assertEquals(QuotaOnlyData(), data.players)
    }

    // ── L'écriture ──

    @Test
    fun `un changement s'écrit tout de suite, sous la forme du cahier des charges, la clé d'un warp après sa position`() {
        val file = WarpsDataFile.at(directory)
        file.load()

        file.update(example)

        assertEquals(jsonObjectOf(EXAMPLE), json())
        val warps = json().getValue("warps").jsonObject
        assertEquals(listOf("dimension", "x", "y", "z", "yaw", "pitch", "key"), warps.getValue("staff").jsonObject.keys.toList())
        assertFalse("key" in warps.getValue("spawn").jsonObject, "An open warp writes no key")
    }

    @Test
    fun `ce qui est écrit se relit tel quel`() {
        val file = WarpsDataFile.at(directory)
        file.load()
        val data = WarpsData(Warps(mapOf("Base" to Warp(Position("mymod:moon", -1.5, 2.0, 3.0, -45f, 10f), "vip"))), QuotaOnlyData(mapOf(steve to PlayerEntry("Steve", NoHoldings, QuotaUsage(QuotaClock.PLAYTIME, 1_200, 1)))))

        file.update(data)

        assertEquals(FileReading.Applied(data), WarpsDataFile.read(path))
        assertEquals(FileReading.Applied(data), WarpsDataFile.at(directory).load())
    }

    // ── La structure et les règles : chaque constat à son chemin et à sa ligne ──

    @Test
    fun `une clé inconnue refuse le fichier, dans un warp comme dans un joueur, à son chemin et à sa ligne`() {
        val findings = refusal(
            """
            {
              "warps": {
                "base": {
                  "dimension": "minecraft:overworld",
                  "x": 1.0,
                  "y": 2.0,
                  "z": 3.0,
                  "yaw": 0.0,
                  "pitch": 0.0,
                  "alpha": 1
                }
              },
              "players": {
                "$STEVE_ID": {
                  "homes": {}
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(FileFinding("warps[base].alpha", 10, unknown), FileFinding("players[$STEVE_ID].homes", 15, unknown)), findings)
    }

    @Test
    fun `un nom de warp, une clé, une position incomplète et un joueur fautifs se constatent à leur chemin et à leur ligne, les warps d'abord`() {
        val findings = refusal(
            """
            {
              "warps": {
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
                  "pitch": 0.0,
                  "key": "Vip"
                }
              },
              "players": {
                "Steve": {
                  "quota": { "clock": "real", "window-start": 10, "uses": -1 }
                }
              }
            }
            """.trimIndent(),
        )

        val expected = listOf(
            FileFinding("warps[ma maison]", 3, CommonMessages.FINDING_NOT_ALLOWED.with("ma maison", CommonMessages.ALLOWED_NAME.with())),
            FileFinding("warps[base].dimension", 12, CommonMessages.FINDING_NOT_ALLOWED.with("The Nether", CommonMessages.ALLOWED_DIMENSION.with())),
            FileFinding("warps[base]", 11, CommonMessages.FINDING_MISSING_KEY.with("x")),
            FileFinding("warps[base].key", 17, CommonMessages.FINDING_NOT_ALLOWED.with("Vip", CommonMessages.ALLOWED_LOWERCASE_NAME.with())),
            FileFinding("players[Steve]", 21, CommonMessages.FINDING_NOT_ALLOWED.with("Steve", CommonMessages.ALLOWED_PLAYER_ID.with())),
            FileFinding("players[Steve].quota.uses", 22, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L))),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `un fichier plus récent que le mod est refusé`() {
        assertEquals(FileFinding("schema-version", 2, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1)), refusal("{\n  \"schema-version\": 2\n}").single())
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et rend les warps indisponibles`() {
        val faulty = "{\n  \"warps\": { \"ma maison\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        repeat(2) { WarpsDataFile.read(path) }
        val file = WarpsDataFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertFalse(file.isAvailable)
        assertFalse(file.isValid)
    }
}
