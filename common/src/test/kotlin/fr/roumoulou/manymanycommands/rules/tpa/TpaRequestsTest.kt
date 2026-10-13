// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Les demandes en règles (`tpa.md` des specs, chapitres 2 à 4) : l'ordre des refus de l'envoi, la demande sortante unique, les demandes reçues
 * et la dernière d'entre elles, l'expiration, l'intervalle, la chute à la déconnexion.
 */
class TpaRequestsTest {

    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val alex = UUID.fromString("7d1ea2c7-5c2a-4b12-9f44-0a5a0b7c1d2e")

    private val notch = UUID.fromString("b0a7c9f1-3e2d-4f6a-8b9c-1d2e3f4a5b6c")

    private val names = mapOf(steve to "Steve", alex to "Alex", notch to "Notch")

    /** Les réglages du serveur des demandes, tels qu'un fichier les écrirait : la durée de vie et l'intervalle, aux valeurs livrées par défaut. */
    private fun settings(lifetime: Long = 60, interval: Long = 60): RuleValues =
        TpaRules.values(WrittenRules(settings = mapOf(TpaRules.requestLifetime.writes(lifetime), TpaRules.requestInterval.writes(interval)))).settings

    /** Un envoi de [requester] à [recipient], chacun sous son nom ; [name] est le nom tapé quand personne ne le porte. */
    private fun send(
        requests: TpaRequests,
        requester: UUID,
        recipient: UUID?,
        name: String? = null,
        kind: RequestKind = RequestKind.TPA,
        closed: Boolean = false,
        settings: RuleValues = settings(),
        quota: Quota? = null,
        wait: Long = 0,
        now: Instant = noon,
    ): TpaSending = requests.send(requester, names.getValue(requester), recipient, name ?: recipient?.let(names::getValue) ?: "Herobrine", kind, closed, settings, quota, { wait }, now)

    private fun sent(outcome: TpaSending): TpaSending.Sent = outcome as? TpaSending.Sent ?: throw AssertionError("The request must be sent, was $outcome")

    private fun refused(outcome: TpaSending): Message = (outcome as? TpaSending.Refused)?.message ?: throw AssertionError("The request must be refused, was $outcome")

    private fun request(answer: TpaAnswer): TpaRequest = (answer as? TpaAnswer.Request)?.request ?: throw AssertionError("A request must answer, was $answer")

    private fun refused(answer: TpaAnswer): Message = (answer as? TpaAnswer.Refused)?.message ?: throw AssertionError("The answer must be refused, was $answer")

    // ── La demande (chapitres 1 et 2) ──

    @Test
    fun `une demande envoyée dit qui voyage et chez qui, nomme ses deux joueurs, et vit sa durée de vie`() {
        val requests = TpaRequests()

        val tpa = sent(send(requests, steve, alex)).request
        val tpahere = sent(send(requests, alex, steve, kind = RequestKind.TPAHERE, settings = settings(lifetime = 30), now = noon.plusSeconds(1))).request

        assertEquals(TpaRequest(steve, "Steve", alex, "Alex", RequestKind.TPA, noon, noon.plusSeconds(60)), tpa)
        assertEquals(steve to alex, tpa.traveler to tpa.host, "By /tpa, the requester travels to the recipient")
        assertEquals(steve to alex, tpahere.traveler to tpahere.host, "By /tpahere, the recipient travels to the requester")
        assertEquals(noon.plusSeconds(31), tpahere.expiresAt)
        assertEquals(30L, tpahere.lifetimeSeconds)
        assertEquals(tpa, requests.outgoing(steve))
        assertEquals(listOf(tpa), requests.received(alex))
        assertEquals(listOf(tpa, tpahere), requests.pending)
        assertEquals(tpa, requests.from(steve, alex))
        assertNull(requests.from(steve, notch))
        assertEquals(alex to "Alex", tpa.other(steve) to tpa.nameOf(alex))
        assertEquals(steve to "Steve", tpa.other(alex) to tpa.nameOf(steve))
    }

