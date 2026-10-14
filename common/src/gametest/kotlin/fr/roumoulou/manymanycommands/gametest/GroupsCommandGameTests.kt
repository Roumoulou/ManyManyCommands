// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.groups.GroupsMessages
import fr.roumoulou.manymanycommands.rules.groups.Membership
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import kotlin.io.path.readText

/**
 * Les branches `group` et `permission` de `/manymanycommands` (`groups.md` des specs, chapitres 5 et 6), jouées par un maître du jeu et par la
 * console : chaque situation du tableau des messages que ces commandes produisent a son test, reconnue à sa clé.
 */
class GroupsCommandGameTests {

    private companion object {

        /** Les jeux `vip`, `default` et `staff` des homes, écrits dans cet ordre, chacun avec sa limite. */
        const val HOMES = """{ "rules": { "vip": { "max-homes": 10 }, "default": { "max-homes": 2 }, "staff": { "max-homes": 50 } } }"""

        /** `vip` nomme son jeu de homes ; `staff` nomme le sien, accorde le rechargement et retire la création d'un home. */
        const val GROUPS = """{ "groups": { "default": {}, "vip": { "rules": { "homes": "vip" } }, "staff": { "rules": { "homes": "staff" }, "allow": ["manymanycommands.reload"], "deny": ["manymanycommands.homes.create"] } } }"""
    }

    /** Ce qu'une commande a rendu, et les messages que chaque destinataire a reçus. */
    private class Outcome(val result: Int, val deliveries: List<Delivery>) {

        fun to(player: ServerPlayer?): List<Message> = deliveries.filter { it.player === player }.map { it.message }
    }

    private fun run(lab: Lab, source: CommandSourceStack, command: String): Outcome {
        var result = 0
        val deliveries = Harness.record(lab.runtime) { result = lab.dispatcher.execute(command, source) }
        return Outcome(result, deliveries)
    }

    /** `data\players.json` pour [players], connectés, et [others], des joueurs qui ne le sont pas, par leur UUID et leur nom. */
    private fun registry(players: List<ServerPlayer>, vararg others: Pair<UUID, String>): String {
        val entries = players.map { it.uuid to it.scoreboardName } + others
        return entries.joinToString(", ", prefix = """{ "players": { """, postfix = " } }") { (id, name) -> """"$id": { "name": "$name", "last-seen": "2026-10-05T12:00:00Z" }""" }
    }

    /** `data\memberships.json` pour [entries], chaque joueur avec son groupe et, en plus, le reste de son entrée en JSON. */
    private fun memberships(vararg entries: Triple<ServerPlayer, String, String>): String =
        entries.joinToString(", ", prefix = """{ "players": { """, postfix = " } }") { (player, group, more) -> """"${player.uuid}": { "name": "${player.scoreboardName}", "group": "$group"$more }""" }

    private fun membershipOf(lab: Lab, player: UUID): Membership? = lab.runtime.serverData.memberships.content?.get(player)?.holdings

    private fun maxHomes(lab: Lab, player: ServerPlayer): Long = lab.runtime.ruleSet(player.createCommandSourceStack(), HomesRules)[HomesRules.maxHomes]

