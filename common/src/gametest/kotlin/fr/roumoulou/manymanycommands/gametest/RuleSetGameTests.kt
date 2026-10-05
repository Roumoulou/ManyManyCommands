// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper

/** Le jeu de règles d'un joueur dans le jeu (cahier des charges, chapitre 2.4.3) : c'est la source des droits qui le lui attribue, par un nœud. */
class RuleSetGameTests {

    private companion object {

        /** Les jeux `vip`, `default` et `staff` des homes, écrits dans cet ordre, chacun avec sa limite. */
        const val HOMES = """{ "rules": { "vip": { "max-homes": 10 }, "default": { "max-homes": 2 }, "staff": { "max-homes": 50 } } }"""
    }

    /**
     * Un joueur sans nœud de jeu a le jeu `default`, maître du jeu compris : aucun profil ne reçoit ces nœuds. Le joueur à qui la source des droits
     * accorde le nœud d'un jeu a ce jeu, dans la famille du nœud et dans elle seule. Celui qui en reçoit plusieurs prend le premier dans l'ordre
     * du fichier.
     */
    @GameTest
    fun ruleSetFollowsTheNodesOfThePlayer(helper: GameTestHelper) {
        val visitor = Harness.player(helper)
        val vip = Harness.player(helper)
        val both = Harness.player(helper)
        val permissionSource = PermissionSource { source, node ->
            when {
                source.player === vip && node == HomesRules.setNode("vip") -> true
                source.player === both && (node == HomesRules.setNode("staff") || node == HomesRules.setNode("vip")) -> true
                else -> null
            }
        }
        val lab = Lab.open("rule-set-follows-the-nodes-of-the-player", rules = mapOf(HomesRules to HOMES), permissionSource = permissionSource)
        fun maxHomes(source: CommandSourceStack): Long = lab.runtime.ruleSet(source, HomesRules)[HomesRules.maxHomes]

        helper.assertValueEqual(maxHomes(visitor.createCommandSourceStack()), 2L, "la limite du joueur sans nœud, celle du jeu default")
        helper.assertValueEqual(maxHomes(Harness.asGameMaster(visitor)), 2L, "la limite du maître du jeu sans nœud, celle du jeu default")
        helper.assertValueEqual(maxHomes(Harness.console(helper)), 2L, "la limite de la console, celle du jeu default")
        helper.assertValueEqual(maxHomes(vip.createCommandSourceStack()), 10L, "la limite du joueur qui a le nœud du jeu vip")
        helper.assertValueEqual(maxHomes(both.createCommandSourceStack()), 10L, "la limite du joueur qui a deux nœuds, celle du premier jeu du fichier")
        helper.assertValueEqual(lab.runtime.ruleSet(vip.createCommandSourceStack(), WarpsRules), lab.runtime.rules(WarpsRules).default, "le jeu de warps du joueur, que son nœud des homes ne change pas")
        helper.succeed()
    }

    /** Un jeu de règles rechargé prend effet tout de suite pour le joueur qui l'a, et un jeu que le fichier ne porte plus le renvoie au jeu `default`. */
    @GameTest
    fun reloadedRuleSetTakesEffectAtOnce(helper: GameTestHelper) {
        val vip = Harness.player(helper)
        val lab = Lab.open("reloaded-rule-set-takes-effect-at-once", rules = mapOf(HomesRules to HOMES), permissionSource = { source, node -> if (source.player === vip && node == HomesRules.setNode("vip")) true else null })
        fun maxHomes(): Long = lab.runtime.ruleSet(vip.createCommandSourceStack(), HomesRules)[HomesRules.maxHomes]

        lab.writeRules(HomesRules, """{ "rules": { "vip": { "max-homes": 25 }, "default": { "max-homes": 2 } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(maxHomes(), 25L, "la limite du jeu vip rechargé")

        lab.writeRules(HomesRules, """{ "rules": { "default": { "max-homes": 4 } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(maxHomes(), 4L, "la limite du joueur dont le jeu a quitté le fichier, celle du jeu default")
        helper.succeed()
    }
}
