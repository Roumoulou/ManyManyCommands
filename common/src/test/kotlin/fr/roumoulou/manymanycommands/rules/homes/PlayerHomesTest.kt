// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Les homes d'un joueur (`homes.md` des specs, chapitre 3) : l'ordre des refus de chaque commande, le tri de la liste et le home par défaut,
 * en valeurs.
 */
class PlayerHomesTest {

    private val overworld = Position("minecraft:overworld", 12.5, 64.0, -3.25, 90f, 0f)

    private val nether = Position("minecraft:the_nether", 0.5, 70.0, 0.5, 0f, 0f)

    /** Le jeu `default` d'un fichier qui n'écrit que la limite, les dimensions interdites aux homes et celles d'où `teleport` ne se lance pas. */
    private fun rules(maxHomes: Long = 3, forbidden: List<String> = emptyList(), forbiddenDeparture: List<String> = emptyList()): RuleValues {
        val set = mapOf(HomesRules.maxHomes.key to maxHomes, HomesRules.forbiddenDimensions.key to forbidden, HomesRules.forbiddenDepartureDimensions.key to forbiddenDeparture)
        return HomesRules.values(WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to set))).default
    }

    private fun refused(change: HomesChange) = assertInstanceOf(HomesChange.Refused::class.java, change).message

    private fun applied(change: HomesChange) = assertInstanceOf(HomesChange.Applied::class.java, change)

    private fun refused(teleport: HomesTeleport) = assertInstanceOf(HomesTeleport.Refused::class.java, teleport).message

    /** Le home à rejoindre, pour un joueur dans l'Overworld dont le serveur porte toutes les dimensions sauf la lune, sans quota. */
    private fun teleport(homes: PlayerHomes, name: String?, ruleSet: RuleValues = rules(), quota: Quota? = null, wait: Long = 0): HomesTeleport =
        homes.teleport(name, "minecraft:overworld", ruleSet, { it != "mymod:moon" }, quota) { wait }

    // ── Créer (chapitre 3.1) ──

    @Test
    fun `la création refuse dans l'ordre de sa fiche, le nom, la dimension, la limite, puis le nom déjà pris`() {
        val homes = PlayerHomes(mapOf("base" to overworld))
        val strict = rules(maxHomes = 1, forbidden = listOf("minecraft:the_nether"))

        assertEquals(CommonMessages.NAME_INVALID.with(), refused(homes.create("ba se", nether, strict)))
        assertEquals(HomesMessages.FORBIDDEN_DIMENSION.with("minecraft:the_nether"), refused(homes.create("base", nether, strict)))
        assertEquals(HomesMessages.LIMIT_REACHED_ONE.with(1L), refused(homes.create("base", overworld, strict)))
        assertEquals(HomesMessages.ALREADY_EXISTS.with("base"), refused(homes.create("base", overworld, rules(maxHomes = 3))))
    }

    @Test
    fun `un home créé s'ajoute aux homes, et le succès dit sa dimension et ses coordonnées entières`() {
        val change = applied(PlayerHomes(mapOf("base" to overworld)).create("mine", nether, rules()))

        assertEquals(mapOf("base" to overworld, "mine" to nether), change.homes.homes)
        assertEquals(HomesMessages.CREATED.with("mine", "minecraft:the_nether", 0, 70, 0), change.message)
    }

    @Test
    fun `une limite de 0 interdit toute création, et une limite passée sous le nombre de homes laisse les homes en place`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "mine" to nether))

        assertEquals(HomesMessages.LIMIT_REACHED_MANY.with(0L), refused(PlayerHomes().create("base", overworld, rules(maxHomes = 0))))
        assertEquals(HomesMessages.LIMIT_REACHED_ONE.with(1L), refused(homes.create("ferme", overworld, rules(maxHomes = 1))))
        applied(homes.delete("base"))
        assertEquals(overworld, homes["base"], "Nothing is deleted on its own: delete gives new homes and leaves the original")
    }

    @Test
    fun `la limite atteinte se dit au singulier pour 1, au pluriel sinon`() {
        assertEquals(HomesMessages.LIMIT_REACHED_ONE.key, HomesMessages.limitReached(1).key)
        assertEquals(HomesMessages.LIMIT_REACHED_MANY.key, HomesMessages.limitReached(0).key)
        assertEquals(HomesMessages.LIMIT_REACHED_MANY.key, HomesMessages.limitReached(3).key)
    }

    // ── Rejoindre (chapitre 3.2) ──

    @Test
    fun `rejoindre refuse dans l'ordre de sa fiche, le home absent, la dimension de départ, la dimension introuvable, puis le quota`() {
        val moon = Position("mymod:moon", 0.0, 0.0, 0.0, 0f, 0f)
        val homes = PlayerHomes(mapOf("base" to overworld, "moon" to moon))
        val noDeparture = rules(forbiddenDeparture = listOf("minecraft:overworld"))
        val quota = Quota(uses = 1, periodSeconds = 60)

        assertEquals(HomesMessages.ABSENT.with("ferme"), refused(teleport(homes, "ferme", noDeparture, quota, wait = 30)))
        assertEquals(HomesMessages.NO_DEFAULT.with(), refused(teleport(homes, null, noDeparture, quota, wait = 30)))
        assertEquals(HomesMessages.DEPARTURE_FORBIDDEN.with("minecraft:overworld"), refused(teleport(homes, "moon", noDeparture, quota, wait = 30)))
        assertEquals(HomesMessages.DIMENSION_MISSING.with("mymod:moon", "moon"), refused(teleport(homes, "moon", quota = quota, wait = 30)))
        assertEquals(HomesMessages.QUOTA_REACHED_ONE.with(1L, DurationWording.text(30)), refused(teleport(homes, "base", quota = quota, wait = 30)))
        assertEquals(HomesTeleport.Target("base", overworld), teleport(homes, "base", quota = quota, wait = 0))
    }

    @Test
    fun `sans nom, rejoindre vise le home par défaut`() {
        assertEquals(HomesTeleport.Target("base", overworld), teleport(PlayerHomes(mapOf("base" to overworld)), null))
        assertEquals(HomesTeleport.Target("mine", nether), teleport(PlayerHomes(mapOf("base" to overworld, "mine" to nether), designated = "mine"), null))
    }

    @Test
    fun `l'attente du quota ne se consulte que quand le joueur en a un`() {
        val homes = PlayerHomes(mapOf("base" to overworld))

        val teleport = homes.teleport("base", "minecraft:overworld", rules(), { true }, quota = null) { error("No quota, no wait to measure") }

        assertEquals(HomesTeleport.Target("base", overworld), teleport)
    }

    @Test
    fun `le quota atteint se dit au singulier pour 1 usage, au pluriel sinon`() {
        val wait = DurationWording.text(90)

        assertEquals(HomesMessages.QUOTA_REACHED_ONE.with(1L, wait), HomesMessages.quotaReached(1, wait))
        assertEquals(HomesMessages.QUOTA_REACHED_MANY.with(3L, wait), HomesMessages.quotaReached(3, wait))
    }

    // ── Supprimer (chapitre 3.3) ──

    @Test
    fun `la suppression refuse le home absent, et dit la position du home supprimé`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "mine" to nether))

        assertEquals(HomesMessages.ABSENT.with("ferme"), refused(homes.delete("ferme")))
        val change = applied(homes.delete("base"))
        assertEquals(mapOf("mine" to nether), change.homes.homes)
        assertEquals(HomesMessages.DELETED.with("base", "minecraft:overworld", 12, 64, -4), change.message)
    }

    @Test
    fun `la suppression du home désigné efface la désignation`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "mine" to nether), designated = "base")

        assertNull(applied(homes.delete("base")).homes.designated)
        assertEquals("base", applied(homes.delete("mine")).homes.designated)
    }

    // ── Renommer (chapitre 3.5) ──

    @Test
    fun `le renommage refuse dans l'ordre de sa fiche, le nouveau nom, le home absent, les noms identiques, puis le nom déjà pris`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "mine" to nether))

        assertEquals(CommonMessages.NAME_INVALID.with(), refused(homes.rename("ferme", "ba se")))
        assertEquals(HomesMessages.ABSENT.with("ferme"), refused(homes.rename("ferme", "mine")))
        assertEquals(HomesMessages.SAME_NAME.with("base"), refused(homes.rename("base", "base")))
        assertEquals(HomesMessages.ALREADY_EXISTS.with("mine"), refused(homes.rename("base", "mine")))
    }

    @Test
    fun `un changement de casse est un renommage, qui garde la place et la position du home`() {
        val change = applied(PlayerHomes(mapOf("base" to overworld, "mine" to nether)).rename("base", "Base"))

        assertEquals(listOf("Base", "mine"), change.homes.homes.keys.toList())
        assertEquals(overworld, change.homes["Base"])
        assertEquals(HomesMessages.RENAMED.with("base", "Base"), change.message)
    }

    @Test
    fun `le home par défaut le reste sous son nouveau nom`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "mine" to nether), designated = "base")

        assertEquals("Base", applied(homes.rename("base", "Base")).homes.designated)
        assertEquals("base", applied(homes.rename("mine", "ferme")).homes.designated)
    }

    // ── Déplacer (chapitre 3.6) ──

    @Test
    fun `le déplacement refuse le home absent, puis la dimension interdite, et remplace la position`() {
        val homes = PlayerHomes(mapOf("base" to overworld))
        val forbidden = rules(forbidden = listOf("minecraft:the_nether"))

        assertEquals(HomesMessages.ABSENT.with("mine"), refused(homes.move("mine", nether, forbidden)))
        assertEquals(HomesMessages.FORBIDDEN_DIMENSION.with("minecraft:the_nether"), refused(homes.move("base", nether, forbidden)))
        val change = applied(homes.move("base", nether, rules()))
        assertEquals(nether, change.homes["base"])
        assertEquals(HomesMessages.MOVED.with("base", "minecraft:the_nether", 0, 70, 0), change.message)
    }

    // ── Le home par défaut (chapitre 3.7) ──

    @Test
    fun `désigner refuse le home absent, et désigner le home déjà par défaut réussit de nouveau`() {
        val homes = PlayerHomes(mapOf("base" to overworld, "mine" to nether), designated = "base")

        assertEquals(HomesMessages.ABSENT.with("ferme"), refused(homes.designate("ferme")))
        assertEquals("mine", applied(homes.designate("mine")).homes.designated)
        val again = applied(homes.designate("base"))
        assertEquals("base", again.homes.designated)
        assertEquals(HomesMessages.DEFAULT_SET.with("base"), again.message)
    }

    @Test
    fun `un joueur à un seul home l'a pour home par défaut, à plusieurs seul un home désigné l'est`() {
        assertNull(PlayerHomes().defaultHome)
        assertEquals("base", PlayerHomes(mapOf("base" to overworld)).defaultHome)
        assertNull(PlayerHomes(mapOf("base" to overworld, "mine" to nether)).defaultHome)
        assertEquals("mine", PlayerHomes(mapOf("base" to overworld, "mine" to nether), designated = "mine").defaultHome)
    }

    @Test
    fun `le home désigné est l'un des homes`() {
        assertThrows(IllegalArgumentException::class.java) { PlayerHomes(mapOf("base" to overworld), designated = "mine") }
    }

    // ── Lister (chapitre 3.4) ──

    @Test
    fun `la liste se trie sans tenir compte de la casse, la majuscule d'abord à égalité`() {
        val homes = PlayerHomes(listOf("base", "Base", "atelier", "Zoo", "ferme").associateWith { overworld })

        assertEquals(listOf("atelier", "Base", "base", "ferme", "Zoo"), homes.sortedNames())
    }
}