    /**
     * Les situations « liste des groupes » et « détail d'un groupe » : chaque groupe du fichier avec son nombre de membres, `default` comptant les
     * joueurs du registre qui le suivent ; le jeu de chaque famille, les nœuds et les membres d'un groupe, nommés par le registre ; le groupe
     * inconnu.
     */
    @GameTest
    fun listAndInfoShowTheGroups(helper: GameTestHelper) {
        val first = Harness.player(helper, "GroupListA")
        val second = Harness.player(helper, "GroupListB")
        val ghost = Harness.player(helper, "GroupListC")
        val offline = UUID.randomUUID()
        val lab = Lab.open(
            "list-and-info-show-the-groups", groups = GROUPS, rules = mapOf(HomesRules to HOMES),
            serverData = mapOf("players" to registry(listOf(first, second, ghost), offline to "GroupListOffline"), "memberships" to memberships(Triple(first, "vip", ""), Triple(second, "vip", ""), Triple(ghost, "ghost", ""))),
        )
        val console = Harness.console(helper)

        val list = run(lab, console, "manymanycommands group list")
        val vip = run(lab, console, "manymanycommands group info vip")
        val staff = run(lab, console, "manymanycommands group info staff")
        val unknown = run(lab, console, "manymanycommands group info ghost")

        helper.assertValueEqual(list.result, 3, "le résultat de group list, le nombre de groupes")
        helper.assertValueEqual(list.to(null), listOf(GroupsMessages.LIST_GROUP_MANY.with("default", 2), GroupsMessages.LIST_GROUP_MANY.with("vip", 2), GroupsMessages.LIST_GROUP_MANY.with("staff", 0)), "la liste des groupes, default comptant le joueur du groupe absent et le joueur hors ligne du registre")
        helper.assertValueEqual(list.to(null).first().type, MessageType.DISPLAY, "la liste est un affichage")
        val expectedVip = listOf("homes" to "vip", "warps" to "default", "back" to "default", "wild" to "default", "tpa" to "default").map { (family, set) -> GroupsMessages.INFO_RULE_SET.with(family, set) } +
            listOf(GroupsMessages.INFO_ALLOW_NONE.with(), GroupsMessages.INFO_DENY_NONE.with(), GroupsMessages.INFO_MEMBERS.with("GroupListA, GroupListB"))
        helper.assertValueEqual(vip.to(null), expectedVip, "le détail du groupe vip")
        helper.assertValueEqual(staff.to(null).drop(5), listOf(GroupsMessages.INFO_ALLOW.with("manymanycommands.reload"), GroupsMessages.INFO_DENY.with("manymanycommands.homes.create"), GroupsMessages.INFO_MEMBERS_NONE.with()), "les nœuds et les membres du groupe staff")
        helper.assertValueEqual(unknown.to(null), listOf(GroupsMessages.UNKNOWN_GROUP.with("ghost")), "la situation « groupe inconnu »")
        helper.assertValueEqual(unknown.result, 0, "le résultat d'un refus")
        helper.succeed()
    }

