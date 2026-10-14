// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.groups

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.groups.Membership
import fr.roumoulou.manymanycommands.rules.groups.Memberships
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
 * `data\memberships.json` sur un vrai fichier (`groups.md` des specs, chapitre 4 ; cahier des charges, chapitre 2.7.5) : sa création dans `data\`,
 * l'inscription écrite tout de suite, ses constats à leur ligne, le fichier fautif jamais réécrit.
 */
class MembershipsDataFileTest {

    private companion object {

        const val STEVE_ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
    }

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("data").resolve("memberships.json")

    private val steve = UUID.fromString(STEVE_ID)

    private fun write(json: String) {
        path.parent.createDirectories()
        path.writeText(json)
    }

    private fun refusal(json: String): List<FileFinding> {
        write(json)
        val reading = MembershipsDataFile.read(path)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    private fun json(): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

    private fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `le fichier qui manque est créé vide, dans data, sous son nom`() {
        val file = MembershipsDataFile.at(directory)

        assertEquals(FileReading.Applied(Memberships()), file.load())
        assertEquals("data/memberships.json", file.name)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json())
    }

    @Test
    fun `une inscription s'écrit tout de suite, avec le nom du joueur et sans ses listes vides, et se relit telle quelle`() {
        val file = MembershipsDataFile.at(directory)
        file.load()
        val data = Memberships().with(steve, PlayerEntry("Steve", Membership("vip", allow = setOf("manymanycommands.rules.homes.staff"))))

        file.update(data)

        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": { "$STEVE_ID": { "name": "Steve", "group": "vip", "allow": ["manymanycommands.rules.homes.staff"] } } }"""), json())
        assertEquals(FileReading.Applied(data), MembershipsDataFile.read(path))
        file.update(Memberships().with(steve, PlayerEntry("Steve", Membership())))
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "players": {} }"""), json(), "A player back in default without own permission is not written")
    }

    @Test
    fun `le fichier ne connaît que le nom, le groupe et les deux listes, et chaque constat porte sa ligne`() {
        val unknown = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"quota\": {}\n    }\n  }\n}")
        val nature = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"group\": 3\n    }\n  }\n}")
        val group = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"group\": \"Vip\"\n    }\n  }\n}")
        val node = refusal("{\n  \"players\": {\n    \"$STEVE_ID\": {\n      \"deny\": [\n        \"manymanycommands.homes.fly\"\n      ]\n    }\n  }\n}")

        assertEquals(listOf(FileFinding("players[$STEVE_ID].quota", 4, CommonMessages.FINDING_UNKNOWN_KEY.with())), unknown)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].group", 4, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_TEXT.with()))), nature)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].group", 4, CommonMessages.FINDING_NOT_ALLOWED.with("Vip", CommonMessages.ALLOWED_LOWERCASE_NAME.with()))), group)
        assertEquals(listOf(FileFinding("players[$STEVE_ID].deny[0]", 5, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.homes.fly", CommonMessages.ALLOWED_NODE.with()))), node)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et les inscriptions sont indisponibles`() {
        val faulty = "{\n  \"players\": { \"Steve\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        val file = MembershipsDataFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertFalse(file.isAvailable)
    }
}
