// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.tpa

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import fr.roumoulou.manymanycommands.rules.tpa.TpaData
import fr.roumoulou.manymanycommands.rules.tpa.TpaHoldings
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
 * `tpa.json` sur un vrai fichier (cahier des charges, chapitre 2.7.5) : sa création, sa forme, la réception fermée et la plage écrites à chaque
 * changement, ses constats, le fichier fautif jamais réécrit.
 */
class TpaDataFileTest {

    private companion object {

        const val STEVE_ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
    }

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("tpa.json")

    private val steve = UUID.fromString(STEVE_ID)

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 2)

    private fun write(json: String) {
        path.parent.createDirectories()
        path.writeText(json)
    }

    private fun refusal(json: String): List<FileFinding> {
        write(json)
        val reading = TpaDataFile.read(path)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    private fun json(): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

    private fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `le fichier qui manque est créé vide, sous son nom`() {
        val file = TpaDataFile.at(directory)

        assertEquals(FileReading.Applied(TpaData()), file.load())
        assertEquals("world/tpa.json", file.name)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json())
    }

    @Test
    fun `la réception fermée et la plage s'écrivent tout de suite, avec le nom du joueur, et se relisent telles quelles`() {
        val file = TpaDataFile.at(directory)
        file.load()
        val data = TpaData().with(steve, PlayerEntry("Steve", TpaHoldings(requestsClosed = true), window))

        file.update(data)

        val expected = """{ "schema-version": 1, "players": { "$STEVE_ID": { "name": "Steve", "requests-closed": true, "quota": { "clock": "real", "window-start": 1759665600, "uses": 2 } } } }"""
        assertEquals(jsonObjectOf(expected), json())
        assertEquals(FileReading.Applied(data), TpaDataFile.read(path))
        file.update(TpaData().with(steve, PlayerEntry("Steve", TpaHoldings(requestsClosed = true))))
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": { "$STEVE_ID": { "name": "Steve", "requests-closed": true } } }"""), json(), "A closed reception is written without a window")
        file.update(TpaData().with(steve, PlayerEntry("Steve", TpaHoldings())))
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json(), "An open reception without a window is not written")
    }

    @Test
    fun `requests-closed à false se lit ouverte, et ne se récrit pas`() {
        write("""{ "players": { "$STEVE_ID": { "name": "Steve", "requests-closed": false, "quota": { "clock": "real", "window-start": 1759665600, "uses": 2 } } } }""")
        val file = TpaDataFile.at(directory)

        val reading = file.load()

        assertEquals(FileReading.Applied(TpaData(mapOf(steve to PlayerEntry("Steve", TpaHoldings(), window)))), reading)
        file.update(file.content ?: error("The file is available"))
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": { "$STEVE_ID": { "name": "Steve", "quota": { "clock": "real", "window-start": 1759665600, "uses": 2 } } } }"""), json())
    }

    @Test
    fun `le fichier ne connaît que le nom, la réception et la plage, et juge la nature de la réception et la plage`() {
        val unknown = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"homes\": {}\n    }\n  }\n}")
        val text = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"requests-closed\": \"oui\"\n    }\n  }\n}")
        val negative = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"quota\": {\n        \"clock\": \"real\",\n        \"window-start\": 10,\n        \"uses\": -1\n      }\n    }\n  }\n}")

        assertEquals(listOf(FileFinding("players[$STEVE_ID].homes", 4, CommonMessages.FINDING_UNKNOWN_KEY.with())), unknown)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].requests-closed", 4, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_BOOLEAN.with()))), text)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].quota.uses", 7, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L)))), negative)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et rend les demandes indisponibles`() {
        val faulty = "{\n  \"players\": { \"Steve\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        val file = TpaDataFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertFalse(file.isAvailable)
    }
}
