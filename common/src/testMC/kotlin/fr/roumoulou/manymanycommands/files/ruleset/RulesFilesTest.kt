// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.ruleset

import fr.roumoulou.manymanycommands.files.homes.HomesRulesFile
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Fragment
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Le fichier de règles d'une famille sur un vrai fichier (cahier des charges, chapitres 2.7.1, 2.7.3 et 2.7.4) : sa création, ses jeux, le JSON
 * strict, la clé inconnue, la valeur absente, la valeur d'une autre nature, la valeur hors des valeurs admises. `rules\homes.json` sert d'exemple.
 */
class RulesFilesTest {

    @TempDir
    lateinit var directory: Path

    private lateinit var file: RulesFileFixture

    private val integer = CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_INTEGER.with())

    @BeforeEach
    fun openTheFile() {
        file = RulesFileFixture(directory.resolve("rules").resolve("homes.json"), HomesRulesFile::read)
    }

    // ── La création ──

    @Test
    fun `le fichier qui manque est créé dans le dossier rules, avec ses valeurs livrées`() {
        val settingsFile = HomesRulesFile.at(directory)

        val reading = settingsFile.load()

        assertEquals("rules/homes.json", settingsFile.name)
        assertEquals(FileReading.Applied(HomesRules.shipped), reading)
        assertTrue(file.path.exists(), "The file must be created at ${file.path}")
    }

    @Test
    fun `le dossier du mod est créé avec lui, espaces et accents compris`() {
        val nested = directory.resolve("config de l'été").resolve("many many commands")

        assertEquals(FileReading.Applied(HomesRules.shipped), HomesRulesFile.at(nested).load())
        assertTrue(nested.resolve("rules").resolve("homes.json").exists())
    }

    @Test
    fun `le fichier créé se relit valide`() {
        file.read()

        assertEquals(FileReading.Applied(HomesRules.shipped), file.read())
    }

    // ── La lecture ──

    @Test
    fun `le fichier d'exemple du cahier des charges s'applique`() {
        val rules = file.rules(
            """
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
                },
                "vip": {
                  "max-homes": 10,
                  "cooldown": 0
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(HomesRules.shipped.default, rules.default)
        assertEquals(listOf("default", "vip"), rules.sets.keys.toList())
        val vip = rules.sets.getValue("vip")
        assertEquals(10L, vip[HomesRules.maxHomes])
        assertEquals(0L, vip[HomesRules.cooldown])
        assertEquals(3L, vip[HomesRules.delay])
    }

    @Test
    fun `un fichier qui n'écrit rien prend les règles livrées, et le jeu default existe sans y être écrit`() {
        assertEquals(HomesRules.shipped, file.rules("{}"))
        assertEquals(HomesRules.shipped, file.rules("""{ "rules": {} }"""))
    }

    @Test
    fun `un réglage et une règle écrits s'appliquent, les autres gardent leur valeur livrée`() {
        val rules = file.rules("""{ "aliases": false, "rules": { "default": { "delay": 0, "forbidden-dimensions": ["minecraft:the_end", "the_nether"] } } }""")

        assertEquals(false, rules.settings[HomesRules.aliases])
        assertEquals(true, rules.settings[HomesRules.enabled])
        assertEquals(0L, rules.default[HomesRules.delay])
        assertEquals(setOf("minecraft:the_end", "minecraft:the_nether"), rules.default[HomesRules.forbiddenDimensions])
        assertEquals(10L, rules.default[HomesRules.cooldown])
    }

    @Test
    fun `les jeux se lisent dans l'ordre du fichier`() {
        val rules = file.rules("""{ "rules": { "staff": {}, "default": {}, "vip": {} } }""")

        assertEquals(listOf("staff", "default", "vip"), rules.sets.keys.toList())
    }

    @Test
    fun `un quota à null n'est pas un quota absent`() {
        val rules = file.rules(
            """
            {
              "rules": {
                "default": { "quota": { "uses": 2, "period": 60 } },
                "vip": { "quota": null },
                "staff": { "quota": { "uses": 9, "period": 3600, "clock": "playtime" } },
                "member": {}
              }
            }
            """.trimIndent(),
        )

        assertEquals(Quota(2, 60, QuotaClock.REAL), rules.default[HomesRules.quota])
        assertNull(rules.sets.getValue("vip")[HomesRules.quota])
        assertEquals(Quota(9, 3_600, QuotaClock.PLAYTIME), rules.sets.getValue("staff")[HomesRules.quota])
        assertEquals(Quota(2, 60, QuotaClock.REAL), rules.sets.getValue("member")[HomesRules.quota])
    }

    @Test
    fun `un fichier valide n'est pas réécrit, sa mise en forme reste celle de l'administrateur`() {
        val handwritten = "{\n\t\"rules\":{\"vip\":{\"max-homes\":10}},\n\n\t\"aliases\":   false\n}\n".toByteArray()
        file.path.parent.createDirectories()
        file.path.writeBytes(handwritten)

        file.read()

        assertArrayEquals(handwritten, file.path.readBytes())
    }

    // ── La structure : la clé inconnue, la valeur d'une autre nature ──

    @Test
    fun `une clé inconnue refuse le fichier, à tous les niveaux, à son chemin et à sa ligne`() {
        val findings = file.refusal(
            """
            {
              "alias": true,
              "rules": {
                "vip": {
                  "max-home": 5,
                  "quota": {
                    "uses": 1,
                    "period": 60,
                    "horloge": "real"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val unknown = CommonMessages.FINDING_UNKNOWN_KEY.with()
        assertEquals(listOf(FileFinding("alias", 2, unknown), FileFinding("rules[vip].max-home", 5, unknown), FileFinding("rules[vip].quota.horloge", 9, unknown)), findings)
    }

    @Test
    fun `la règle d'une autre famille est une clé inconnue`() {
        val findings = file.refusal("{\n  \"min-distance\": 100,\n  \"rules\": {\n    \"default\": {\n      \"sources\": {}\n    }\n  }\n}")

        assertEquals(listOf("min-distance" to 2, "rules[default].sources" to 5), findings.map { it.path to it.line })
        assertEquals(setOf(CommonMessages.FINDING_UNKNOWN_KEY.key), findings.map { it.text.key }.toSet())
    }

    @Test
    fun `une valeur d'une autre nature refuse le fichier, avec la nature attendue`() {
        val findings = file.refusal(
            """
            {
              "enabled": "oui",
              "rules": {
                "default": {
                  "max-homes": "3",
                  "delay": 2.5,
                  "forbidden-dimensions": "minecraft:the_end",
                  "forbidden-departure-dimensions": [
                    "minecraft:the_end",
                    7
                  ],
                  "quota": 5
                },
                "vip": 3
              }
            }
            """.trimIndent(),
        )

        fun nature(nature: Fragment) = CommonMessages.FINDING_WRONG_NATURE.with(nature.with())
        val expected = listOf(
            FileFinding("enabled", 2, nature(CommonMessages.NATURE_BOOLEAN)),
            FileFinding("rules[default].max-homes", 5, integer),
            FileFinding("rules[default].delay", 6, integer),
            FileFinding("rules[default].forbidden-dimensions", 7, nature(CommonMessages.NATURE_LIST)),
            FileFinding("rules[default].forbidden-departure-dimensions[1]", 10, nature(CommonMessages.NATURE_TEXT)),
            FileFinding("rules[default].quota", 12, nature(CommonMessages.NATURE_OBJECT)),
            FileFinding("rules[vip]", 14, nature(CommonMessages.NATURE_OBJECT)),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `les jeux s'écrivent dans un objet`() {
        val finding = file.refusal("{\n  \"rules\": []\n}").single()

        assertEquals(FileFinding("rules", 2, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_OBJECT.with())), finding)
    }

    @Test
    fun `une valeur nulle n'a la nature d'aucune règle, sauf celle du quota`() {
        val findings = file.refusal("{\n  \"enabled\": null,\n  \"rules\": {\n    \"default\": {\n      \"delay\": null,\n      \"quota\": null\n    },\n    \"vip\": {\n      \"quota\": {\n        \"uses\": null,\n        \"period\": 60\n      }\n    }\n  }\n}")

        val expected = listOf(
            FileFinding("enabled", 2, CommonMessages.FINDING_WRONG_NATURE.with(CommonMessages.NATURE_BOOLEAN.with())),
            FileFinding("rules[default].delay", 5, integer),
            FileFinding("rules[vip].quota.uses", 10, integer),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `tant que la structure est fautive, les valeurs ne sont pas jugées`() {
        val findings = file.refusal("{\n  \"rules\": {\n    \"default\": {\n      \"delay\": -1,\n      \"delai\": 3\n    }\n  }\n}")

        assertEquals(listOf(FileFinding("rules[default].delai", 5, CommonMessages.FINDING_UNKNOWN_KEY.with())), findings)
    }

    // ── Les valeurs admises ──

    @Test
    fun `une valeur hors des valeurs admises refuse le fichier, à son chemin et à sa ligne`() {
        val findings = file.refusal(
            """
            {
              "rules": {
                "default": {
                  "forbidden-dimensions": [
                    "minecraft:the_end",
                    "The Nether"
                  ],
                  "quota": {
                    "uses": 0,
                    "period": 60,
                    "clock": "lunar"
                  }
                },
                "vip": {
                  "max-homes": -1
                }
              }
            }
            """.trimIndent(),
        )

        val expected = listOf(
            FileFinding("rules[default].forbidden-dimensions[1]", 6, CommonMessages.FINDING_NOT_ALLOWED.with("The Nether", CommonMessages.ALLOWED_DIMENSION.with())),
            FileFinding("rules[default].quota.uses", 9, CommonMessages.FINDING_NOT_ALLOWED.with(0L, CommonMessages.ALLOWED_AT_LEAST.with(1L))),
            FileFinding("rules[default].quota.clock", 11, CommonMessages.FINDING_NOT_ALLOWED.with("lunar", "real, playtime")),
            FileFinding("rules[vip].max-homes", 15, CommonMessages.FINDING_NOT_ALLOWED.with(-1L, CommonMessages.ALLOWED_AT_LEAST.with(0L))),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `un nom de jeu en majuscules refuse le fichier, à la ligne du jeu`() {
        val finding = file.refusal("{\n  \"rules\": {\n    \"default\": {},\n    \"VIP\": {\n      \"max-homes\": 10\n    }\n  }\n}").single()

        assertEquals(FileFinding("rules[VIP]", 4, CommonMessages.FINDING_NOT_ALLOWED.with("VIP", CommonMessages.ALLOWED_LOWERCASE_NAME.with())), finding)
    }

    @Test
    fun `un quota sans uses refuse le fichier, à la ligne du quota et non à celle du uses d'un autre jeu`() {
        val finding = file.refusal(
            """
            {
              "rules": {
                "vip": {
                  "quota": {
                    "period": 60
                  }
                },
                "staff": {
                  "quota": {
                    "uses": 9,
                    "period": 60
                  }
                }
              }
            }
            """.trimIndent(),
        ).single()

        assertEquals(FileFinding("rules[vip].quota", 4, CommonMessages.FINDING_MISSING_KEY.with("uses")), finding)
    }

    @Test
    fun `un fichier plus récent que le mod est refusé`() {
        val finding = file.refusal("{\n  \"schema-version\": 2\n}").single()

        assertEquals(FileFinding("schema-version", 2, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1)), finding)
    }

    // ── Le JSON strict ──

    @Test
    fun `un JSON mal formé refuse le fichier, par le message du lecteur`() {
        val finding = file.refusal("{\n  \"rules\": {\n    \"default\" {}\n  }\n}").single()

        assertNull(finding.path)
        assertEquals(CommonMessages.FINDING_UNREADABLE.key, finding.text.key)
    }

    @Test
    fun `le JSON est strict, un commentaire ou une virgule finale refusent le fichier`() {
        listOf("{\n  // les jeux\n  \"rules\": {}\n}", "{\n  \"rules\": { \"default\": { \"delay\": 3, } }\n}").forEach { json ->
            assertEquals(CommonMessages.FINDING_UNREADABLE.key, file.refusal(json).single().text.key, "Strict JSON must refuse: $json")
        }
    }

    @Test
    fun `un jeu déclaré deux fois refuse le fichier, à la ligne du second`() {
        val finding = file.refusal("{\n  \"rules\": {\n    \"vip\": {},\n    \"vip\": {}\n  }\n}").single()

        assertEquals(CommonMessages.FINDING_UNREADABLE.key, finding.text.key)
        assertEquals(4, finding.line)
    }

    @Test
    fun `le fichier fautif n'est jamais réécrit`() {
        val faulty = "{\n  \"rules\": { \"VIP\": { \"delay\": -1 } }\n}\n".toByteArray()
        file.path.parent.createDirectories()
        file.path.writeBytes(faulty)

        repeat(2) { file.read() }

        assertArrayEquals(faulty, file.path.readBytes())
    }
}
