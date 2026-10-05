// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.permission.PermissionSource
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.mod.ModNodes
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/** Les nœuds de permission dans le jeu (cahier des charges, chapitre 2.4.2) : une commande dont la source n'a pas le nœud n'existe pas pour elle. */
class PermissionNodeGameTests {

    /**
     * Sans le niveau des maîtres du jeu, un joueur n'a aucun nœud de `/manymanycommands` : la commande et son alias sont absents de ce qu'il peut
     * lancer, donc de la complétion, le jeu y répond comme à une commande inconnue, et rien ne lui est envoyé.
     */
    @GameTest
    fun commandDoesNotExistWithoutItsNode(helper: GameTestHelper) {
        val runtime = Harness.serverRuntime(helper)
        val dispatcher = Harness.serverDispatcher(helper)
        val player = Harness.player(helper)
        val source = player.createCommandSourceStack()

        val deliveries = Harness.record(runtime) {
            listOf("manymanycommands", "manymanycommands reload", "mmc", "mmc reload").forEach { Harness.assertUnknownCommand(helper, dispatcher, source, it) }
        }

        helper.assertFalse(dispatcher.root.getChild("manymanycommands").canUse(source), "/manymanycommands n'existe pas pour un joueur sans son nœud")
        helper.assertFalse(dispatcher.root.getChild("mmc").canUse(source), "/mmc n'existe pas pour un joueur sans son nœud")
        helper.assertValueEqual(deliveries, emptyList(), "les messages du mod devant une commande inconnue")
        helper.succeed()
    }

    /** Le profil administrateur par défaut : le niveau des maîtres du jeu ouvre la commande, sa sous-commande et l'alias, et la console les a aussi. */
    @GameTest
    fun gameMastersAndTheConsoleHaveTheNodesByDefault(helper: GameTestHelper) {
        val dispatcher = Harness.serverDispatcher(helper)
        val gameMaster = Harness.asGameMaster(Harness.player(helper))
        val console = Harness.console(helper)

        listOf(gameMaster, console).forEach { source ->
            listOf("manymanycommands", "mmc").forEach { name ->
                val command = dispatcher.root.getChild(name)
                helper.assertTrue(command.canUse(source), "/$name existe pour ${source.textName}")
                helper.assertTrue(command.getChild("reload").canUse(source), "/$name reload existe pour ${source.textName}")
            }
        }
        helper.succeed()
    }

    /**
     * La source des droits l'emporte sur le défaut du profil, nœud par nœud. Un joueur qui ne reçoit que `manymanycommands.reload` recharge, mais
     * la commande seule, dont le nœud est `manymanycommands.status`, n'existe pas pour lui. Un maître du jeu à qui `status` est retiré la perd, et
     * garde `reload`.
     */
    @GameTest
    fun permissionSourceDecidesNodeByNode(helper: GameTestHelper) {
        val reloader = Harness.player(helper)
        val demoted = Harness.player(helper)
        val permissionSource = PermissionSource { source, node ->
            when {
                source.player === reloader && node == ModNodes.RELOAD -> true
                source.player === demoted && node == ModNodes.STATUS -> false
                else -> null
            }
        }
        val lab = Lab.open("permission-source-decides-node-by-node", permissionSource = permissionSource)

        val reloaderKeys = Harness.keysSentTo(reloader, lab.runtime) {
            helper.assertValueEqual(lab.dispatcher.execute("manymanycommands reload", reloader.createCommandSourceStack()), 1, "le rechargement par le joueur qui a reçu son nœud")
            Harness.assertUnknownCommand(helper, lab.dispatcher, reloader.createCommandSourceStack(), "manymanycommands")
        }
        val demotedKeys = Harness.keysSentTo(demoted, lab.runtime) {
            Harness.assertUnknownCommand(helper, lab.dispatcher, Harness.asGameMaster(demoted), "manymanycommands")
            helper.assertValueEqual(lab.dispatcher.execute("mmc reload", Harness.asGameMaster(demoted)), 1, "le rechargement par le maître du jeu privé de status")
        }

        helper.assertValueEqual(reloaderKeys, listOf(ModMessages.RELOAD_FILE_APPLIED.key), "ce que reçoit le joueur qui n'a que reload")
        helper.assertValueEqual(demotedKeys, listOf(ModMessages.RELOAD_FILE_APPLIED.key), "ce que reçoit le maître du jeu privé de status")
        helper.succeed()
    }

    /** Un joueur à qui la source des droits retire tous les nœuds de la commande ne la voit plus, maître du jeu ou non. */
    @GameTest
    fun withdrawnNodesHideTheCommand(helper: GameTestHelper) {
        val player = Harness.player(helper)
        val lab = Lab.open("withdrawn-nodes-hide-the-command", permissionSource = { source, _ -> if (source.player === player) false else null })
        val source = Harness.asGameMaster(player)

        helper.assertFalse(lab.dispatcher.root.getChild("manymanycommands").canUse(source), "/manymanycommands n'existe plus pour le joueur privé de ses nœuds")
        Harness.assertUnknownCommand(helper, lab.dispatcher, source, "manymanycommands reload")
        helper.assertTrue(lab.dispatcher.root.getChild("manymanycommands").canUse(Harness.console(helper)), "la console, sur qui la source ne se prononce pas, garde la commande")
        helper.succeed()
    }
}