    @Test
    fun `un joueur n'a qu'une demande sortante, la nouvelle annule la précédente, qui est rendue`() {
        val requests = TpaRequests()
        val first = sent(send(requests, steve, alex)).request

        val second = sent(send(requests, steve, notch, now = noon.plusSeconds(1)))

        assertEquals(first, second.cancelled)
        assertEquals(second.request, requests.outgoing(steve))
        assertEquals(emptyList<TpaRequest>(), requests.received(alex), "The cancelled request waits nowhere")
        assertEquals(listOf(second.request), requests.received(notch))
        assertNull(sent(send(requests, alex, steve)).cancelled, "A first request cancels nothing")
    }

    @Test
    fun `un joueur reçoit plusieurs demandes, dans l'ordre de leur envoi, et la dernière reçue répond sans nom`() {
        val requests = TpaRequests()
        val fromAlex = sent(send(requests, alex, steve)).request
        val fromNotch = sent(send(requests, notch, steve, now = noon.plusSeconds(1))).request

        assertEquals(listOf(fromAlex, fromNotch), requests.received(steve))
        assertEquals(fromNotch, request(requests.answer(steve, null, null)), "Without a name, the last received")
        assertEquals(fromAlex, request(requests.answer(steve, alex, "Alex")), "With a name, the request of that player")
        assertEquals(TpaMessages.NONE_FROM.with("Herobrine"), refused(requests.answer(steve, null, "Herobrine")), "A name that no connected player bears")
        assertEquals(TpaMessages.NONE_FROM.with("Steve"), refused(requests.answer(alex, steve, "Steve")), "A connected player without a request to this recipient")

        val renewed = sent(send(requests, alex, steve, settings = settings(interval = 0), now = noon.plusSeconds(2))).request
        assertEquals(listOf(fromNotch, renewed), requests.received(steve), "A request sent again takes its place at the end")
        assertEquals(renewed, request(requests.answer(steve, null, null)))
    }

    @Test
    fun `une demande consommée n'attend plus, et consommer une demande déjà remplacée ne retire pas la nouvelle`() {
        val requests = TpaRequests()
        val first = sent(send(requests, alex, steve)).request
        val fromNotch = sent(send(requests, notch, steve)).request

        requests.consume(fromNotch)
        assertEquals(listOf(first), requests.received(steve))
        assertEquals(TpaMessages.NONE_FROM.with("Notch"), refused(requests.answer(steve, notch, "Notch")))

        val renewed = sent(send(requests, alex, steve, settings = settings(interval = 0), now = noon.plusSeconds(1))).request
        requests.consume(first)
        assertEquals(renewed, requests.outgoing(alex), "The replaced request is gone already: consuming it leaves the new one")

        requests.consume(renewed)
        assertEquals(TpaMessages.NONE_PENDING.with(), refused(requests.answer(steve, null, null)))
    }

    @Test
    fun `une demande expire au terme de sa durée de vie, et l'expiration la retire`() {
        val requests = TpaRequests()
        val request = sent(send(requests, steve, alex, settings = settings(lifetime = 30))).request
        val late = sent(send(requests, notch, alex, settings = settings(lifetime = 30), now = noon.plusSeconds(10))).request

        assertEquals(emptyList<TpaRequest>(), requests.expire(noon.plusSeconds(29)))
        assertEquals(listOf(request), requests.expire(noon.plusSeconds(30)), "Expired at the end of its lifetime")
        assertNull(requests.outgoing(steve))
        assertEquals(listOf(late), requests.received(alex))
        assertEquals(listOf(late), requests.expire(noon.plusSeconds(40)))
        assertEquals(emptyList<TpaRequest>(), requests.received(alex))
    }

