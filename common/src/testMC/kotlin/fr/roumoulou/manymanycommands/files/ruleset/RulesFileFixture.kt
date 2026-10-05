// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.ruleset

import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Le fichier de règles d'une famille dans le dossier d'un test : ce que le test y écrit, et ce que la lecture de la famille en rend.
 *
 * @param path le fichier, que le test n'a pas encore créé
 * @param reader la lecture de la famille
 */
class RulesFileFixture(val path: Path, private val reader: (Path) -> FileReading<FamilyRules>) {

    /** Lit le fichier tel qu'il est sur le disque : il naît de cette lecture quand il manque. */
    fun read(): FileReading<FamilyRules> = reader(path)

    fun read(json: String): FileReading<FamilyRules> {
        path.parent.createDirectories()
        path.writeText(json)
        return reader(path)
    }

    /** Les règles de [json], qui doit s'appliquer. */
    fun rules(json: String): FamilyRules {
        val reading = read(json)
        assertTrue(reading is FileReading.Applied, "The file must apply, was $reading")
        return (reading as FileReading.Applied).content
    }

    /** Les constats de [json], qui doit être refusé. */
    fun refusal(json: String): List<FileFinding> {
        val reading = read(json)
        assertTrue(reading is FileReading.Refused, "The file must be refused, was $reading")
        return (reading as FileReading.Refused).findings
    }

    /** Le fichier que la lecture crée quand il manque, en arbre JSON. */
    fun created(): JsonObject {
        read()
        return Json.parseToJsonElement(path.readText()).jsonObject
    }
}

/** L'arbre JSON de [json], pour le comparer à un fichier sans égard pour sa mise en forme. */
fun jsonObjectOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
