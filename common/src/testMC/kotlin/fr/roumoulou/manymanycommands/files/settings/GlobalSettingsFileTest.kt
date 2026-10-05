// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.settings

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** `settings.json` sur un vrai fichier : sa création, le JSON strict, la clé inconnue, la valeur absente, le fichier invalide (cahier des charges, chapitre 2.7). */
class GlobalSettingsFileTest {

    @TempDir
    lateinit var directory: Path

    private val path: Path get() = directory.resolve(GlobalSettings.FILE_NAME)

    private fun read(json: String): FileReading<GlobalSettings> {
        path.writeText(json)
        return GlobalSettingsFile.read(path)
    }

    private fun refusal(json: String): List<FileFinding> {
        val reading = read(json)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    // ── La création ──

    @Test
    fun `le fichier qui manque est créé, avec les valeurs livrées`() {
        val reading = GlobalSettingsFile.read(path)

        assertEquals(FileReading.Applied(GlobalSettings()), reading)
        val written = Json.parseToJsonElement(path.readText()).jsonObject
        val expected = mapOf("schema-version" to JsonPrimitive(1), "language" to JsonPrimitive("auto"), "permission-source" to JsonPrimitive("internal"), "mmc-alias" to JsonPrimitive(true))
        assertEquals(expected, written.toMap())
    }

    @Test
    fun `le dossier du mod est créé avec lui, espaces et accents compris`() {
        val nested = directory.resolve("config de l'été").resolve("many many commands").resolve(GlobalSettings.FILE_NAME)

        assertEquals(FileReading.Applied(GlobalSettings()), GlobalSettingsFile.read(nested))
        assertTrue(nested.exists())
    }

    @Test
    fun `le fichier créé se relit valide`() {
        GlobalSettingsFile.read(path)

        assertEquals(FileReading.Applied(GlobalSettings()), GlobalSettingsFile.read(path))
    }

    // ── La lecture ──

    @Test
    fun `un fichier valide s'applique`() {
        val reading = read("""{ "schema-version": 1, "language": "fr", "permission-source": "external", "mmc-alias": false }""")

        assertEquals(FileReading.Applied(GlobalSettings(LanguageSetting.FRENCH, PermissionSourceSetting.EXTERNAL, mmcAlias = false)), reading)
    }

    @Test
    fun `un réglage absent prend sa valeur livrée`() {
        assertEquals(FileReading.Applied(GlobalSettings(language = LanguageSetting.ENGLISH)), read("""{ "language": "en" }"""))
        assertEquals(FileReading.Applied(GlobalSettings()), read("{}"))
    }

    @Test
    fun `un fichier valide n'est pas réécrit, sa mise en forme reste celle de l'administrateur`() {
        val handwritten = "{\n\t\"mmc-alias\":false,\n\n\t\"language\":   \"fr\"\n}\n".toByteArray()
        path.writeBytes(handwritten)

        GlobalSettingsFile.read(path)

        assertArrayEquals(handwritten, path.readBytes())
    }

    @Test
    fun `un BOM en tête de fichier est toléré`() {
        path.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + """{ "language": "fr" }""".toByteArray())

        assertEquals(FileReading.Applied(GlobalSettings(language = LanguageSetting.FRENCH)), GlobalSettingsFile.read(path))
    }

    // ── Le fichier invalide ──

    @Test
    fun `une clé inconnue refuse le fichier, à son chemin et à sa ligne`() {
        val findings = refusal("{\n  \"language\": \"fr\",\n  \"langage\": \"fr\"\n}")

        assertEquals(listOf(FileFinding("langage", 3, CommonMessages.FINDING_UNKNOWN_KEY.with())), findings)
    }

