// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** Les warps en règles (`warps.md` des specs, chapitres 1 à 3) : chaque commande, ses refus dans l'ordre de sa fiche, et ce qu'elle change. */
class WarpsTest {

    private val overworld = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val nether = Position("minecraft:the_nether", 0.5, 70.0, 0.5, 0f, 0f)

    private val moon = Position("mymod:moon", 0.0, 64.0, 0.0, 0f, 0f)

    /** Deux warps ouverts, dont un dans une dimension que le serveur ne porte pas, et un warp réservé. */
    private val warps = Warps(mapOf("base" to Warp(overworld), "moon" to Warp(moon), "staff" to Warp(nether, key = "staff")))

    /** Le jeu `default` d'un fichier qui interdit de rejoindre un warp depuis l'Overworld. */
    private val noDeparture: RuleValues = WarpsRules.values(WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to mapOf(WarpsRules.forbiddenDepartureDimensions.key to listOf("minecraft:overworld"))))).default

    private val allowed = { true }

    private val outOfBounds = { false }

    private val placementNotConsulted = { error("The placement is consulted last") }

    private fun applied(change: WarpsChange): Warps = assertInstanceOf(WarpsChange.Applied::class.java, change).warps

    private fun refused(change: WarpsChange) = assertInstanceOf(WarpsChange.Refused::class.java, change).message

    private fun refused(teleport: WarpsTeleport) = assertInstanceOf(WarpsTeleport.Refused::class.java, teleport).message

    /** Un joueur de l'Overworld sans aucun nœud d'accès, sur un serveur qui ne porte pas `mymod:moon`. */
    private fun teleport(name: String, hasAccess: (String) -> Boolean = { false }, ruleSet: RuleValues = WarpsRules.shipped.default, quota: Quota? = null, wait: Long = 0): WarpsTeleport =
        warps.teleport(name, hasAccess, "minecraft:overworld", ruleSet, { it != "mymod:moon" }, quota) { wait }

    // ── Créer (chapitre 3.1) ──

    @Test
    fun `create fait un warp ouvert à tous, à la position donnée, et le dit avec sa dimension et ses coordonnées entières`() {
        val change = Warps().create("base", overworld, allowed)

        assertEquals(Warps(mapOf("base" to Warp(overworld))), applied(change))
        assertEquals(WarpsMessages.CREATED.with("base", "minecraft:overworld", 12, 64, -4), change.message)
    }

    @Test
    fun `create refuse dans l'ordre de sa fiche, le nom invalide, le nom pris, puis le placement, consulté en dernier`() {
        assertEquals(CommonMessages.NAME_INVALID.with(), refused(warps.create("ba.se", overworld, placementNotConsulted)))
        assertEquals(WarpsMessages.ALREADY_EXISTS.with("base"), refused(warps.create("base", nether, placementNotConsulted)))
        assertEquals(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with(), refused(warps.create("hub", overworld, outOfBounds)))
        assertEquals(warps.warps + ("hub" to Warp(overworld)), applied(warps.create("hub", overworld, allowed)).warps)
    }

    // ── Supprimer (chapitre 3.2) ──

    @Test
    fun `delete refuse le warp absent, et rappelle la dimension et les coordonnées du warp supprimé`() {
        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(warps.delete("mine")))

        val change = warps.delete("staff")

        assertEquals(warps.warps - "staff", applied(change).warps)
        assertEquals(WarpsMessages.DELETED.with("staff", "minecraft:the_nether", 0, 70, 0), change.message)
    }

    // ── Renommer (chapitre 3.3) ──

    @Test
    fun `rename refuse dans l'ordre de sa fiche, le nouveau nom invalide, le warp absent, les noms identiques, le nom pris`() {
        assertEquals(CommonMessages.NAME_INVALID.with(), refused(warps.rename("mine", "ba.se")))
        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(warps.rename("mine", "base")))
        assertEquals(WarpsMessages.SAME_NAME.with("base"), refused(warps.rename("base", "base")))
        assertEquals(WarpsMessages.ALREADY_EXISTS.with("moon"), refused(warps.rename("base", "moon")))
    }

    @Test
    fun `rename change le nom et rien d'autre, la clé comprise, et un changement de casse est un renommage`() {
        val change = warps.rename("staff", "Staff")

        assertEquals(mapOf("base" to Warp(overworld), "moon" to Warp(moon), "Staff" to Warp(nether, "staff")), applied(change).warps)
        assertEquals(WarpsMessages.RENAMED.with("staff", "Staff"), change.message)
        assertEquals(listOf("Base", "moon", "staff"), applied(warps.rename("base", "Base")).sortedNames())
    }

    // ── Déplacer (chapitre 3.4) ──

    @Test
    fun `move refuse le warp absent puis le placement, et remplace la position en gardant la clé`() {
        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(warps.move("mine", overworld, placementNotConsulted)))
        assertEquals(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with(), refused(warps.move("staff", overworld, outOfBounds)))

        val change = warps.move("staff", overworld, allowed)

        assertEquals(Warp(overworld, "staff"), applied(change)["staff"])
        assertEquals(WarpsMessages.MOVED.with("staff", "minecraft:overworld", 12, 64, -4), change.message)
    }

    // ── Réserver (chapitre 3.5) ──

    @Test
    fun `restrict refuse la clé invalide avant le warp absent, réserve le warp, et une nouvelle clé remplace l'ancienne`() {
        assertEquals(WarpsMessages.KEY_INVALID.with(), refused(warps.restrict("mine", "Vip")))
        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(warps.restrict("mine", "vip")))

        val change = warps.restrict("base", "vip")

        assertEquals(Warp(overworld, "vip"), applied(change)["base"])
        assertEquals(WarpsMessages.RESTRICTED.with("base", "vip"), change.message)
        assertEquals(Warp(nether, "vip"), applied(warps.restrict("staff", "vip"))["staff"])
    }

    @Test
    fun `unrestrict refuse le warp absent, rouvre le warp, et rouvrir un warp ouvert réussit de nouveau`() {
        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(warps.unrestrict("mine")))

        val change = warps.unrestrict("staff")

        assertEquals(Warp(nether), applied(change)["staff"])
        assertEquals(WarpsMessages.UNRESTRICTED.with("staff"), change.message)
        assertEquals(WarpsMessages.UNRESTRICTED.with("base"), warps.unrestrict("base").message)
        assertEquals(warps, applied(warps.unrestrict("base")))
    }

    // ── Rejoindre (chapitre 2.1) ──

    @Test
    fun `un warp réservé à d'autres répond comme s'il n'existait pas, et s'ouvre à qui a sa clé`() {
        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(teleport("mine")))
        assertEquals(WarpsMessages.ABSENT.with("staff"), refused(teleport("staff")))
        assertEquals(WarpsTeleport.Target("staff", nether), teleport("staff", hasAccess = { it == "staff" }))
    }

    @Test
    fun `teleport refuse dans l'ordre de sa fiche, le warp absent, la dimension de départ, la dimension du warp, le quota`() {
        val quota = Quota(uses = 1, periodSeconds = 60)

        assertEquals(WarpsMessages.ABSENT.with("mine"), refused(teleport("mine", ruleSet = noDeparture, quota = quota, wait = 30)))
        assertEquals(WarpsMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld"), refused(teleport("moon", ruleSet = noDeparture, quota = quota, wait = 30)))
        assertEquals(WarpsMessages.DIMENSION_MISSING.with("mymod:moon", "moon"), refused(teleport("moon", quota = quota, wait = 30)))
        assertEquals(WarpsMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(30)), refused(teleport("base", quota = quota, wait = 30)))
        assertEquals(WarpsTeleport.Target("base", overworld), teleport("base", quota = quota, wait = 0))
    }

    @Test
    fun `l'attente ne se consulte que quand le joueur a un quota`() {
        val teleport = warps.teleport("base", { false }, "minecraft:overworld", WarpsRules.shipped.default, { true }, quota = null) { error("No quota, no wait to measure") }

        assertEquals(WarpsTeleport.Target("base", overworld), teleport)
    }

    // ── La liste et la complétion (chapitres 2.2 et 3.5) ──

    @Test
    fun `les noms se trient sans tenir compte de la casse, et les warps accessibles sont les ouverts et ceux dont le lecteur a la clé`() {
        val many = Warps(mapOf("base" to Warp(overworld), "Base" to Warp(nether), "atelier" to Warp(overworld), "staff" to Warp(nether, "staff"), "vip" to Warp(nether, "vip")))

        assertEquals(listOf("atelier", "Base", "base", "staff", "vip"), many.sortedNames())
        assertEquals(listOf("atelier", "Base", "base"), many.accessible { false })
        assertEquals(listOf("atelier", "Base", "base", "vip"), many.accessible { it == "vip" })
        assertEquals(listOf("staff", "vip"), many.keys())
        assertEquals(emptyList<String>(), Warps().keys())
    }
}
