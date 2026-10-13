// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Le sens du voyage d'une demande (`tpa.md` des specs, chapitre 1) : qui voyage, et chez qui. */
enum class RequestKind {

    /** `/tpa <player>` : le demandeur rejoint le destinataire. */
    TPA,

    /** `/tpahere <player>` : le destinataire rejoint le demandeur. */
    TPAHERE,
}

/**
 * Une demande en attente (`tpa.md` des specs, chapitres 1 et 2).
 *
 * @property requester le joueur qui l'a envoyée
 * @property recipient le joueur qui en décide
 * @property sentAt l'instant de l'envoi
 * @property expiresAt l'instant où elle expire, au terme de sa durée de vie
 */
data class TpaRequest(val requester: UUID, val recipient: UUID, val kind: RequestKind, val sentAt: Instant, val expiresAt: Instant) {

    /** Le joueur qui voyage à l'acceptation. */
    val traveler: UUID get() = if (kind == RequestKind.TPA) requester else recipient

    /** Le joueur que le voyageur rejoint. */
    val host: UUID get() = if (kind == RequestKind.TPA) recipient else requester

    fun involves(player: UUID): Boolean = player == requester || player == recipient

    /** L'autre joueur de la demande, vu de [player]. */
    fun other(player: UUID): UUID = if (player == requester) recipient else requester

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}

/** L'issue de `/tpa <player>` ou de `/tpahere <player>` : un refus, ou la demande envoyée, avec celle qu'elle annule. */
sealed interface TpaSending {

    data class Refused(val message: Message) : TpaSending

    /** @property cancelled la demande sortante que le demandeur avait, annulée par celle-ci, ou `null` */
    data class Sent(val request: TpaRequest, val cancelled: TpaRequest?) : TpaSending
}

/** La demande que `/tpaaccept [<player>]` ou `/tpadeny [<player>]` vise : un refus, ou la demande. */
sealed interface TpaAnswer {

    data class Refused(val message: Message) : TpaAnswer

    data class Request(val request: TpaRequest) : TpaAnswer
}

/**
 * Les demandes en attente (`tpa.md` des specs, chapitres 2 et 3), et les derniers envois d'où se mesure l'intervalle entre deux demandes au
 * même joueur (chapitre 4). Tout vit en mémoire et s'efface avec l'état du mod, au redémarrage du serveur.
 *
 * Chaque commande y est une règle pure : elle reçoit des valeurs et l'instant, et rend son issue. Les joueurs y sont des UUID, et un message
 * nomme l'autre joueur par le nom qu'on lui donne.
 */
class TpaRequests {

    /** La demande sortante de chaque demandeur, une au plus, dans l'ordre des envois : la dernière reçue par un joueur est la dernière ici. */
    private val outgoing = LinkedHashMap<UUID, TpaRequest>()

    /** Le dernier envoi d'un demandeur à un destinataire, quelle qu'en ait été l'issue. */
    private val lastSentAt = HashMap<Pair<UUID, UUID>, Instant>()

    fun outgoing(requester: UUID): TpaRequest? = outgoing[requester]

    /** Les demandes que [recipient] a reçues et qui attendent, dans l'ordre de leur envoi. */
    fun received(recipient: UUID): List<TpaRequest> = outgoing.values.filter { it.recipient == recipient }

    /** La demande de [requester] qui attend chez [recipient], ou `null`. */
    fun from(requester: UUID, recipient: UUID): TpaRequest? = outgoing[requester]?.takeIf { it.recipient == recipient }

    /** Ce qui reste de l'intervalle avant une nouvelle demande de [requester] à [recipient], en secondes arrondies au-dessus, et 0 quand elle est possible. */
    fun intervalRemainingSeconds(requester: UUID, recipient: UUID, intervalSeconds: Long, now: Instant): Long {
        val last = lastSentAt[requester to recipient] ?: return 0
        val remaining = Duration.between(now, last.plusSeconds(intervalSeconds))
        return if (remaining.isNegative || remaining.isZero) 0 else Math.ceilDiv(remaining.toMillis(), 1_000L)
    }