    @Test
    fun `une valeur d'une autre nature refuse le fichier, avec la nature attendue`() {
        val findings = refusal("{\n  \"schema-version\": \"1\",\n  \"language\": 5,\n  \"mmc-alias\": \"oui\"\n}")

        val expected = listOf(
            FileFinding("schema-version", 2, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_INTEGER.with())),
            FileFinding("language", 3, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_TEXT.with())),
            FileFinding("mmc-alias", 4, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_BOOLEAN.with())),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `une valeur nulle n'a la nature d'aucun réglage`() {
        val findings = refusal("{\n  \"language\": null\n}")

        assertEquals(listOf(FileFinding("language", 2, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_TEXT.with()))), findings)
    }

    @Test
    fun `chaque faute de structure a son constat, clés inconnues et natures ensemble`() {
        val findings = refusal("{\n  \"alias\": true,\n  \"mmc-alias\": 1,\n  \"sources\": []\n}")

        assertEquals(listOf("alias" to 2, "mmc-alias" to 3, "sources" to 4), findings.map { it.path to it.line })
    }

    @Test
    fun `une valeur hors des valeurs admises refuse le fichier, à son chemin et à sa ligne`() {
        val findings = refusal("{\n  \"schema-version\": 1,\n  \"language\": \"de\",\n  \"permission-source\": \"luckperms\"\n}")

        val expected = listOf(
            FileFinding("language", 3, CommonMessages.FINDING_NOT_ALLOWED.with("de", "auto, fr, en")),
            FileFinding("permission-source", 4, CommonMessages.FINDING_NOT_ALLOWED.with("luckperms", "internal, external")),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `un fichier plus récent que le mod est refusé`() {
        val findings = refusal("{\n  \"schema-version\": 2\n}")

        assertEquals(listOf(FileFinding("schema-version", 2, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, GlobalSettings.SCHEMA_VERSION))), findings)
    }

    @Test
    fun `un fichier écrit sur une seule ligne donne ses constats sans ligne`() {
        val findings = refusal("""{"language":"de"}""")

        assertEquals("language", findings.single().path)
        assertNull(findings.single().line)
    }

    @Test
    fun `un JSON mal formé refuse le fichier, par le message du lecteur et sa ligne`() {
        val finding = refusal("{\n  \"language\": \"fr\",\n  \"mmc-alias\" true\n}").single()

        assertNull(finding.path)
        assertEquals(3, finding.line)
        assertEquals(CommonMessages.FINDING_UNREADABLE.key, finding.text.key)
        assertTrue(finding.text.arguments.single().toString().isNotBlank(), "The reader's message must be given")
    }

    @Test
    fun `le JSON est strict, un commentaire, une virgule finale ou une clé sans guillemets refusent le fichier`() {
        listOf("{\n  // la langue\n  \"language\": \"fr\"\n}", "{\n  \"language\": \"fr\",\n}", "{\n  language: \"fr\"\n}").forEach { json ->
            assertEquals(CommonMessages.FINDING_UNREADABLE.key, refusal(json).single().text.key, "Strict JSON must refuse: $json")
        }
    }

    @Test
    fun `une clé déclarée deux fois refuse le fichier, à la ligne de la seconde`() {
        val finding = refusal("{\n  \"language\": \"fr\",\n  \"language\": \"en\"\n}").single()

        assertEquals(CommonMessages.FINDING_UNREADABLE.key, finding.text.key)
        assertEquals(3, finding.line)
    }

    @Test
    fun `un fichier vide est refusé`() {
        assertEquals(CommonMessages.FINDING_UNREADABLE.key, refusal("").single().text.key)
    }

    @Test
    fun `un fichier qui n'est pas un objet est refusé`() {
        val finding = refusal("[]").single()

        assertNull(finding.path)
        assertEquals(CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_OBJECT.with()), finding.text)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit`() {
        val faulty = "{\n  \"language\": \"de\",\n  \"langage\": 3\n}\n".toByteArray()
        path.writeBytes(faulty)

        repeat(2) { GlobalSettingsFile.read(path) }

        assertArrayEquals(faulty, path.readBytes())
    }

    @Test
    fun `un dossier à la place du fichier refuse la lecture sans rien lever`() {
        path.createDirectories()

        val reading = GlobalSettingsFile.read(path)

        assertTrue(reading is FileReading.Refused, "A directory in place of the file must be refused, was $reading")
        assertEquals(CommonMessages.FINDING_UNREADABLE.key, (reading as FileReading.Refused).findings.single().text.key)
    }
}
