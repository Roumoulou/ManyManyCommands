// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.ruleset

import fr.roumoulou.manymanycommands.files.back.BackRulesFile
import fr.roumoulou.manymanycommands.files.homes.HomesRulesFile
import fr.roumoulou.manymanycommands.files.tpa.TpaRulesFile
import fr.roumoulou.manymanycommands.files.warps.WarpsRulesFile
import fr.roumoulou.manymanycommands.files.wild.WildRulesFile
import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Les cinq fichiers de règles sur de vrais fichiers (cahier des charges, chapitre 2.7.1) : chacun naît avec les réglages et le jeu `default` du
 * tableau de sa fiche, dans son ordre, et ne connaît que ses propres clés.
 */
class FamilyRulesFilesTest {

    /** Une famille : sa table, son fichier dans un dossier, et sa lecture. */
    private class Family(val table: RulesTable, val at: (Path) -> SettingsFile<FamilyRules>, val read: (Path) -> FileReading<FamilyRules>)

    private val families = listOf(
        Family(HomesRules, HomesRulesFile::at, HomesRulesFile::read),
        Family(WarpsRules, WarpsRulesFile::at, WarpsRulesFile::read),
        Family(BackRules, BackRulesFile::at, BackRulesFile::read),
        Family(WildRules, WildRulesFile::at, WildRulesFile::read),
        Family(TpaRules, TpaRulesFile::at, TpaRulesFile::read),
    )

    @TempDir
    lateinit var directory: Path

    private fun fixture(name: String, read: (Path) -> FileReading<FamilyRules>) = RulesFileFixture(directory.resolve("rules").resolve("$name.json"), read)

    // ── Les cinq fichiers ensemble ──

    @Test
    fun `chaque famille crée son fichier dans le dossier rules, le relit valide et y trouve ses règles livrées`() {
        families.forEach { family ->
            val file = family.at(directory)

            assertEquals("rules/${family.table.family}.json", file.name)
            assertEquals(FileReading.Applied(family.table.shipped), file.load(), "The created file of ${family.table.family}")
            assertTrue(directory.resolve("rules").resolve("${family.table.family}.json").exists())
            assertEquals(FileReading.Applied(family.table.shipped), file.load(), "The created file of ${family.table.family}, read again")
        }
    }

    @Test
    fun `chaque fichier créé écrit ses réglages puis ses règles dans l'ordre du tableau de sa fiche`() {
        families.forEach { family ->
            val created = fixture(family.table.family, family.read).created()

            assertEquals(listOf("schema-version") + family.table.settings.map { it.key } + "rules", created.keys.toList(), "The keys of ${family.table.fileName}")
            val sets = created.getValue("rules").jsonObject
            assertEquals(listOf("default"), sets.keys.toList(), "The sets of ${family.table.fileName}")
            assertEquals(family.table.rules.map { it.key }, sets.getValue("default").jsonObject.keys.toList(), "The rules of ${family.table.fileName}")
        }
    }

    // ── Chaque fichier, ses valeurs livrées et ses propres clés ──

    @Test
    fun `homes json naît avec les valeurs livrées de sa fiche`() {
        val expected = """
            {
              "schema-version": 1,
              "enabled": true,
              "aliases": true,
              "rules": {
                "default": {
                  "max-homes": 3,
                  "forbidden-dimensions": [],
                  "forbidden-departure-dimensions": [],
                  "delay": 3,
                  "cooldown": 10,
                  "quota": null
                }
              }
            }
        """.trimIndent()

        assertEquals(jsonObjectOf(expected), fixture("homes", HomesRulesFile::read).created())
    }

    @Test
    fun `warps json naît avec les valeurs livrées de sa fiche, et ne connaît ni limite ni dimension interdite`() {
        val file = fixture("warps", WarpsRulesFile::read)
        val expected = """
            {
              "schema-version": 1,
              "enabled": true,
              "aliases": true,
              "rules": {
                "default": {
                  "forbidden-departure-dimensions": [],
                  "delay": 3,
                  "cooldown": 10,
                  "quota": null
                }
              }
            }
        """.trimIndent()

        assertEquals(jsonObjectOf(expected), file.created())
        val findings = file.refusal("{\n  \"rules\": {\n    \"default\": {\n      \"max-homes\": 3,\n      \"forbidden-dimensions\": []\n    }\n  }\n}")
        val unknown = CommonMessages.FINDING_UNKNOWN_KEY.with()
        assertEquals(listOf(FileFinding("rules[default].max-homes", 4, unknown), FileFinding("rules[default].forbidden-dimensions", 5, unknown)), findings)
    }

