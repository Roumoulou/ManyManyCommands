// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.groups

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.groups.GroupDefinition
import fr.roumoulou.manymanycommands.rules.groups.Groups
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** `groups.json` sur un vrai fichier (`groups.md` des specs, chapitre 4) : sa création, l'exemple de la fiche, ses constats à leur ligne, le fichier fautif jamais réécrit. */
class GroupsFileTest {

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve("groups.json")

    private fun write(json: String) {
        path.parent.createDirectories()
        path.writeText(json)
    }

    private fun refusal(json: String): List<FileFinding> {
        write(json)
        val reading = GroupsFile.read(path)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    private fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `le fichier qui manque est créé avec le groupe default, vide et complet, sous son nom`() {
        val file = GroupsFile.at(directory)

        assertEquals(FileReading.Applied(Groups.SHIPPED), file.load())
        assertEquals("groups.json", file.name)
        assertEquals(jsonObjectOf("""{ "schema-version": 1, "groups": { "default": { "rules": {}, "allow": [], "deny": [] } } }"""), Json.parseToJsonElement(path.readText()).jsonObject)
    }

    @Test
    fun `l'exemple de la fiche se lit, dans son ordre, et une clé absente d'un groupe vaut vide`() {
        write("""{ "schema-version": 1, "groups": { "default": {}, "vip": { "rules": { "homes": "vip", "wild": "vip" }, "allow": ["manymanycommands.warps.access.vip"], "deny": [] } } }""")

        val reading = GroupsFile.read(path)

        val expected = Groups(mapOf("default" to GroupDefinition(), "vip" to GroupDefinition(mapOf("homes" to "vip", "wild" to "vip"), setOf("manymanycommands.warps.access.vip"))))
        assertEquals(FileReading.Applied(expected), reading)
    }

    @Test
    fun `le fichier ne connaît que rules, allow et deny, juge leur nature, et chaque constat porte sa ligne`() {
        val unknown = refusal("{\n  \"groups\": {\n    \"vip\": {\n      \"weight\": 10\n    }\n  }\n}")
        val nature = refusal("{\n  \"groups\": {\n    \"vip\": {\n      \"allow\": \"manymanycommands.back\"\n    }\n  }\n}")
        val family = refusal("{\n  \"groups\": {\n    \"vip\": {\n      \"rules\": {\n        \"homez\": \"vip\"\n      }\n    }\n  }\n}")
        val node = refusal("{\n  \"groups\": {\n    \"vip\": {\n      \"allow\": [\n        \"manymanycommands.back\",\n        \"manymanycommands.rules.homes.vip\"\n      ]\n    }\n  }\n}")

        assertEquals(listOf(FileFinding("groups[vip].weight", 4, CommonMessages.FINDING_UNKNOWN_KEY.with())), unknown)
        assertEquals(listOf(FileFinding("groups[vip].allow", 4, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_LIST.with()))), nature)
        assertEquals(listOf(FileFinding("groups[vip].rules.homez", 5, CommonMessages.FINDING_UNKNOWN_KEY.with())), family)
        assertEquals(listOf(FileFinding("groups[vip].allow[1]", 6, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.rules.homes.vip", CommonMessages.ALLOWED_COMMAND_NODE.with()))), node)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit, et les groupes livrés tiennent sa place`() {
        val faulty = "{\n  \"groups\": { \"Vip\": {} }\n}\n".toByteArray()
        path.parent.createDirectories()
        path.writeBytes(faulty)

        val file = GroupsFile.at(directory)
        file.load()

        assertArrayEquals(faulty, path.readBytes())
        assertEquals(Groups.SHIPPED, file.inForce)
        assertTrue(file.isShippedInPlace)
    }
}
