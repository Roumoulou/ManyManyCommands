// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.permission.RuleSetNode
import fr.roumoulou.manymanycommands.rules.players.PlayerRef
import fr.roumoulou.manymanycommands.rules.players.PlayerRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Les commandes `group` et `permission` en règles (`groups.md` des specs, chapitre 5) : chaque issue, chaque refus, et ce qui s'inscrit. */
class GroupsAdminTest {

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val alex = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")

    private val notch = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6")

    private val jeb = UUID.fromString("f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2")

    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    private val groups = Groups(
        mapOf(
            "default" to GroupDefinition(),
            "vip" to GroupDefinition(mapOf("homes" to "vip"), allow = setOf("manymanycommands.reload")),
            "staff" to GroupDefinition(mapOf("homes" to "staff", "wild" to "staff"), deny = setOf("manymanycommands.homes.create")),
        ),
    )

    /** Steve et Alex sont vip, Alex avec un nœud propre, Notch est inscrit dans un groupe absent des réglages, Jeb nulle part. */
    private val memberships = Memberships(mapOf(steve to PlayerEntry("Steve", Membership("vip")), alex to PlayerEntry("Alex", Membership("vip", allow = setOf("manymanycommands.back"))), notch to PlayerEntry("Notch", Membership("ghost"))))

    private val registry = PlayerRegistry().joined(steve, "Steve", noon).joined(alex, "Alex", noon).joined(notch, "Notch", noon).joined(jeb, "Jeb", noon)

    private val jebRef = PlayerRef(jeb, "Jeb")

    private val alexRef = PlayerRef(alex, "Alex")

    /** Seul le jeu `vip` des homes existe. */
    private val hasRuleSet: (RuleSetNode) -> Boolean = { it == RuleSetNode("homes", "vip") }

    @Test
    fun `la liste donne chaque groupe du fichier avec son nombre de membres, default comptant les joueurs du registre qui le suivent`() {
        val expected = listOf(GroupsMessages.LIST_GROUP_MANY.with("default", 2), GroupsMessages.LIST_GROUP_MANY.with("vip", 2), GroupsMessages.LIST_GROUP_MANY.with("staff", 0))
        assertEquals(expected, GroupsAdmin.list(groups, memberships, registry))

        val one = Memberships(mapOf(steve to PlayerEntry("Steve", Membership("vip"))))
        assertEquals(GroupsMessages.LIST_GROUP_ONE.with("vip", 1), GroupsAdmin.list(groups, one, registry)[1])
        assertEquals(GroupsMessages.LIST_GROUP_MANY.with("default", 0), GroupsAdmin.list(groups, memberships, registry = null).first(), "Without a registry, nobody is counted in default")
    }

    @Test
    fun `le détail d'un groupe donne son jeu par famille, ses nœuds et ses membres nommés par le registre, par leur UUID à défaut`() {
        val vip = GroupsAdmin.info("vip", groups, memberships, registry)

        val expected = listOf(
            GroupsMessages.INFO_RULE_SET.with("homes", "vip"),
            GroupsMessages.INFO_RULE_SET.with("warps", "default"),
            GroupsMessages.INFO_RULE_SET.with("back", "default"),
            GroupsMessages.INFO_RULE_SET.with("wild", "default"),
            GroupsMessages.INFO_RULE_SET.with("tpa", "default"),
            GroupsMessages.INFO_ALLOW.with("manymanycommands.reload"),
            GroupsMessages.INFO_DENY_NONE.with(),
            GroupsMessages.INFO_MEMBERS.with("Steve, Alex"),
        )
        assertEquals(GroupsOutcome.Shown(expected), vip)
        val default = GroupsAdmin.info("default", groups, memberships, registry) as GroupsOutcome.Shown
        assertEquals(GroupsMessages.INFO_MEMBERS.with("Notch, Jeb"), default.messages.last(), "The members of default follow it: registered nowhere, or in a group absent from the settings")
        val staff = GroupsAdmin.info("staff", groups, memberships, registry = null) as GroupsOutcome.Shown
        assertEquals(listOf(GroupsMessages.INFO_ALLOW_NONE.with(), GroupsMessages.INFO_DENY.with("manymanycommands.homes.create"), GroupsMessages.INFO_MEMBERS_NONE.with()), staff.messages.drop(5))
        val unnamed = GroupsAdmin.info("vip", groups, memberships, registry = null) as GroupsOutcome.Shown
        assertEquals(GroupsMessages.INFO_MEMBERS.with("$steve, $alex"), unnamed.messages.last())
        assertEquals(GroupsOutcome.Refused(GroupsMessages.UNKNOWN_GROUP.with("ghost")), GroupsAdmin.info("ghost", groups, memberships, registry))
    }