    @Test
    fun `la déconnexion fait tomber les demandes que le joueur a envoyées et reçues`() {
        val requests = TpaRequests()
        val toAlex = sent(send(requests, steve, alex)).request
        val fromNotch = sent(send(requests, notch, steve, now = noon.plusSeconds(1))).request
        val aside = sent(send(requests, alex, notch, now = noon.plusSeconds(2))).request

        assertEquals(listOf(toAlex, fromNotch), requests.drop(steve))

        assertNull(requests.outgoing(steve))
        assertNull(requests.outgoing(notch))
        assertEquals(aside, requests.outgoing(alex), "A request between two other players stays")
        assertEquals(emptyList<TpaRequest>(), requests.drop(steve), "Nothing drops twice")
    }

    // ── Demander (chapitres 3.1 et 4) ──

    @Test
    fun `l'envoi refuse dans l'ordre de la fiche, soi-même, non connecté, réception fermée, demande trop rapprochée, quota atteint`() {
        val requests = TpaRequests()
        sent(send(requests, steve, alex))
        val quota = Quota(uses = 1, periodSeconds = 3_600)
        val later = noon.plusSeconds(10)

        assertEquals(TpaMessages.SELF.with(), refused(send(requests, steve, steve, closed = true, quota = quota, wait = 120, now = later)))
        assertEquals(TpaMessages.NOT_CONNECTED.with("Herobrine"), refused(send(requests, steve, null, closed = true, quota = quota, wait = 120, now = later)))
        assertEquals(TpaMessages.CLOSED.with("Alex"), refused(send(requests, steve, alex, closed = true, quota = quota, wait = 120, now = later)))
        assertEquals(TpaMessages.TOO_SOON.with(DurationWording.text(50), "Alex"), refused(send(requests, steve, alex, quota = quota, wait = 120, now = later)))
        assertEquals(TpaMessages.quotaReached(1, DurationWording.text(120)), refused(send(requests, steve, alex, quota = quota, wait = 120, now = noon.plusSeconds(60))))
        assertEquals(noon.plusSeconds(60), sent(send(requests, steve, alex, quota = quota, wait = 0, now = noon.plusSeconds(60))).request.sentAt, "Nothing refuses any more")
    }

    @Test
    fun `le quota ne se consulte qu'avec un quota`() {
        val requests = TpaRequests()

        sent(requests.send(steve, "Steve", alex, "Alex", RequestKind.TPA, false, settings(), null, { error("Without a quota, the wait is not consulted") }, noon))
    }

    @Test
    fun `l'intervalle se mesure d'un envoi au suivant au même joueur, quelle qu'en soit l'issue, et un intervalle de 0 ne refuse rien`() {
        val requests = TpaRequests()
        sent(send(requests, steve, alex))
        requests.cancel(steve)

        assertEquals(30L, requests.intervalRemainingSeconds(steve, alex, 60, noon.plusMillis(30_500)), "Rounded up")
        assertEquals(TpaMessages.TOO_SOON.with(DurationWording.text(30), "Alex"), refused(send(requests, steve, alex, now = noon.plusSeconds(30))), "Cancelled or not, the request counts")
        sent(send(requests, steve, notch, now = noon.plusSeconds(30)))
        assertEquals(0L, requests.intervalRemainingSeconds(steve, alex, 60, noon.plusSeconds(60)))
        sent(send(requests, steve, alex, now = noon.plusSeconds(60)))
        sent(send(requests, steve, alex, settings = settings(interval = 0), now = noon.plusSeconds(61)))
        assertEquals(0L, requests.intervalRemainingSeconds(alex, steve, 60, noon), "Never sent: nothing to wait")
    }

    // ── Annuler (chapitre 3.4) ──

    @Test
    fun `tpacancel retire la demande envoyée, et rend null sans demande`() {
        val requests = TpaRequests()
        val request = sent(send(requests, steve, alex)).request

        assertEquals(request, requests.cancel(steve))

        assertNull(requests.outgoing(steve))
        assertNull(requests.cancel(steve))
    }
}
