// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.game.teleport.QuotaCharge
import fr.roumoulou.manymanycommands.game.teleport.TeleportListener
import fr.roumoulou.manymanycommands.game.teleport.TeleportRequest
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenQuota
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import fr.roumoulou.manymanycommands.rules.teleport.CancellationCause
import fr.roumoulou.manymanycommands.rules.teleport.TeleportRefusal
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

/**
 * Le décor d'un test du déroulé d'une téléportation, bâti au bloc dans la structure du test : une dalle de départ, et un joueur simulé qui s'y
 * tient, en mode survie.
 *
 * Le joueur simulé du jeu se dit en mode créatif, que le déroulé dispense du contrôle de l'arrivée : le mode se donne ici là où le mod le lit.
 * Son client n'annonce jamais qu'il a chargé le monde, et le jeu tient jusque-là un joueur pour invulnérable : l'annonce se fait ici à sa place.
 * Le serveur ne le fait ni tomber ni marcher : il reste où le test le pose.
 */
class TeleportStage(val helper: GameTestHelper) {

    companion object {

        /** La hauteur des dalles dans la structure du test. Un joueur posé dessus a les pieds un bloc plus haut. */
        const val FLOOR = 1

        /** Au-dessus du toit de bedrock du Nether : de l'air, quoi que la génération ait posé dessous. */
        private const val NETHER_FLOOR = 200

        /** Le jeu de règles d'un joueur dans la famille de [table], tel qu'un fichier l'écrirait : les valeurs livrées des homes par défaut. */
        fun rules(table: RulesTable = HomesRules, delay: Long = 3, cooldown: Long = 10, quota: WrittenQuota? = null): RuleValues =
            table.values(WrittenRules(sets = mapOf(RulesTable.DEFAULT_SET to mapOf(table.delay.writes(delay), table.cooldown.writes(cooldown), table.quota.writes(quota))))).default
    }

    /** Le point de départ : le joueur debout au centre de sa dalle. */
    val departure: Location = platform(1, 1)

    val player: ServerPlayer = Harness.player(helper).also {
        it.setGameMode(GameType.SURVIVAL)
        it.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        place(it, departure)
    }

    /** Une dalle de pierre de 3 blocs sur 3, centrée sur la colonne ([x], [z]) de la structure, et l'endroit où un joueur s'y tient debout. */
    fun platform(x: Int, z: Int): Location {
        for (dx in -1..1) for (dz in -1..1) set(x + dx, FLOOR, z + dz, Blocks.STONE)
        return standing(x, FLOOR + 1, z)
    }

    /** L'endroit où un joueur a les pieds au centre du bloc ([x], [y], [z]) de la structure, que rien n'y soit posé ou non. */
    fun standing(x: Int, y: Int, z: Int): Location {
        val point = helper.absoluteVec(Vec3(x + 0.5, y.toDouble(), z + 0.5))
        return Location(helper.level, point.x, point.y, point.z, 0f, 0f)
    }

    fun set(x: Int, y: Int, z: Int, block: Block) = helper.setBlock(BlockPos(x, y, z), block)

    /** Une dalle de pierre dans le Nether, à l'aplomb de la structure du test, et l'endroit où un joueur s'y tient debout. */
    fun netherPlatform(): Location {
        val nether = nether()
        val center = helper.absolutePos(BlockPos(1, 0, 1))
        for (dx in -1..1) for (dz in -1..1) nether.setBlockAndUpdate(BlockPos(center.x + dx, NETHER_FLOOR, center.z + dz), Blocks.STONE.defaultBlockState())
        return Location(nether, center.x + 0.5, NETHER_FLOOR + 1.0, center.z + 0.5, 0f, 0f)
    }

    fun nether(): ServerLevel = helper.level.server.getLevel(Level.NETHER) ?: throw helper.assertionException("le Nether n'existe pas sur le serveur GameTest")

    /** La demande qu'une famille ferait au déroulé pour le joueur : vers [destination], avec le jeu de règles [ruleSet], sa source étant la sienne. */
    fun request(
        destination: Location,
        ruleSet: RuleValues = rules(),
        table: RulesTable = HomesRules,
        source: CommandSourceStack = player.createCommandSourceStack(),
        arrival: Arrival = Arrival.At(destination),
        familyRefusal: () -> Message? = { null },
        quotaCharge: QuotaCharge? = null,
        listener: TeleportListener = TeleportListener.NONE,
    ): TeleportRequest = TeleportRequest(player, source, table, ruleSet, arrival, familyRefusal, quotaCharge, listener)

    /**
     * Fait apparaître une entité dans la structure du test. Son type se lit au registre, par son identifiant (`minecraft:pig`) : la classe qui
     * porte les constantes des types n'est pas la même en 26.1 et dans les versions suivantes.
     */
    fun spawn(type: String, x: Int, y: Int, z: Int): Entity = helper.spawn(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type)), x, y, z)

    /** Pose [player] à [location], comme le ferait la commande `tp` du jeu. */
    fun place(player: ServerPlayer, location: Location) {
        player.teleportTo(location.level, location.x, location.y, location.z, emptySet(), location.yaw, location.pitch, true)
    }

    /** Le lancement [launched] est refusé, par [expected]. Une assertion du jeu ne prend pas de valeur nulle : le lancement admis se dit ici. */
    fun assertRefused(launched: TeleportRefusal?, expected: TeleportRefusal, what: String) {
        helper.assertValueEqual(launched ?: throw helper.assertionException("le lancement devait être refusé : $what"), expected, what)
    }

    /** Le joueur se tient à [location], dans sa dimension et à ses coordonnées exactes. */
    fun assertPlayerAt(location: Location, what: String) {
        helper.assertValueEqual(player.level().dimension(), location.level.dimension(), "la dimension du joueur, $what")
        helper.assertValueEqual(player.position(), Vec3(location.x, location.y, location.z), "la position du joueur, $what")
    }

    /** L'issue d'une téléportation, telle que la famille l'apprend du déroulé. */
    class Outcome : TeleportListener {

        var arrival: Pair<Location, Location>? = null
            private set

        val cancellations = mutableListOf<CancellationCause>()

        override fun onArrived(from: Location, to: Location) {
            arrival = from to to
        }

        override fun onCancelled(cause: CancellationCause) {
            cancellations += cause
        }
    }
}
