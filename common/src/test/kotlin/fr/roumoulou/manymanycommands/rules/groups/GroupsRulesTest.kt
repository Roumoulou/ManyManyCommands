// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** `groups.json` en règles (`groups.md` des specs, chapitres 2 et 4) : le fichier livré, chaque constat à son chemin, le contenu d'un fichier valide. */
class GroupsRulesTest {

    private val notAllowedNode = CommonMessages.ALLOWED_COMMAND_NODE.with()

    @Test
    fun `le fichier livré ne porte que le groupe default, vide`() {
        assertEquals(WrittenGroups(1, mapOf("default" to WrittenGroup())), GroupsRules.shippedFile())
        assertEquals(Groups.SHIPPED, GroupsRules.groups(GroupsRules.shippedFile()))
        assertEquals(GroupDefinition(), Groups.SHIPPED.effective("vip"), "A group absent from the settings is treated as default")
    }

    @Test
    fun `un fichier valide donne ses groupes dans son ordre, et default existe même quand il ne l'écrit pas, après les autres`() {
        val written = WrittenGroups(groups = mapOf("vip" to WrittenGroup(mapOf("homes" to "vip", "wild" to "vip"), listOf("manymanycommands.warps.access.vip"), listOf("manymanycommands.tpahere"))))

        val groups = GroupsRules.groups(written)

        assertEquals(emptyList<FileFinding>(), GroupsRules.findings(written))
        assertEquals(listOf("vip", "default"), groups.names)
        assertEquals(GroupDefinition(mapOf("homes" to "vip", "wild" to "vip"), setOf("manymanycommands.warps.access.vip"), setOf("manymanycommands.tpahere")), groups["vip"])
        assertEquals(GroupDefinition(), groups.default())
        assertEquals(true, groups["vip"]?.verdict("manymanycommands.warps.access.vip"))
        assertEquals(false, groups["vip"]?.verdict("manymanycommands.tpahere"))
        assertNull(groups["vip"]?.verdict("manymanycommands.homes.create"))
    }

    @Test
    fun `default peut nommer des jeux et porter des nœuds comme les autres`() {
        val written = WrittenGroups(groups = mapOf("default" to WrittenGroup(mapOf("back" to "slow"), deny = listOf("manymanycommands.wild.timed"))))

        assertEquals(emptyList<FileFinding>(), GroupsRules.findings(written))
        assertEquals(GroupDefinition(mapOf("back" to "slow"), deny = setOf("manymanycommands.wild.timed")), GroupsRules.groups(written).effective(null))
    }

    @Test
    fun `le nom d'un groupe, une famille inconnue sous rules et un nom de jeu invalide se constatent à leur chemin`() {
        val written = WrittenGroups(groups = mapOf("Vip" to WrittenGroup(mapOf("homez" to "vip", "homes" to "Vip"))))

        val expected = listOf(
            FileFinding("groups[Vip]", null, CommonMessages.FINDING_NOT_ALLOWED.with("Vip", CommonMessages.ALLOWED_LOWERCASE_NAME.with())),
            FileFinding("groups[Vip].rules.homez", null, CommonMessages.FINDING_UNKNOWN_KEY.with()),
            FileFinding("groups[Vip].rules.homes", null, CommonMessages.FINDING_NOT_ALLOWED.with("Vip", CommonMessages.ALLOWED_LOWERCASE_NAME.with())),
        )
        assertEquals(expected, GroupsRules.findings(written))
    }

    @Test
    fun `un nœud inconnu, un nœud de jeu de règles et un nœud à la fois accordé et retiré se constatent à leur index`() {
        val written = WrittenGroups(groups = mapOf("vip" to WrittenGroup(allow = listOf("manymanycommands.homes.create", "manymanycommands.homes.fly", "manymanycommands.rules.homes.vip"), deny = listOf("manymanycommands.homes.create"))))

        val expected = listOf(
            FileFinding("groups[vip].allow[1]", null, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.homes.fly", notAllowedNode)),
            FileFinding("groups[vip].allow[2]", null, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.rules.homes.vip", notAllowedNode)),
            FileFinding("groups[vip].deny[0]", null, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.homes.create", CommonMessages.ALLOWED_NODE_ABSENT_FROM_ALLOW.with())),
        )
        assertEquals(expected, GroupsRules.findings(written))
    }

    @Test
    fun `une version de schéma plus récente que le mod se constate en premier`() {
        val written = WrittenGroups(schemaVersion = 2, groups = mapOf("Vip" to WrittenGroup()))

        assertEquals(FileFinding("schema-version", null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(2, 1)), GroupsRules.findings(written).first())
        assertEquals(2, GroupsRules.findings(written).size)
    }

    private fun Groups.default(): GroupDefinition = effective(null)
}
