// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.rules.groups.GroupsMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.fabric.api.permission.v1.PermissionContext
import net.fabricmc.fabric.api.permission.v1.PermissionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import net.fabricmc.fabric.api.permission.v1.PermissionNode as FabricPermissionNode

/**
 * La source `external` dans le jeu (cahier des charges, chapitres 2.4.2 et 2.4.5 ; `strategie-de-test.md`, chapitre 5) : un faux fournisseur de
 * permissions, inscrit sur `PermissionEvents.ON_REQUEST` du module de permissions de la Fabric API, répond les nœuds que le test décide, par leur
 * identifiant. Un mod de permissions réel, LuckPerms, s'essaie à la main.
 */
class ExternalRightsGameTests {

    private companion object {

        /** Les jeux `vip` et `default` des homes, chacun avec sa limite. */
        const val HOMES = """{ "rules": { "vip": { "max-homes": 10 }, "default": { "max-homes": 2 } } }"""

        const val EXTERNAL = """{ "permission-source": "external" }"""

        /** Ce que le faux fournisseur répond : par joueur et par identifiant de nœud, accordé ou retiré ; rien pour le reste. */
        val decisions = ConcurrentHashMap<Pair<UUID, String>, Boolean>()

        private val registered = AtomicBoolean()

        /** Inscrit le faux fournisseur une fois pour le run : l'événement est global au serveur. */
        fun registerProvider() {
            if (!registered.compareAndSet(false, true)) return
            PermissionEvents.ON_REQUEST.register(object : PermissionEvents.OnRequest {

                override fun <T : Any> handlePermissionRequest(context: PermissionContext, node: FabricPermissionNode<T>): T? = decisions[context.uuid() to node.key().toString()]?.let { node.cast(it) }
            })
        }
    }

    private fun decide(player: ServerPlayer, identifier: String, granted: Boolean) {
        decisions[player.uuid to identifier] = granted
    }

    private fun maxHomes(lab: Lab, source: CommandSourceStack): Long = lab.runtime.ruleSet(source, HomesRules)[HomesRules.maxHomes]

    /**
     * Sous `external`, le mod de permissions décide, nœud par nœud et par l'identifiant du nœud : le jeu de règles accordé, le rechargement
     * accordé à un joueur, la création d'un home retirée ; sur ce qu'il tait, le défaut du profil, console comprise ; et les commandes des groupes
     * répondent que le système est inactif.
     */
    @GameTest
    fun externalSourceFollowsThePermissionMod(helper: GameTestHelper) {
        registerProvider()
        val vip = Harness.player(helper, "ExternalVipA")
        val reloader = Harness.player(helper, "ExternalReloaderA")
        val denied = Harness.player(helper, "ExternalDeniedA")
        val nobody = Harness.player(helper, "ExternalNobodyA")
        decide(vip, "manymanycommands:rules/homes/vip", true)
        decide(reloader, "manymanycommands:reload", true)
        decide(denied, "manymanycommands:homes/create", false)
        val lab = Lab.open("external-source-follows-the-permission-mod", settings = EXTERNAL, rules = mapOf(HomesRules to HOMES))

        helper.assertFalse(lab.runtime.usesInternalRights, "le système du mod n'est pas la source des droits")
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 10L, "la limite du joueur à qui le mod de permissions accorde le jeu vip")
        helper.assertValueEqual(maxHomes(lab, nobody.createCommandSourceStack()), 2L, "la limite du joueur dont le mod de permissions ne dit rien, celle du jeu default")
        helper.assertValueEqual(maxHomes(lab, Harness.console(helper)), 2L, "la limite de la console")
        val applied = Harness.FILES.map { ModMessages.RELOAD_FILE_APPLIED.key }
        helper.assertValueEqual(Harness.keysSentTo(reloader, lab.runtime) { lab.dispatcher.execute("manymanycommands reload", reloader.createCommandSourceStack()) }, applied, "le joueur à qui le mod de permissions accorde reload recharge")
        Harness.assertUnknownCommand(helper, lab.dispatcher, nobody.createCommandSourceStack(), "manymanycommands reload")
        Harness.assertUnknownSubcommand(helper, lab.dispatcher, denied.createCommandSourceStack(), "homes create base")
        helper.assertTrue(lab.dispatcher.root.getChild("homes").getChild("create").canUse(nobody.createCommandSourceStack()), "la création d'un home reste au joueur dont le mod de permissions ne dit rien")
        helper.assertValueEqual(Harness.keysSentTo(null, lab.runtime) { lab.dispatcher.execute("manymanycommands group list", Harness.console(helper)) }, listOf(GroupsMessages.INACTIVE.key), "les commandes des groupes disent le système inactif")
        helper.succeed()
    }

    /** Sous `internal`, le mod de permissions est ignoré : ce qu'il accorde ne vaut rien, et les groupes seuls décident. */
    @GameTest
    fun internalSourceIgnoresThePermissionMod(helper: GameTestHelper) {
        registerProvider()
        val vip = Harness.player(helper, "ExternalVipB")
        val reloader = Harness.player(helper, "ExternalReloaderB")
        decide(vip, "manymanycommands:rules/homes/vip", true)
        decide(reloader, "manymanycommands:reload", true)
        val lab = Lab.open("internal-source-ignores-the-permission-mod", rules = mapOf(HomesRules to HOMES))

        helper.assertTrue(lab.runtime.usesInternalRights, "le système du mod est la source des droits")
        helper.assertValueEqual(maxHomes(lab, vip.createCommandSourceStack()), 2L, "la limite du joueur que le mod de permissions accorderait, celle du jeu default")
        Harness.assertUnknownCommand(helper, lab.dispatcher, reloader.createCommandSourceStack(), "manymanycommands reload")
        helper.succeed()
    }
}
