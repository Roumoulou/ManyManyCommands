// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

import fr.roumoulou.manymanycommands.rules.back.BackRules
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le fichier de règles d'une famille (cahier des charges, chapitres 2.4.3, 2.5.9, 2.7.1, 2.7.3 et 2.7.4) : ses réglages du serveur, ses jeux, le
 * jeu `default`, ce qu'un jeu en reprend, et ses constats. Les homes servent d'exemple, le retour pour ses sources.
 */
class RulesTableTest {

    private val tables = listOf(HomesRules, WarpsRules, BackRules, WildRules, TpaRules)

    private fun sets(vararg sets: Pair<String, Map<String, Any?>>): WrittenRules = WrittenRules(sets = mapOf(*sets))

    // ── Le fichier créé ──

    @Test
    fun `le fichier créé écrit la version de schéma, chaque réglage du serveur et le jeu default entier, à leurs valeurs livrées`() {
        val file = HomesRules.shippedFile()

        assertEquals(RulesTable.SCHEMA_VERSION, file.schemaVersion)
        assertEquals(listOf("enabled" to true, "aliases" to true), file.settings.toList())
        assertEquals(listOf("default"), file.sets.keys.toList())
        val default = listOf("max-homes" to 3L, "forbidden-dimensions" to emptyList<String>(), "forbidden-departure-dimensions" to emptyList<String>(), "delay" to 3L, "cooldown" to 10L, "quota" to null)
        assertEquals(default, file.sets.getValue("default").toList())
    }

    @Test
    fun `le fichier créé de chaque famille est valide, et rend ses règles livrées`() {
        tables.forEach { table ->
            assertEquals(emptyList<FileFinding>(), table.findings(table.shippedFile()), "The shipped file of ${table.family} must be valid")
            assertEquals(table.shipped, table.values(table.shippedFile()), "The shipped file of ${table.family} must give its shipped rules")
        }
    }

    @Test
    fun `le fichier se nomme par son chemin dans le dossier du mod`() {
        assertEquals(listOf("rules/homes.json", "rules/warps.json", "rules/back.json", "rules/wild.json", "rules/tpa.json"), tables.map { it.fileName })
    }

    // ── Le jeu default et ce qu'un jeu en reprend ──

    @Test
    fun `le jeu default existe toujours, même quand le fichier ne l'écrit pas`() {
        assertEquals(listOf("default"), HomesRules.values(WrittenRules()).sets.keys.toList())
        assertEquals(HomesRules.shipped.default, HomesRules.values(sets("vip" to emptyMap())).default)
    }

    @Test
    fun `un réglage absent prend sa valeur livrée`() {
        val rules = HomesRules.values(WrittenRules(settings = mapOf(HomesRules.aliases.writes(false))))

        assertEquals(false, rules.settings[HomesRules.aliases])
        assertEquals(true, rules.settings[HomesRules.enabled])
    }

    @Test
    fun `une valeur absente du jeu default prend sa valeur livrée`() {
        val default = HomesRules.values(sets("default" to mapOf(HomesRules.delay.writes(0)))).default

        assertEquals(0L, default[HomesRules.delay])
        assertEquals(3L, default[HomesRules.maxHomes])
        assertEquals(10L, default[HomesRules.cooldown])
    }

    @Test
    fun `un jeu n'écrit que ce qui le distingue, et reprend le reste du jeu default`() {
        val rules = HomesRules.values(sets("default" to mapOf(HomesRules.delay.writes(5), HomesRules.maxHomes.writes(1)), "vip" to mapOf(HomesRules.maxHomes.writes(10), HomesRules.cooldown.writes(0))))
        val vip = rules.sets.getValue("vip")

        assertEquals(10L, vip[HomesRules.maxHomes])
        assertEquals(0L, vip[HomesRules.cooldown])
        assertEquals(5L, vip[HomesRules.delay], "A value the set does not write is the one of the default set")
        assertEquals(10L, rules.default[HomesRules.cooldown], "The default set keeps its own values")
    }

    @Test
    fun `un jeu reprend le jeu default où qu'il soit écrit dans le fichier`() {
        val rules = HomesRules.values(sets("vip" to emptyMap(), "default" to mapOf(HomesRules.delay.writes(7))))

        assertEquals(7L, rules.sets.getValue("vip")[HomesRules.delay])
    }

