// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Ce que l'état et le rechargement disent des groupes (`groups.md` des specs, chapitre 6) : le groupe absent où des joueurs restent inscrits, le jeu nommé absent des règles. */
class GroupsReportTest {

    private val groups = Groups(mapOf("default" to GroupDefinition(), "vip" to GroupDefinition(mapOf("homes" to "vip", "wild" to "vip")), "staff" to GroupDefinition(mapOf("homes" to "staff"))))

    private val memberships = Memberships(mapOf(UUID.randomUUID() to PlayerEntry("Steve", Membership("ghost")), UUID.randomUUID() to PlayerEntry("Alex", Membership("vip")), UUID.randomUUID() to PlayerEntry("Notch", Membership("ghost")), UUID.randomUUID() to PlayerEntry("Jeb", Membership("former"))))

    private val sets = mapOf("homes" to setOf("default", "vip"), "wild" to setOf("default"))

    @Test
    fun `chaque groupe absent des réglages où des joueurs restent inscrits a sa ligne, avec leur nombre, un ou plusieurs`() {
        val lines = GroupsReport.lines(groups, memberships) { emptySet() }.take(2)

        assertEquals(listOf(GroupsMessages.STATUS_MISSING_GROUP_MANY.with(2, "ghost"), GroupsMessages.STATUS_MISSING_GROUP_ONE.with(1, "former")), lines)
        assertEquals(setOf(MessageType.DISPLAY), lines.map { it.type }.toSet())
    }

    @Test
    fun `chaque jeu qu'un groupe nomme et que les règles de sa famille ne portent pas a sa ligne, dans l'ordre des groupes`() {
        val lines = GroupsReport.lines(groups, Memberships()) { sets[it].orEmpty() }

        assertEquals(listOf(GroupsMessages.STATUS_MISSING_RULE_SET.with("vip", "vip", "wild"), GroupsMessages.STATUS_MISSING_RULE_SET.with("staff", "staff", "homes")), lines)
    }

    @Test
    fun `rien ne manque, et des inscriptions indisponibles ne disent rien des groupes absents`() {
        assertEquals(emptyList<Message>(), GroupsReport.lines(Groups.SHIPPED, memberships = null) { emptySet() })
        assertEquals(emptyList<Message>(), GroupsReport.lines(Groups(mapOf("default" to GroupDefinition(mapOf("homes" to "vip")))), memberships) { sets[it].orEmpty() }.filter { it.key == GroupsMessages.STATUS_MISSING_RULE_SET.key })
        assertEquals(2, GroupsReport.lines(groups, null) { sets[it].orEmpty() }.size, "Only the rule sets speak while the memberships are unavailable")
    }
}
