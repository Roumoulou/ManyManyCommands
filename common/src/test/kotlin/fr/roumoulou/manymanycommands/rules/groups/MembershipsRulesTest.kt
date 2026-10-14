// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** `data\memberships.json` en règles (`groups.md` des specs, chapitres 3 et 4) : l'inscription d'un joueur, ses constats, ce qui s'écrit. */
class MembershipsRulesTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val alex = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")

    @Test
    fun `le fichier se nomme data memberships json`() {
        assertEquals("data/memberships.json", MembershipsRules.fileName)
    }

    @Test
    fun `une entrée porte le nom, le groupe et les nœuds propres, et un fichier valide s'écrit tel qu'il se relit, sans ses listes vides`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenMembership("Steve", "vip", listOf("manymanycommands.rules.homes.staff", "manymanycommands.reload"), null)))

        val data = MembershipsRules.data(written)

        assertEquals(emptyList<FileFinding>(), MembershipsRules.findings(written))
        assertEquals(PlayerEntry("Steve", Membership("vip", setOf("manymanycommands.rules.homes.staff", "manymanycommands.reload"))), data[steve])
        assertEquals(written, MembershipsRules.written(data))
    }

    @Test
    fun `le groupe default écrit vaut nulle part, et une inscription vide ne s'écrit pas`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenMembership("Steve", "default", null, null)))

        val data = MembershipsRules.data(written)

        assertEquals(PlayerEntry("Steve", Membership()), data[steve])
        assertTrue(Membership().isEmpty)
        assertEquals(WrittenData<WrittenMembership>(1, emptyMap()), MembershipsRules.written(data))
        assertEquals(Memberships(), data.with(steve, PlayerEntry("Steve", Membership("vip").inGroup("default"))))
    }

    @Test
    fun `un nom de groupe invalide, un nœud inconnu et un nœud à la fois accordé et retiré se constatent à leur chemin`() {
        val written = WrittenData(players = mapOf(steve.toString() to WrittenMembership("Steve", "Vip", listOf("manymanycommands.homes.fly", "manymanycommands.back"), listOf("manymanycommands.back"))))

        val expected = listOf(
            FileFinding("players[$steve].group", null, CommonMessages.FINDING_NOT_ALLOWED.with("Vip", CommonMessages.ALLOWED_LOWERCASE_NAME.with())),
            FileFinding("players[$steve].allow[0]", null, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.homes.fly", CommonMessages.ALLOWED_NODE.with())),
            FileFinding("players[$steve].deny[0]", null, CommonMessages.FINDING_NOT_ALLOWED.with("manymanycommands.back", CommonMessages.ALLOWED_NODE_ABSENT_FROM_ALLOW.with())),
        )
        assertEquals(expected, MembershipsRules.findings(written))
    }

    @Test
    fun `les permissions propres s'accordent, se retirent et s'effacent, un nœud n'étant jamais des deux côtés`() {
        val membership = Membership("vip").allowing("manymanycommands.back")

        assertEquals(true, membership.verdict("manymanycommands.back"))
        assertNull(membership.verdict("manymanycommands.wild"))
        assertEquals(Membership("vip", deny = setOf("manymanycommands.back")), membership.denying("manymanycommands.back"))
        assertEquals(Membership("vip"), membership.without("manymanycommands.back"))
        assertEquals(Membership("staff", allow = setOf("manymanycommands.back")), membership.inGroup("staff"))
        assertFalse(membership.hasOwnRuleSet("homes"))
        assertTrue(membership.allowing("manymanycommands.rules.homes.staff").hasOwnRuleSet("homes"))
        assertFalse(membership.allowing("manymanycommands.rules.homes.staff").hasOwnRuleSet("wild"))
    }

    @Test
    fun `le nombre d'inscrits se compte par groupe nommé, dans l'ordre du fichier`() {
        val data = Memberships(mapOf(steve to PlayerEntry("Steve", Membership("vip")), alex to PlayerEntry("Alex", Membership(allow = setOf("manymanycommands.back"))), UUID.randomUUID() to PlayerEntry(null, Membership("vip"))))

        assertEquals(mapOf("vip" to 2), MembershipsRules.countByGroup(data))
    }
}