    @Test
    fun `set inscrit à la place du précédent, garde les permissions propres, refuse le groupe inconnu, et default comme reset renvoient nulle part`() {
        val joined = GroupsAdmin.set(jebRef, "staff", groups, memberships)
        assertEquals(GroupsOutcome.Changed(memberships.with(jeb, PlayerEntry("Jeb", Membership("staff"))), GroupsMessages.JOINED.with("Jeb", "staff")), joined)

        val moved = GroupsAdmin.set(alexRef, "staff", groups, memberships) as GroupsOutcome.Changed
        assertEquals(PlayerEntry("Alex", Membership("staff", allow = setOf("manymanycommands.back"))), moved.data[alex])

        assertEquals(GroupsOutcome.Refused(GroupsMessages.UNKNOWN_GROUP.with("ghost")), GroupsAdmin.set(jebRef, "ghost", groups, memberships))

        val toDefault = GroupsAdmin.set(PlayerRef(steve, "Steve"), "default", groups, memberships)
        assertEquals(GroupsOutcome.Changed(Memberships(memberships.players - steve), GroupsMessages.JOINED.with("Steve", "default")), toDefault)
        assertEquals(toDefault, GroupsAdmin.reset(PlayerRef(steve, "Steve"), memberships))
    }

    @Test
    fun `of donne le groupe où le joueur est traité, default pour un groupe absent des réglages ou un joueur inscrit nulle part`() {
        assertEquals(GroupsMessages.GROUP_OF.with("Steve", "vip"), GroupsAdmin.of(PlayerRef(steve, "Steve"), groups, memberships))
        assertEquals(GroupsMessages.GROUP_OF.with("Notch", "default"), GroupsAdmin.of(PlayerRef(notch, "Notch"), groups, memberships))
        assertEquals(GroupsMessages.GROUP_OF.with("Jeb", "default"), GroupsAdmin.of(jebRef, groups, memberships))
    }

    @Test
    fun `allow et deny acceptent un nœud de commande, un jeu qui existe et une clé d'accès, et refusent le nœud inconnu et le jeu absent`() {
        assertEquals(GroupsOutcome.Changed(memberships.with(jeb, PlayerEntry("Jeb", Membership(allow = setOf("manymanycommands.reload")))), GroupsMessages.ALLOWED.with("manymanycommands.reload", "Jeb")), GroupsAdmin.allow(jebRef, "manymanycommands.reload", memberships, hasRuleSet))
        assertEquals(GroupsMessages.ALLOWED.with("manymanycommands.rules.homes.vip", "Jeb"), (GroupsAdmin.allow(jebRef, "manymanycommands.rules.homes.vip", memberships, hasRuleSet) as GroupsOutcome.Changed).message)
        assertEquals(GroupsMessages.ALLOWED.with("manymanycommands.warps.access.secret", "Jeb"), (GroupsAdmin.allow(jebRef, "manymanycommands.warps.access.secret", memberships, hasRuleSet) as GroupsOutcome.Changed).message)
        assertEquals(GroupsOutcome.Refused(GroupsMessages.UNKNOWN_NODE.with("manymanycommands.rules.homes.staff")), GroupsAdmin.allow(jebRef, "manymanycommands.rules.homes.staff", memberships, hasRuleSet))
        assertEquals(GroupsOutcome.Refused(GroupsMessages.UNKNOWN_NODE.with("manymanycommands.homes.fly")), GroupsAdmin.allow(jebRef, "manymanycommands.homes.fly", memberships, hasRuleSet))

        val denied = GroupsAdmin.deny(alexRef, "manymanycommands.back", memberships, hasRuleSet)
        assertEquals(GroupsOutcome.Changed(memberships.with(alex, PlayerEntry("Alex", Membership("vip", deny = setOf("manymanycommands.back")))), GroupsMessages.DENIED.with("manymanycommands.back", "Alex")), denied, "A node denied is no longer allowed")
        assertEquals(GroupsOutcome.Refused(GroupsMessages.UNKNOWN_NODE.with("manymanycommands.homes.fly")), GroupsAdmin.deny(alexRef, "manymanycommands.homes.fly", memberships, hasRuleSet))
    }

    @Test
    fun `unset efface la permission propre, quel que soit le nœud, et la liste des permissions propres donne chaque nœud ou l'absence`() {
        assertEquals(GroupsOutcome.Changed(memberships.with(alex, PlayerEntry("Alex", Membership("vip"))), GroupsMessages.UNSET.with("Alex", "manymanycommands.back")), GroupsAdmin.unset(alexRef, "manymanycommands.back", memberships))
        assertEquals(GroupsOutcome.Changed(memberships, GroupsMessages.UNSET.with("Jeb", "manymanycommands.homes.fly")), GroupsAdmin.unset(jebRef, "manymanycommands.homes.fly", memberships), "An unknown node is unset like any other, and an empty membership is not written")

        assertEquals(listOf<Message>(GroupsMessages.PERMISSION_ALLOWED.with("manymanycommands.back")), GroupsAdmin.permissions(alexRef, memberships))
        val both = memberships.with(alex, PlayerEntry("Alex", Membership("vip", setOf("manymanycommands.back"), setOf("manymanycommands.wild"))))
        assertEquals(listOf(GroupsMessages.PERMISSION_ALLOWED.with("manymanycommands.back"), GroupsMessages.PERMISSION_DENIED.with("manymanycommands.wild")), GroupsAdmin.permissions(alexRef, both))
        assertEquals(listOf(GroupsMessages.PERMISSION_NONE.with("Jeb")), GroupsAdmin.permissions(jebRef, memberships))
    }
}
