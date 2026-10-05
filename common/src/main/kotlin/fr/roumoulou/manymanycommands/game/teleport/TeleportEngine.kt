// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.teleport

import fr.roumoulou.manymanycommands.ManyManyCommands
import fr.roumoulou.manymanycommands.game.message.Messenger
import fr.roumoulou.manymanycommands.game.permission.Rights
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalRules
import fr.roumoulou.manymanycommands.rules.teleport.CancellationCause
import fr.roumoulou.manymanycommands.rules.teleport.Cooldowns
import fr.roumoulou.manymanycommands.rules.teleport.DelayStep
import fr.roumoulou.manymanycommands.rules.teleport.Dispensations
import fr.roumoulou.manymanycommands.rules.teleport.ImmobilityDelay
import fr.roumoulou.manymanycommands.rules.teleport.QuotaRules
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsages
import fr.roumoulou.manymanycommands.rules.teleport.TeleportRefusal
import fr.roumoulou.manymanycommands.rules.teleport.TeleportSteps
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.entity.Leashable
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Le moteur qui joue le déroulé d'une téléportation pour un joueur (cahier des charges, chapitre 2.5), et l'état qu'il tient en mémoire pour un
 * serveur : les téléportations en attente, les cooldowns, les plages de quota.
 *
 * Il ne refait aucune règle : il lit le jeu, l'heure et les droits, les passe en valeurs aux règles de `rules.teleport`, et fait dans le jeu ce
 * qu'elles décident. Il envoie les messages du tableau du chapitre 2.5.8.
 *
 * @param clock l'horloge de l'état du mod, que tout délai mesure
 */
class TeleportEngine(private val messenger: Messenger, private val rights: Rights, private val clock: Clock) {

    private companion object {

        /** Le jeu compte le temps de jeu d'un joueur en ticks. */
        const val TICKS_PER_SECOND = 20
    }

    /** Les téléportations en attente, une par joueur au plus, dans l'ordre de leur lancement. */
    private val journeys = LinkedHashMap<UUID, Journey>()

    private val cooldowns = Cooldowns()

    private val quotaUsages = QuotaUsages()

    fun hasPending(player: ServerPlayer): Boolean = player.uuid in journeys

    /** Ce dont [source] est dispensée, par ses nœuds (cahier des charges, chapitre 2.4.4). */
    fun dispensations(source: CommandSourceStack): Dispensations = Dispensations.of { rights.has(source, it) }

    /**
     * L'usage que la téléportation de [holder] comptera dans la famille de [table], `null` quand rien ne se compte : le jeu de règles n'a pas de
     * quota, ou [source] en est dispensée.
     */
    fun quotaCharge(source: CommandSourceStack, holder: ServerPlayer, table: RulesTable, ruleSet: RuleValues): QuotaCharge? =
        dispensations(source).quota(ruleSet[table.quota])?.let { QuotaCharge(holder, table.family, it) }

    /** Les secondes à attendre avant le prochain usage de [charge], et 0 quand il est possible : la famille en fait son refus du quota atteint. */
    fun quotaWaitSeconds(charge: QuotaCharge?): Long = if (charge == null) 0 else QuotaRules.waitSeconds(charge.quota, quotaUsages[charge.holder.uuid, charge.family], quotaNow(charge))

    /**
     * Lance la téléportation de [request] : les refus des étapes 1 à 4, puis le délai d'immobilité, que [tick] fait s'écouler.
     *
     * @return ce qui la refuse, déjà répondu à la source de la demande, ou `null` quand elle est lancée. Sans délai, le joueur est déjà arrivé
     */
    fun launch(request: TeleportRequest): TeleportRefusal? {
        val player = request.player
        val now = clock.instant()
        val dispensations = dispensations(request.source)
        var reached: Location? = null
        val refusal = TeleportSteps.firstRefusal(
            familyRefusal = request.familyRefusal,
            hasPendingTeleport = { hasPending(player) },
            cooldownRemainingSeconds = { cooldowns.remainingSeconds(player.uuid, request.table.family, dispensations.cooldownSeconds(request.ruleSet[request.table.cooldown]), now) },
            arrivalRefusal = { arrivalRefusal(request) { reached = it } },
        )
        if (refusal != null) {
            messenger.reply(request.source, refusal.message)
            return refusal
        }
        val destination = reached ?: error("A teleport that nothing refuses has a destination")
        val journey = Journey(request, Location.of(player), destination, ImmobilityDelay(now, dispensations.delaySeconds(request.ruleSet[request.table.delay])))
        journeys[player.uuid] = journey
        advance(journey, now)
        return null
    }