    /**
     * Les situations « joueur inscrit », « joueur renvoyé dans default » et « groupe d'un joueur » : l'inscription écrite tout de suite dans
     * `data\memberships.json`, journalisée, qui donne au joueur le jeu de son groupe ; `<player>` lu sans la casse, et résolu par le registre pour
     * un joueur hors ligne ; l'état déjà en place qui réussit ; les situations « groupe inconnu » et « joueur inconnu ».
     */
    @GameTest
    fun setResetAndOfChangeTheMembership(helper: GameTestHelper) {
        val admin = Harness.player(helper, "GroupSetAdmin")
        val vip = Harness.player(helper, "GroupSetVip")
        val offline = UUID.randomUUID()
        val lab = Lab.open("set-reset-and-of-change-the-membership", groups = GROUPS, rules = mapOf(HomesRules to HOMES), serverData = mapOf("players" to registry(listOf(admin, vip), offline to "GroupSetOffline")))
        val source = Harness.asGameMaster(admin)
        val file = lab.directory.resolve("data").resolve("memberships.json")

        val joined = run(lab, source, "manymanycommands group set groupsetvip vip")
        helper.assertValueEqual(joined.result, 1, "le résultat de group set")
        helper.assertValueEqual(joined.to(admin), listOf(GroupsMessages.JOINED.with("GroupSetVip", "vip")), "la situation « joueur inscrit », sous le nom que le joueur porte")
        helper.assertValueEqual(joined.to(admin).single().type, MessageType.SUCCESS, "l'inscription est un succès")
        helper.assertValueEqual(membershipOf(lab, vip.uuid) ?: throw helper.assertionException("le joueur inscrit n'a pas d'inscription"), Membership("vip"), "l'inscription en mémoire")
        helper.assertTrue(file.readText().contains("\"${vip.uuid}\"") && file.readText().contains("\"vip\""), "l'inscription est écrite tout de suite dans data/memberships.json")
        helper.assertValueEqual(maxHomes(lab, vip), 10L, "le joueur inscrit a le jeu de son groupe tout de suite")
        helper.assertValueEqual(run(lab, source, "manymanycommands group of GroupSetVip").to(admin), listOf(GroupsMessages.GROUP_OF.with("GroupSetVip", "vip")), "la situation « groupe d'un joueur »")
        helper.assertValueEqual(run(lab, source, "manymanycommands group set GroupSetVip vip").to(admin), listOf(GroupsMessages.JOINED.with("GroupSetVip", "vip")), "l'état déjà en place réussit")
        helper.assertValueEqual(run(lab, source, "manymanycommands group set GroupSetVip ghost").to(admin), listOf(GroupsMessages.UNKNOWN_GROUP.with("ghost")), "la situation « groupe inconnu »")
        helper.assertValueEqual(run(lab, source, "manymanycommands group set Herobrine vip").to(admin), listOf(GroupsMessages.UNKNOWN_PLAYER.with("Herobrine")), "la situation « joueur inconnu », avec le nom tapé")
        helper.assertValueEqual(run(lab, source, "manymanycommands group set groupsetoffline staff").to(admin), listOf(GroupsMessages.JOINED.with("GroupSetOffline", "staff")), "un joueur hors ligne se désigne par le registre, sous son dernier nom connu")
        helper.assertValueEqual(membershipOf(lab, offline) ?: throw helper.assertionException("le joueur hors ligne n'a pas d'inscription"), Membership("staff"), "l'inscription du joueur hors ligne")

        val recording = Harness.RecordingSource()
        lab.dispatcher.execute("manymanycommands group reset GroupSetVip", source.withSource(recording))
        helper.assertValueEqual(recording.journalRequests, 1, "le renvoi dans default se journalise")
        lab.dispatcher.execute("manymanycommands group of GroupSetVip", source.withSource(recording))
        helper.assertValueEqual(recording.journalRequests, 1, "le groupe d'un joueur ne se journalise pas")
        helper.assertTrue(membershipOf(lab, vip.uuid) == null, "le joueur renvoyé dans default n'a plus d'entrée")
        helper.assertValueEqual(maxHomes(lab, vip), 2L, "le joueur renvoyé dans default a le jeu default")
        helper.assertValueEqual(run(lab, source, "manymanycommands group reset GroupSetVip").to(admin), listOf(GroupsMessages.JOINED.with("GroupSetVip", "default")), "la situation « joueur renvoyé dans default »")
        helper.succeed()
    }

    /**
     * Les situations « nœud accordé », « nœud retiré », « permission propre effacée », « permissions propres d'un joueur » et « aucune permission
     * propre » : chaque changement prend effet tout de suite dans les commandes et les jeux de règles ; le jeu d'une famille s'accepte quand il
     * existe, la clé d'accès d'un warp pour toute clé ; la situation « nœud inconnu ».
     */
    @GameTest
    fun permissionCommandsChangeTheOwnNodes(helper: GameTestHelper) {
        val admin = Harness.player(helper, "PermAdmin")
        val player = Harness.player(helper, "PermPlayer")
        val lab = Lab.open("permission-commands-change-the-own-nodes", groups = GROUPS, rules = mapOf(HomesRules to HOMES), serverData = mapOf("players" to registry(listOf(admin, player))))
        val source = Harness.asGameMaster(admin)

        helper.assertValueEqual(run(lab, source, "manymanycommands permission list PermPlayer").to(admin), listOf(GroupsMessages.PERMISSION_NONE.with("PermPlayer")), "la situation « aucune permission propre »")
        val allowed = run(lab, source, "manymanycommands permission allow PermPlayer manymanycommands.reload")
        helper.assertValueEqual(allowed.to(admin), listOf(GroupsMessages.ALLOWED.with("manymanycommands.reload", "PermPlayer")), "la situation « nœud accordé »")
        helper.assertValueEqual(allowed.to(admin).single().type, MessageType.SUCCESS, "le nœud accordé est un succès")
        helper.assertValueEqual(run(lab, player.createCommandSourceStack(), "manymanycommands reload").result, Harness.FILES.size, "le joueur recharge avec le nœud accordé en propre")
        helper.assertValueEqual(run(lab, source, "manymanycommands permission deny PermPlayer manymanycommands.homes.create").to(admin), listOf(GroupsMessages.DENIED.with("manymanycommands.homes.create", "PermPlayer")), "la situation « nœud retiré »")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, player.createCommandSourceStack(), "homes create base")
        val listed = run(lab, source, "manymanycommands permission list PermPlayer")
        helper.assertValueEqual(listed.to(admin), listOf(GroupsMessages.PERMISSION_ALLOWED.with("manymanycommands.reload"), GroupsMessages.PERMISSION_DENIED.with("manymanycommands.homes.create")), "la situation « permissions propres d'un joueur »")
        helper.assertValueEqual(listed.result, 2, "le résultat de permission list, le nombre de permissions propres")
        helper.assertValueEqual(run(lab, source, "manymanycommands permission unset PermPlayer manymanycommands.reload").to(admin), listOf(GroupsMessages.UNSET.with("PermPlayer", "manymanycommands.reload")), "la situation « permission propre effacée »")
        Harness.assertUnknownCommand(helper, lab.dispatcher, player.createCommandSourceStack(), "manymanycommands reload")