    /**
     * `/tpa <player>` et `/tpahere <player>` (chapitre 3.1) : le premier des refus, dans l'ordre de la fiche, ou la demande envoyée, qui annule
     * la demande sortante que le demandeur avait (chapitre 2).
     *
     * @param recipient le joueur connecté que `<player>` désigne, `null` quand aucun ne porte ce nom
     * @param recipientName le nom tapé, celui des messages
     * @param isRecipientClosed le destinataire a fermé la réception des demandes
     * @param settings les réglages du serveur des demandes : la durée de vie et l'intervalle
     * @param quota le quota du demandeur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun send(
        requester: UUID,
        recipient: UUID?,
        recipientName: String,
        kind: RequestKind,
        isRecipientClosed: Boolean,
        settings: RuleValues,
        quota: Quota?,
        quotaWaitSeconds: () -> Long,
        now: Instant,
    ): TpaSending {
        if (recipient == requester) return TpaSending.Refused(TpaMessages.SELF.with())
        if (recipient == null) return TpaSending.Refused(TpaMessages.NOT_CONNECTED.with(recipientName))
        if (isRecipientClosed) return TpaSending.Refused(TpaMessages.CLOSED.with(recipientName))
        val interval = intervalRemainingSeconds(requester, recipient, settings[TpaRules.requestInterval], now)
        if (interval > 0) return TpaSending.Refused(TpaMessages.TOO_SOON.with(DurationWording.text(interval), recipientName))
        if (quota != null) {
            val wait = quotaWaitSeconds()
            if (wait > 0) return TpaSending.Refused(TpaMessages.quotaReached(quota.uses, DurationWording.text(wait)))
        }
        val cancelled = outgoing.remove(requester)
        val request = TpaRequest(requester, recipient, kind, now, now.plusSeconds(settings[TpaRules.requestLifetime]))
        outgoing[requester] = request
        lastSentAt[requester to recipient] = now
        return TpaSending.Sent(request, cancelled)
    }

    /**
     * `/tpaaccept [<player>]` et `/tpadeny [<player>]` (chapitres 3.2 et 3.3) : la demande du joueur nommé, ou, sans nom, la dernière reçue.
     *
     * @param requester le joueur connecté que `<player>` désigne, `null` sans nom ou quand aucun ne porte ce nom
     * @param requesterName le nom tapé, `null` sans nom
     */
    fun answer(recipient: UUID, requester: UUID?, requesterName: String?): TpaAnswer {
        if (requesterName == null) return received(recipient).lastOrNull()?.let { TpaAnswer.Request(it) } ?: TpaAnswer.Refused(TpaMessages.NONE_PENDING.with())
        val request = requester?.let { from(it, recipient) } ?: return TpaAnswer.Refused(TpaMessages.NONE_FROM.with(requesterName))
        return TpaAnswer.Request(request)
    }

    /** Retire [request], que sa réponse consomme (chapitre 3.2), et rien quand elle n'attendait plus. */
    fun consume(request: TpaRequest) {
        outgoing.remove(request.requester, request)
    }

    /** `/tpacancel` (chapitre 3.4) : la demande que [requester] avait envoyée, retirée, ou `null` quand il n'en avait pas. */
    fun cancel(requester: UUID): TpaRequest? = outgoing.remove(requester)

    /** Les demandes expirées à [now] (chapitre 2), retirées, dans l'ordre de leur envoi. */
    fun expire(now: Instant): List<TpaRequest> = outgoing.values.filter { it.isExpired(now) }.onEach { outgoing.remove(it.requester) }

    /** Les demandes que [player], qui se déconnecte, avait envoyées ou reçues (chapitre 2), retirées, dans l'ordre de leur envoi. */
    fun drop(player: UUID): List<TpaRequest> = outgoing.values.filter { it.involves(player) }.onEach { outgoing.remove(it.requester) }
}
