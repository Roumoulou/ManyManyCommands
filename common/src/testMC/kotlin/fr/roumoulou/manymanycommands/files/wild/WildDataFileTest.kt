// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.wild

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.NoHoldings
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import fr.roumoulou.manymanycommands.rules.wild.WildData
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

/** `wild.json` sur un vrai fichier (`wild.md` des specs, chapitre 6) : sa création sous son nom, l'écriture d'une plage, ses constats, le fichier fautif jamais réécrit. */
class WildDataFileTest {

    private companion object {

        const val STEVE_ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
    }

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("wild.json")

    private val steve = UUID.fromString(STEVE_ID)

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 3)

    private fun refusal(json: String): List<FileFinding> {
        path.parent.createDirectories()
        path.writeText(json)
        val reading = WildDataFile.read(path)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    private fun json(): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

    private fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `le fichier qui manque est créé vide, sous son nom`() {
        val file = WildDataFile.at(directory)

        assertEquals(FileReading.Applied(WildData()), file.load())
        assertEquals("world/wild.json", file.name)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json())
    }

    @Test
    fun `une plage s'écrit tout de suite, avec le nom du joueur, et se relit telle quelle`() {
        val file = WildDataFile.at(directory)
        file.load()
        val data = WildData().with(steve, PlayerEntry("Steve", NoHoldings, window))

        file.update(data)

        val expected = """{ "schema-version": 1, "players": { "$STEVE_ID": { "name": "Steve", "quota": { "clock": "real", "window-start": 1759665600, "uses": 3 } } } }"""
        assertEquals(jsonObjectOf(expected), json())
        assertEquals(FileReading.Applied(data), WildDataFile.read(path))
    }

    @Test
    fun `le fichier ne connaît que le nom et la plage, et juge la plage`() {
        val unknown = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"warps\": {}\n    }\n  }\n}")
        val negative = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"quota\": {\n        \"clock\": \"real\",\n        \"window-start\": -10,\n        \"uses\": 1\n      }\n    }\n  }\n}")

        assertEquals(listOf(FileFinding("players[$STEVE_ID].warps", 4, CommonMessages.FINDING_UNKNOWN_KEY.with())), unknown)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].quota.window-start", 6, CommonMessages.FINDING_NOT_ALLOWED.with(-10L, CommonMessages.ALLOWED_AT_LEAST.with(0L)))), negative)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et rend la téléportation aléatoire indisponible`() {
        val faulty = "{\n  \"players\": { \"Steve\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        val file = WildDataFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertFalse(file.isAvailable)
    }
}
