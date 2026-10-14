// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.groups.GroupsMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

/**
 * Le système du mod dans le jeu (`groups.md` des specs, chapitres 1 à 4) : le jeu de règles et les nœuds que donne un groupe, la permission
 * propre qui l'emporte, les inscriptions invalides, la source `external`, et le rechargement des groupes.
 */
class GroupRightsGameTests {

    private companion object {

        /** Les jeux `vip`, `default` et `staff` des homes, écrits dans cet ordre, chacun avec sa limite. */
        const val HOMES = """{ "rules": { "vip": { "max-homes": 10 }, "default": { "max-homes": 2 }, "staff": { "max-homes": 50 } } }"""

        /** `vip` nomme son jeu de homes ; `staff` nomme le sien, accorde le rechargement et retire la création d'un home. */
        const val GROUPS = """{ "groups": { "default": {}, "vip": { "rules": { "homes": "vip" } }, "staff": { "rules": { "homes": "staff" }, "allow": ["manymanycommands.reload"], "deny": ["manymanycommands.homes.create"] } } }"""
    }

    /** `data\memberships.json` pour [entries], chaque joueur avec son groupe et, en plus, le reste de son entrée en JSON. */
    private fun memberships(vararg entries: Triple<ServerPlayer, String, String>): String =
        entries.joinToString(", ", prefix = """{ "players": { """, postfix = " } }") { (player, group, more) -> """"${player.uuid}": { "name": "${player.scoreboardName}", "group": "$group"$more }""" }

    private fun maxHomes(lab: Lab, source: CommandSourceStack): Long = lab.runtime.ruleSet(source, HomesRules)[HomesRules.maxHomes]

    /**
     * Un joueur a le jeu que nomme son groupe, dans la famille nommée et dans elle seule ; qui n'est inscrit nulle part, ou dans un groupe absent
     * des réglages, suit `default`, maître du jeu et console compris. Un groupe accorde un nœud à ses membres et leur en retire un autre, et
     * l'état dit le groupe absent où un joueur reste inscrit.
     */
    @GameTest
    fun groupGivesItsRuleSetAndItsNodes(helper: GameTestHelper) {
        val visitor = Harness.player(helper, "GroupVisitor")
        val vip = Harness.player(helper, "GroupVip")
        val staffer = Harness.player(helper, "GroupStaffer")
        val ghost = Harness.player(helper, "GroupGhost")
        val lab = Lab.open(
            "group-gives-its-rule-set-and-its-nodes", groups = GROUPS, rules = mapOf(HomesRules to HOMES),
            serverData = mapOf("memberships" to memberships(Triple(vip, "vip", ""), Triple(staffer, "staff", ""), Triple(ghost, "ghost", ""))),
        )

        helper.assertValueEqual(maxHomes(lab, visitor.createCommandSourceStack()), 2L, "la limite du joueur inscrit nulle part, celle du jeu default")
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 10L, "la limite du membre de vip, celle du jeu que nomme son groupe")
        helper.assertValueEqual(maxHomes(lab, staffer.createCommandSourceStack()), 50L, "la limite du membre de staff")
        helper.assertValueEqual(maxHomes(lab, ghost.createCommandSourceStack()), 2L, "la limite du joueur inscrit dans un groupe absent des réglages, celle du jeu default")
        helper.assertValueEqual(maxHomes(lab, Harness.asGameMaster(visitor)), 2L, "la limite du maître du jeu inscrit nulle part")
        helper.assertValueEqual(maxHomes(lab, Harness.console(helper)), 2L, "la limite de la console")
        helper.assertValueEqual(lab.runtime.ruleSet(vip.createCommandSourceStack(), WarpsRules), lab.runtime.rules(WarpsRules).default, "le jeu de warps du membre de vip, que son groupe ne nomme pas")

        val applied = Harness.FILES.map { ModMessages.RELOAD_FILE_APPLIED.key }
        val reload = Harness.keysSentTo(staffer, lab.runtime) { helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", staffer.createCommandSourceStack()), applied.size, "le rechargement par le membre de staff, à qui son groupe l'accorde") }
        helper.assertValueEqual(reload, applied + GroupsMessages.STATUS_MISSING_GROUP_ONE.key, "la réponse du rechargement au membre de staff, le groupe absent en dernier")
        Harness.assertUnknownCommand(helper, lab.dispatcher, vip.createCommandSourceStack(), "manymanycommands reload")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, staffer.createCommandSourceStack(), "homes create base")
        helper.assertTrue(lab.dispatcher.root.getChild("homes").getChild("create").canUse(visitor.createCommandSourceStack()), "la création d'un home reste au joueur inscrit nulle part")

