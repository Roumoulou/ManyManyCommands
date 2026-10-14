// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.players

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.players.PlayerRecord
import fr.roumoulou.manymanycommands.rules.players.PlayerRegistry
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
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * `data\players.json` sur un vrai fichier (cahier des charges, chapitres 2.7.2 et 2.7.5) : sa création dans `data\`, sa forme, la connexion
 * écrite tout de suite, ses constats, le fichier fautif jamais réécrit.
 */
class PlayersDataFileTest {

    private companion object {

        const val STEVE_ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
    }

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("data").resolve("players.json")

    private val steve = UUID.fromString(STEVE_ID)

    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    private fun write(json: String) {
        path.parent.createDirectories()
        path.writeText(json)
    }

    private fun refusal(json: String): List<FileFinding> {
        write(json)
        val reading = PlayersDataFile.read(path)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    private fun json(): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

    private fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `le fichier qui manque est créé vide, dans data, sous son nom`() {
        val file = PlayersDataFile.at(directory)

        assertEquals(FileReading.Applied(PlayerRegistry()), file.load())
        assertEquals("data/players.json", file.name)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json())
    }

    @Test
    fun `une connexion s'écrit tout de suite, avec le nom et la date en UTC, et se relit telle quelle`() {
        val file = PlayersDataFile.at(directory)
        file.load()
        val registry = PlayerRegistry().joined(steve, "Steve", noon)

        file.update(registry)

        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": { "$STEVE_ID": { "name": "Steve", "last-seen": "2026-10-05T12:00:00Z" } } }"""), json())
        assertEquals(FileReading.Applied(registry), PlayersDataFile.read(path))
        assertEquals(PlayerRecord("Steve", noon), PlayersDataFile.at(directory).also { it.load() }.content?.get(steve), "Another file reads what was written")
    }

    @Test
    fun `le fichier ne connaît que le nom et la dernière connexion, les exige, et juge leur nature et leur valeur`() {
        val unknown = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"name\": \"Steve\",\n      \"last-seen\": \"2026-10-05T12:00:00Z\",\n      \"group\": \"vip\"\n    }\n  }\n}")
        val nature = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"name\": \"Steve\",\n      \"last-seen\": 1759665600\n    }\n  }\n}")
        val missing = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"name\": \"Steve\"\n    }\n  }\n}")
        val date = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"name\": \"Steve\",\n      \"last-seen\": \"hier\"\n    }\n  }\n}")

        assertEquals(listOf(FileFinding("players[$STEVE_ID].group", 6, CommonMessages.FINDING_UNKNOWN_KEY.with())), unknown)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].last-seen", 5, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_TEXT.with()))), nature)
        assertEquals(listOf(FileFinding("players[$STEVE_ID]", 3, CommonMessages.FINDING_MISSING_KEY.with("last-seen"))), missing)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].last-seen", 5, CommonMessages.FINDING_NOT_ALLOWED.with("hier", CommonMessages.ALLOWED_UTC_INSTANT.with()))), date)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et le registre est indisponible`() {
        val faulty = "{\n  \"players\": { \"Steve\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        val file = PlayersDataFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertFalse(file.isAvailable)
    }
}