    @Test
    fun `les jeux gardent l'ordre du fichier, le jeu default après eux quand il n'est pas écrit`() {
        assertEquals(listOf("vip", "default", "staff"), HomesRules.values(sets("vip" to emptyMap(), "default" to emptyMap(), "staff" to emptyMap())).sets.keys.toList())
        assertEquals(listOf("staff", "vip", "default"), HomesRules.values(sets("staff" to emptyMap(), "vip" to emptyMap())).sets.keys.toList())
    }

    @Test
    fun `une liste de dimensions se reprend entière, et s'écrit en identifiants complets`() {
        val rules = HomesRules.values(sets("default" to mapOf(HomesRules.forbiddenDimensions.writes(listOf("the_end", "mymod:moon"))), "vip" to emptyMap(), "staff" to mapOf(HomesRules.forbiddenDimensions.writes(emptyList()))))

        assertEquals(setOf("minecraft:the_end", "mymod:moon"), rules.default[HomesRules.forbiddenDimensions])
        assertEquals(setOf("minecraft:the_end", "mymod:moon"), rules.sets.getValue("vip")[HomesRules.forbiddenDimensions])
        assertEquals(emptySet<String>(), rules.sets.getValue("staff")[HomesRules.forbiddenDimensions])
    }

    @Test
    fun `un jeu n'écrit que les sources du retour qui le distinguent`() {
        val rules = BackRules.values(sets("default" to mapOf(BackRules.sources.writes(mapOf("death" to true))), "vip" to mapOf(BackRules.sources.writes(mapOf("homes" to false)))))

        assertEquals(BackRules.sources.shipped + ("death" to true), rules.default[BackRules.sources])
        assertEquals(BackRules.sources.shipped + ("death" to true) + ("homes" to false), rules.sets.getValue("vip")[BackRules.sources])
    }

    // ── Le quota ──

    @Test
    fun `un jeu sans quota écrit reprend celui du jeu default, et un quota à null n'en a pas`() {
        val quota = WrittenQuota(uses = 3, period = 3_600, clock = "playtime")
        val rules = HomesRules.values(sets("default" to mapOf(HomesRules.quota.writes(quota)), "vip" to emptyMap(), "staff" to mapOf(HomesRules.quota.writes(null))))

        assertEquals(Quota(3, 3_600, QuotaClock.PLAYTIME), rules.default[HomesRules.quota])
        assertEquals(Quota(3, 3_600, QuotaClock.PLAYTIME), rules.sets.getValue("vip")[HomesRules.quota])
        assertNull(rules.sets.getValue("staff")[HomesRules.quota])
    }

    @Test
    fun `un quota ne reprend rien du jeu default, et son horloge absente est l'heure réelle`() {
        val rules = HomesRules.values(
            sets("default" to mapOf(HomesRules.quota.writes(WrittenQuota(uses = 3, period = 3_600, clock = "playtime"))), "vip" to mapOf(HomesRules.quota.writes(WrittenQuota(uses = 9, period = 60, clock = null)))),
        )

        assertEquals(Quota(9, 60, QuotaClock.REAL), rules.sets.getValue("vip")[HomesRules.quota])
    }

    @Test
    fun `un quota s'écrit entier, uses et period y sont obligatoires`() {
        val findings = HomesRules.findings(sets("vip" to mapOf(HomesRules.quota.writes(WrittenQuota(uses = null, period = null, clock = "real")))))

        val expected = listOf(FileFinding("rules[vip].quota", null, CommonMessages.FINDING_MISSING_KEY.with("uses")), FileFinding("rules[vip].quota", null, CommonMessages.FINDING_MISSING_KEY.with("period")))
        assertEquals(expected, findings)
    }

    @Test
    fun `l'horloge d'un quota est real ou playtime`() {
        val finding = HomesRules.findings(sets("default" to mapOf(HomesRules.quota.writes(WrittenQuota(uses = 1, period = 60, clock = "lunar"))))).single()

        assertEquals(FileFinding("rules[default].quota.clock", null, CommonMessages.FINDING_NOT_ALLOWED.with("lunar", "real, playtime")), finding)
    }

    // ── Le fichier invalide ──