    /**
     * Fait avancer les téléportations en attente, à chaque tick du serveur : l'annulation de celles dont le joueur s'est déconnecté, est mort, a
     * changé de dimension ou s'est éloigné, le compte à rebours des autres, et l'arrivée de celles dont le délai est écoulé.
     */
    fun tick() {
        if (journeys.isEmpty()) return
        val now = clock.instant()
        journeys.values.toList().forEach { journey ->
            val player = journey.request.player
            val cause = when {
                player.hasDisconnected() -> CancellationCause.DISCONNECTED
                player.isDeadOrDying -> CancellationCause.DIED
                else -> CancellationCause.ofPosition(journey.departure.spot(), Location.of(player).spot())
            }
            if (cause != null) cancel(journey, cause) else advance(journey, now)
        }
    }

    /** [player] vient de subir des dégâts : sa téléportation en attente s'annule, sans attendre le tick. Des dégâts mortels disent sa mort. */
    fun onDamaged(player: ServerPlayer) {
        val journey = journeys[player.uuid] ?: return
        cancel(journey, if (player.isDeadOrDying) CancellationCause.DIED else CancellationCause.DAMAGED)
    }

    /**
     * L'étape 4. Le point connu d'avance passe le contrôle de l'arrivée, lu dans le monde. Le point que la famille cherche elle-même ne le
     * passe pas : sa recherche en tient lieu.
     *
     * @param reach reçoit le point d'arrivée quand rien ne le refuse
     */
    private fun arrivalRefusal(request: TeleportRequest, reach: (Location) -> Unit): TeleportRefusal? {
        val destination = when (val arrival = request.arrival) {
            is Arrival.At -> {
                val danger = ArrivalRules.danger(ArrivalSurveyor.isExempt(request.player)) { ArrivalSurveyor.survey(request.player, arrival.destination) }
                if (danger != null) return TeleportRefusal.DangerousArrival(danger)
                arrival.destination
            }
            is Arrival.Searched -> when (val outcome = arrival.search()) {
                is SearchOutcome.Found -> outcome.destination
                is SearchOutcome.Refused -> return TeleportRefusal.OfFamily(outcome.message)
            }
        }
        reach(destination)
        return null
    }

    private fun advance(journey: Journey, now: Instant) {
        when (val step = journey.delay.step(now)) {
            DelayStep.Arrive -> arrive(journey, now)
            is DelayStep.Show -> messenger.tell(journey.request.player, step.message)
            DelayStep.Wait -> {}
        }
    }

    /**
     * L'étape 6. Le joueur part seul (cahier des charges, chapitre 2.5.7) : le jeu lui fait quitter sa monture. Ses laisses tombent ici, parce
     * que le jeu garderait en laisse un animal resté à portée de l'arrivée. Ses passagers descendent ici aussi : le jeu ne laisse aucune entité
     * monter sur un joueur, mais emmènerait celle qu'un autre mod y aurait mise.
     */
    private fun arrive(journey: Journey, now: Instant) {
        val request = journey.request
        val player = request.player
        val from = Location.of(player)
        val to = journey.destination
        journeys.remove(player.uuid)
        player.ejectPassengers()
        Leashable.leashableLeashedTo(player).forEach { it.dropLeash() }
        if (!player.teleportTo(to.level, to.x, to.y, to.z, emptySet(), to.yaw, to.pitch, true)) {
            // Le jeu ne refuse de téléporter qu'un joueur déjà retiré du monde, que le tick annule avant d'en arriver ici.
            ManyManyCommands.LOGGER.warn("The game refused to teleport {} to {} at {} {} {}", player.scoreboardName, to.dimension, to.x, to.y, to.z)
            return
        }
        cooldowns.start(player.uuid, request.table.family, now)
        request.quotaCharge?.let { quotaUsages.count(it.holder.uuid, it.family, it.quota, quotaNow(it)) }
        request.listener.onArrived(from, to)
    }

    /** Une téléportation annulée ne démarre pas le cooldown et ne compte aucun usage. */
    private fun cancel(journey: Journey, cause: CancellationCause) {
        val request = journey.request
        journeys.remove(request.player.uuid)
        cause.situation?.let { messenger.tell(request.player, it.with()) }
        request.listener.onCancelled(cause)
    }

    /** L'instant sur l'horloge du quota, en secondes : l'heure réelle, ou le temps de jeu que le jeu tient en statistique pour le joueur. */
    private fun quotaNow(charge: QuotaCharge): Long = when (charge.quota.clock) {
        QuotaClock.REAL -> clock.instant().epochSecond
        QuotaClock.PLAYTIME -> charge.holder.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)).toLong() / TICKS_PER_SECOND
    }

    /**
     * Une téléportation en attente, avec ce qu'elle garde de son lancement.
     *
     * @property departure le point de départ, dont le joueur ne s'éloigne pas
     */
    private class Journey(val request: TeleportRequest, val departure: Location, val destination: Location, val delay: ImmobilityDelay)
}