    @Test
    fun `back json naît avec les valeurs livrées de sa fiche, sans alias`() {
        val file = fixture("back", BackRulesFile::read)
        val expected = """
            {
              "schema-version": 1,
              "enabled": true,
              "rules": {
                "default": {
                  "sources": {
                    "homes": true,
                    "warps": true,
                    "wild": true,
                    "tpa": true,
                    "back": true,
                    "send": true,
                    "death": false
                  },
                  "forbidden-departure-dimensions": [],
                  "delay": 3,
                  "cooldown": 10,
                  "quota": null
                }
              }
            }
        """.trimIndent()

        assertEquals(jsonObjectOf(expected), file.created())
        assertEquals(listOf(FileFinding("aliases", 2, CommonMessages.FINDING_UNKNOWN_KEY.with())), file.refusal("{\n  \"aliases\": true\n}"))
    }

    @Test
    fun `back json lit les sources qu'un jeu écrit, et refuse une source inconnue ou un interrupteur d'une autre nature`() {
        val file = fixture("back", BackRulesFile::read)

        val rules = file.rules("""{ "rules": { "default": { "sources": { "death": true } }, "vip": { "sources": { "wild": false } } } }""")
        assertEquals(BackRules.sources.shipped + ("death" to true), rules.default[BackRules.sources])
        assertEquals(BackRules.sources.shipped + ("death" to true) + ("wild" to false), rules.sets.getValue("vip")[BackRules.sources])

        val findings = file.refusal("{\n  \"rules\": {\n    \"default\": {\n      \"sources\": {\n        \"deaths\": true,\n        \"homes\": \"non\"\n      }\n    }\n  }\n}")
        val expected = listOf(
            FileFinding("rules[default].sources.deaths", 5, CommonMessages.FINDING_UNKNOWN_KEY.with()),
            FileFinding("rules[default].sources.homes", 6, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_BOOLEAN.with())),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `wild json naît avec les valeurs livrées de sa fiche, son quota écrit entier`() {
        val expected = """
            {
              "schema-version": 1,
              "enabled": true,
              "aliases": true,
              "min-distance": 4000,
              "max-distance": 10000,
              "timed-window": 10,
              "rules": {
                "default": {
                  "forbidden-departure-dimensions": ["minecraft:the_nether", "minecraft:the_end"],
                  "quota": {
                    "uses": 3,
                    "period": 28800,
                    "clock": "real"
                  },
                  "delay": 3,
                  "cooldown": 3600
                }
              }
            }
        """.trimIndent()

        assertEquals(jsonObjectOf(expected), fixture("wild", WildRulesFile::read).created())
    }

    @Test
    fun `wild json refuse une distance minimale qui n'est pas sous la distance maximale, à sa ligne`() {
        val file = fixture("wild", WildRulesFile::read)

        val finding = file.refusal("{\n  \"max-distance\": 5000,\n  \"min-distance\": 5000\n}").single()

        assertEquals(FileFinding("min-distance", 3, CommonMessages.FINDING_NOT_ALLOWED.with(5_000L, CommonMessages.ALLOWED_RANGE.with(0L, 4_999L))), finding)
        assertEquals(1_000L, file.rules("""{ "min-distance": 1000, "max-distance": 1001 }""").settings[WildRules.minDistance])
    }

    @Test
    fun `tpa json naît avec les valeurs livrées de sa fiche, sans alias`() {
        val file = fixture("tpa", TpaRulesFile::read)
        val expected = """
            {
              "schema-version": 1,
              "enabled": true,
              "request-lifetime": 60,
              "request-interval": 60,
              "rules": {
                "default": {
                  "forbidden-departure-dimensions": [],
                  "delay": 3,
                  "cooldown": 10,
                  "quota": null
                }
              }
            }
        """.trimIndent()

        assertEquals(jsonObjectOf(expected), file.created())
        assertEquals(listOf(FileFinding("aliases", 2, CommonMessages.FINDING_UNKNOWN_KEY.with())), file.refusal("{\n  \"aliases\": true\n}"))
    }

    @Test
    fun `tpa json refuse une demande sans durée de vie, à sa ligne`() {
        val finding = fixture("tpa", TpaRulesFile::read).refusal("{\n  \"request-interval\": 0,\n  \"request-lifetime\": 0\n}").single()

        assertEquals(FileFinding("request-lifetime", 3, CommonMessages.FINDING_NOT_ALLOWED.with(0L, CommonMessages.ALLOWED_AT_LEAST.with(1L))), finding)
    }
}