    @Test
    fun `le nom d'un jeu s'écrit en minuscules, de 1 à 32 caractères`() {
        val allowed = CommonMessages.ALLOWED_LOWERCASE_NAME.with()
        val long = "a".repeat(33)

        val findings = HomesRules.findings(sets("VIP" to emptyMap(), "vip" to emptyMap(), "" to emptyMap(), long to emptyMap(), "42" to emptyMap()))

        val expected = listOf(
            FileFinding("rules[VIP]", null, CommonMessages.FINDING_NOT_ALLOWED.with("VIP", allowed)),
            FileFinding("rules[\"\"]", null, CommonMessages.FINDING_NOT_ALLOWED.with("", allowed)),
            FileFinding("rules[$long]", null, CommonMessages.FINDING_NOT_ALLOWED.with(long, allowed)),
        )
        assertEquals(expected, findings)
    }

    @Test
    fun `un fichier plus récent que le mod est invalide`() {
        val finding = HomesRules.findings(WrittenRules(schemaVersion = RulesTable.SCHEMA_VERSION + 1)).single()

        assertEquals(FileFinding("schema-version", null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(RulesTable.SCHEMA_VERSION + 1, RulesTable.SCHEMA_VERSION)), finding)
    }

    @Test
    fun `chaque valeur fautive a son constat, à son chemin dans le fichier`() {
        val written = WrittenRules(
            schemaVersion = 99,
            settings = mapOf(WildRules.timedWindow.writes(0)),
            sets = mapOf("default" to mapOf(WildRules.delay.writes(-1), WildRules.forbiddenDepartureDimensions.writes(listOf("the_end", "The End"))), "vip" to mapOf(WildRules.cooldown.writes(-5))),
        )

        val paths = WildRules.findings(written).map { it.path }

        assertEquals(listOf("schema-version", "timed-window", "rules[default].forbidden-departure-dimensions[1]", "rules[default].delay", "rules[vip].cooldown"), paths)
    }

    @Test
    fun `une valeur admise ne laisse aucun constat, dans le jeu default comme dans un autre`() {
        val written = sets("default" to mapOf(HomesRules.maxHomes.writes(0), HomesRules.delay.writes(0)), "vip" to mapOf(HomesRules.maxHomes.writes(50), HomesRules.quota.writes(WrittenQuota(uses = 1, period = 1, clock = "real"))))

        assertTrue(HomesRules.findings(written).isEmpty())
    }

    // ── Les réglages lus au seul démarrage ──

    @Test
    fun `l'activation de la famille et celle de ses alias attendent le redémarrage quand elles ne valent plus ce qu'elles valaient au démarrage`() {
        val startup = HomesRules.shipped
        fun reloaded(vararg settings: Pair<String, Any?>) = HomesRules.values(WrittenRules(settings = mapOf(*settings)))

        assertEquals(emptyList<String>(), HomesRules.awaitingRestart(startup, startup))
        assertEquals(listOf("enabled"), HomesRules.awaitingRestart(reloaded(HomesRules.enabled.writes(false)), startup))
        assertEquals(listOf("aliases"), HomesRules.awaitingRestart(reloaded(HomesRules.aliases.writes(false)), startup))
        assertEquals(listOf("enabled", "aliases"), HomesRules.awaitingRestart(reloaded(HomesRules.enabled.writes(false), HomesRules.aliases.writes(false)), startup))
    }

    @Test
    fun `les jeux de règles et les autres réglages prennent effet au rechargement et n'attendent rien`() {
        val reloaded = WildRules.values(WrittenRules(settings = mapOf(WildRules.timedWindow.writes(30)), sets = mapOf("default" to mapOf(WildRules.delay.writes(0)), "vip" to emptyMap())))

        assertEquals(emptyList<String>(), WildRules.awaitingRestart(reloaded, WildRules.shipped))
    }

    @Test
    fun `une famille sans alias n'attend que son activation`() {
        val disabled = BackRules.values(WrittenRules(settings = mapOf(BackRules.enabled.writes(false))))

        assertNull(BackRules.aliases)
        assertNull(TpaRules.aliases)
        assertEquals(listOf("enabled"), BackRules.awaitingRestart(disabled, BackRules.shipped))
    }
}