        helper.assertValueEqual(run(lab, source, "manymanycommands permission allow PermPlayer manymanycommands.homes.fly").to(admin), listOf(GroupsMessages.UNKNOWN_NODE.with("manymanycommands.homes.fly")), "la situation « nœud inconnu »")
        helper.assertValueEqual(run(lab, source, "manymanycommands permission allow PermPlayer manymanycommands.rules.homes.nowhere").to(admin), listOf(GroupsMessages.UNKNOWN_NODE.with("manymanycommands.rules.homes.nowhere")), "le jeu que le fichier de règles ne porte pas est un nœud inconnu")
        helper.assertValueEqual(run(lab, source, "manymanycommands permission allow PermPlayer manymanycommands.rules.homes.staff").to(admin), listOf(GroupsMessages.ALLOWED.with("manymanycommands.rules.homes.staff", "PermPlayer")), "le jeu qui existe s'accorde")
        helper.assertValueEqual(maxHomes(lab, player), 50L, "le jeu accordé en propre s'applique tout de suite")
        helper.assertValueEqual(run(lab, source, "manymanycommands permission allow PermPlayer manymanycommands.warps.access.secret").to(admin), listOf(GroupsMessages.ALLOWED.with("manymanycommands.warps.access.secret", "PermPlayer")), "la clé d'accès d'un warp s'accorde pour toute clé bien écrite")
        helper.assertValueEqual(membershipOf(lab, player.uuid) ?: throw helper.assertionException("le joueur n'a pas d'inscription"), Membership(allow = setOf("manymanycommands.rules.homes.staff", "manymanycommands.warps.access.secret"), deny = setOf("manymanycommands.homes.create")), "l'inscription du joueur après ces commandes")
        helper.succeed()
    }

    /** Les situations « système inactif », sous un mod de permissions, et « inscriptions indisponibles », sur un fichier invalide, pour une commande de chaque branche. */
    @GameTest
    fun commandsRefuseWhenInactiveOrUnavailable(helper: GameTestHelper) {
        val player = Harness.player(helper, "GroupRefused")
        val inactive = Lab.open("groups-commands-refuse-when-inactive", settings = """{ "permission-source": "external" }""", groups = GROUPS)
        val unavailable = Lab.open("groups-commands-refuse-when-unavailable", groups = GROUPS, serverData = mapOf("memberships" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))
        val console = Harness.console(helper)

        val inactiveList = run(inactive, console, "manymanycommands group list")
        helper.assertValueEqual(inactiveList.to(null), listOf(GroupsMessages.INACTIVE.with()), "la situation « système inactif »")
        helper.assertValueEqual(inactiveList.to(null).single().type, MessageType.REFUSAL, "un refus")
        helper.assertValueEqual(inactiveList.result, 0, "le résultat d'un refus")
        helper.assertValueEqual(run(inactive, console, "manymanycommands permission allow GroupRefused manymanycommands.back").to(null), listOf(GroupsMessages.INACTIVE.with()), "la même situation pour la branche permission")
        helper.assertValueEqual(run(unavailable, console, "manymanycommands group set GroupRefused vip").to(null), listOf(GroupsMessages.UNAVAILABLE.with()), "la situation « inscriptions indisponibles »")
        helper.assertValueEqual(run(unavailable, console, "manymanycommands permission list GroupRefused").to(null), listOf(GroupsMessages.UNAVAILABLE.with()), "la même situation pour la branche permission")
        helper.succeed()
    }

    /**
     * Les nœuds des neuf commandes : un joueur sans nœud ne voit pas `/manymanycommands` ; avec le seul nœud de `group of`, il lance cette
     * commande, les autres sous-commandes sont pour lui un argument incorrect, et la commande seule, sans le nœud de l'état, est inconnue.
     */
    @GameTest
    fun commandsFollowTheirNodes(helper: GameTestHelper) {
        val visitor = Harness.player(helper, "GroupNodesA")
        val asker = Harness.player(helper, "GroupNodesB")
        val lab = Lab.open("groups-commands-follow-their-nodes", groups = GROUPS, serverData = mapOf("players" to registry(listOf(visitor, asker)), "memberships" to memberships(Triple(asker, "default", """, "allow": ["manymanycommands.group.of"]"""))))

        Harness.assertUnknownCommand(helper, lab.dispatcher, visitor.createCommandSourceStack(), "manymanycommands group of GroupNodesB")
        helper.assertValueEqual(run(lab, asker.createCommandSourceStack(), "manymanycommands group of GroupNodesA").to(asker), listOf(GroupsMessages.GROUP_OF.with("GroupNodesA", "default")), "le joueur qui a le seul nœud de group of la lance")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, asker.createCommandSourceStack(), "manymanycommands group list")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, asker.createCommandSourceStack(), "manymanycommands permission list GroupNodesA")
        Harness.assertUnknownCommand(helper, lab.dispatcher, asker.createCommandSourceStack(), "manymanycommands")
        helper.assertTrue(lab.dispatcher.root.getChild("manymanycommands").getChild("group").getChild("set").canUse(Harness.asGameMaster(visitor)), "le maître du jeu a chaque sous-commande par défaut")
        helper.assertTrue(lab.dispatcher.root.getChild("mmc").getChild("permission").getChild("allow").canUse(Harness.console(helper)), "l'alias porte les deux branches, que la console a")
        helper.succeed()
    }

    /** La complétion : `<player>` par les joueurs connectés, `<group>` par les groupes de `groups.json`, `<node>` par les nœuds du mod, les jeux de règles en vigueur compris. */
    @GameTest
    fun argumentsCompleteWithPlayersGroupsAndNodes(helper: GameTestHelper) {
        val admin = Harness.player(helper, "GroupCompleteAdmin")
        val other = Harness.player(helper, "GroupCompleteOther")
        val lab = Lab.open("groups-arguments-complete", groups = GROUPS, rules = mapOf(HomesRules to HOMES))
        val dispatcher = lab.dispatcher
        val source = Harness.asGameMaster(admin)

        val players = dispatcher.getCompletionSuggestions(dispatcher.parse("manymanycommands group set ", source)).join().list.map { it.text }
        val groups = dispatcher.getCompletionSuggestions(dispatcher.parse("manymanycommands group set GroupCompleteOther ", source)).join().list.map { it.text }
        val nodes = dispatcher.getCompletionSuggestions(dispatcher.parse("manymanycommands permission allow GroupCompleteOther ", source)).join().list.map { it.text }

        helper.assertTrue(listOf(admin.scoreboardName, other.scoreboardName).all { it in players }, "la complétion de <player> propose les joueurs connectés")
        helper.assertValueEqual(groups.toSet(), setOf("default", "vip", "staff"), "la complétion de <group> propose les groupes du fichier")
        helper.assertTrue(listOf("manymanycommands.homes.create", "manymanycommands.group.set", "manymanycommands.bypass.quota", "manymanycommands.rules.homes.vip", "manymanycommands.rules.homes.staff").all { it in nodes }, "la complétion de <node> propose les nœuds du mod et les jeux de règles en vigueur")
        helper.assertFalse("manymanycommands.rules.homes.nowhere" in nodes, "un jeu que le fichier ne porte pas n'est pas proposé")
        helper.succeed()
    }
}