        val status = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.console(helper)) }
        helper.assertValueEqual(status.last(), GroupsMessages.STATUS_MISSING_GROUP_ONE.key, "l'état dit le groupe absent des réglages où un joueur reste inscrit")
        helper.succeed()
    }

    /**
     * La permission propre d'un joueur l'emporte sur son groupe : le jeu propre remplace celui du groupe, même quand celui du groupe vient avant
     * dans le fichier de règles, et un nœud retiré en propre cache la commande que le groupe accorde.
     */
    @GameTest
    fun ownPermissionsBeatTheGroup(helper: GameTestHelper) {
        val ownSet = Harness.player(helper, "OwnSet")
        val denied = Harness.player(helper, "OwnDenied")
        val lab = Lab.open(
            "own-permissions-beat-the-group", groups = GROUPS, rules = mapOf(HomesRules to HOMES),
            serverData = mapOf("memberships" to memberships(Triple(ownSet, "vip", """, "allow": ["manymanycommands.rules.homes.staff"]"""), Triple(denied, "staff", """, "deny": ["manymanycommands.reload"]"""))),
        )

        helper.assertValueEqual(maxHomes(lab, ownSet.createCommandSourceStack()), 50L, "la limite du membre de vip qui a le jeu staff en propre, alors que vip vient avant dans le fichier")
        Harness.assertUnknownCommand(helper, lab.dispatcher, denied.createCommandSourceStack(), "manymanycommands reload")
        helper.assertValueEqual(maxHomes(lab, denied.createCommandSourceStack()), 50L, "le jeu du groupe reste à qui n'a pas de jeu propre")
        helper.succeed()
    }

    /** Des inscriptions invalides font de tout joueur un membre de `default`, sans permission propre, l'état le montre refusé, et le fichier corrigé puis rechargé rend les groupes. */
    @GameTest
    fun invalidMembershipsMakeEveryoneDefault(helper: GameTestHelper) {
        val vip = Harness.player(helper, "InvalidVip")
        val lab = Lab.open("invalid-memberships-make-everyone-default", groups = GROUPS, rules = mapOf(HomesRules to HOMES), serverData = mapOf("memberships" to "{\n  \"players\": {\n    \"Steve\": {}\n  }\n}"))

        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 2L, "la limite de tout joueur sur des inscriptions invalides, celle du jeu default")
        val status = Harness.record(lab.runtime) { lab.dispatcher.execute("manymanycommands", Harness.console(helper)) }.map { it.message }
        helper.assertTrue(status.contains(ModMessages.STATUS_FILE_REFUSED.with("data/memberships.json")), "l'état montre les inscriptions refusées")

        lab.writeServerData("memberships", memberships(Triple(vip, "vip", "")))
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))

        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 10L, "la limite du membre de vip, les inscriptions corrigées et rechargées")
        helper.succeed()
    }

    /** Sous un mod de permissions (`permission-source` à `external`), les groupes et les permissions propres sont ignorés : tout joueur suit le défaut du profil. */
    @GameTest
    fun externalSourceSilencesTheModSystem(helper: GameTestHelper) {
        val vip = Harness.player(helper, "ExternalVip")
        val lab = Lab.open(
            "external-source-silences-the-mod-system", settings = """{ "permission-source": "external" }""", groups = GROUPS, rules = mapOf(HomesRules to HOMES),
            serverData = mapOf("memberships" to memberships(Triple(vip, "staff", """, "allow": ["manymanycommands.reload"]"""))),
        )

        helper.assertFalse(lab.runtime.usesInternalRights, "le système du mod n'est pas la source des droits")
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 2L, "la limite du membre de staff sous un mod de permissions, celle du jeu default")
        Harness.assertUnknownCommand(helper, lab.dispatcher, vip.createCommandSourceStack(), "manymanycommands reload")
        helper.succeed()
    }

    /** Des groupes rechargés prennent effet tout de suite : le jeu renommé, puis le groupe retiré du fichier, qui renvoie ses membres à `default` et se dit dans la réponse. */
    @GameTest
    fun reloadedGroupsTakeEffectAtOnce(helper: GameTestHelper) {
        val vip = Harness.player(helper, "ReloadedVip")
        val lab = Lab.open("reloaded-groups-take-effect-at-once", groups = GROUPS, rules = mapOf(HomesRules to HOMES), serverData = mapOf("memberships" to memberships(Triple(vip, "vip", ""))))
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 10L, "la limite du membre de vip au départ")

        lab.writeGroups("""{ "groups": { "vip": { "rules": { "homes": "staff" } } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 50L, "la limite du membre de vip, son groupe rechargé nommant staff")

        lab.writeGroups("""{ "groups": { "default": { "rules": { "homes": "nowhere" } } } }""")
        val reload = Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands reload", Harness.console(helper)) }
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 2L, "la limite du joueur dont le groupe a quitté le fichier, celle du jeu default")
        helper.assertValueEqual(reload.takeLast(2), listOf(GroupsMessages.STATUS_MISSING_GROUP_ONE.key, GroupsMessages.STATUS_MISSING_RULE_SET.key), "le rechargement dit le groupe absent où le joueur reste inscrit, puis le jeu nommé absent des règles")
        helper.succeed()
    }
}
