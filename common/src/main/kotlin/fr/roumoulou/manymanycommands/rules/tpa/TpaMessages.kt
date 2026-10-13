// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageFamily
import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/** Les messages de `tpa.md` des specs (chapitre 5). Le refus de la console est celui de la règle commune. */
object TpaMessages : MessageFamily("tpa") {

    // L'envoi (chapitre 3.1) : la confirmation, la demande reçue selon son sens, ses deux mots cliquables, puis les refus dans l'ordre de la fiche.
    val SENT = success("sent")
    val RECEIVED_TPA = display("received.tpa")
    val RECEIVED_TPAHERE = display("received.tpahere")
    val ACCEPT_WORD = fragment("received.accept")
    val DENY_WORD = fragment("received.deny")
    val SELF = refusal("self")
    val NOT_CONNECTED = refusal("not_connected")
    val CLOSED = refusal("closed")
    val TOO_SOON = refusal("too_soon")
    val QUOTA_REACHED_ONE = refusal("quota_reached.one")
    val QUOTA_REACHED_MANY = refusal("quota_reached.many")

    // L'acceptation, le refus, l'annulation et l'expiration (chapitre 2), chez le demandeur puis chez le destinataire.
    val ACCEPTED_REQUESTER = success("accepted.requester")
    val ACCEPTED_RECIPIENT = success("accepted.recipient")
    val DENIED_REQUESTER = refusal("denied.requester")
    val DENIED_RECIPIENT = success("denied.recipient")
    val CANCELLED_REQUESTER = success("cancelled.requester")
    val CANCELLED_RECIPIENT = display("cancelled.recipient")
    val EXPIRED_REQUESTER = display("expired.requester")
    val EXPIRED_RECIPIENT = display("expired.recipient")

    // Répondre ou annuler sans demande (chapitres 3.2 à 3.4).
    val NONE_PENDING = refusal("none_pending")
    val NONE_FROM = refusal("none_from")
    val NONE_TO_CANCEL = refusal("none_to_cancel")

    // Le voyage (chapitre 3.2) : la dimension de départ interdite, l'arrivée chez le voyageur et chez l'hôte, et la téléportation qui n'a pas eu lieu.
    val DEPARTURE_FORBIDDEN = refusal("departure_forbidden")
    val ARRIVED_TRAVELER = success("arrived.traveler")
    val ARRIVED_HOST = display("arrived.host")
    val NOT_DONE = display("not_done")

    // Ce qui a refusé ou annulé la téléportation du voyageur, à la troisième personne : l'argument de NOT_DONE.
    val CAUSE_DEPARTURE_FORBIDDEN = fragment("cause.departure_forbidden")
    val CAUSE_PENDING = fragment("cause.pending")
    val CAUSE_COOLDOWN = fragment("cause.cooldown")
    val CAUSE_OBSTRUCTED = fragment("cause.obstructed")
    val CAUSE_LAVA_OR_FIRE = fragment("cause.lava_or_fire")
    val CAUSE_VOID = fragment("cause.void")
    val CAUSE_MOVED = fragment("cause.moved")
    val CAUSE_DAMAGED = fragment("cause.damaged")
    val CAUSE_DIED = fragment("cause.died")
    val CAUSE_CHANGED_DIMENSION = fragment("cause.changed_dimension")
    val CAUSE_DISCONNECTED = fragment("cause.disconnected")

    /** L'autre joueur s'est déconnecté : la demande est tombée (chapitre 2). */
    val DISCONNECTED = display("disconnected")

    // La réception (chapitre 3.5).
    val RECEPTION_CLOSED = success("reception.closed")
    val RECEPTION_OPENED = success("reception.opened")

    /** Le fichier de données est invalide (cahier des charges, chapitre 2.7.3). */
    val UNAVAILABLE = refusal("unavailable")

    /** Le quota atteint : le nombre d'usages, une quantité, et ce qui reste à attendre, une durée. */
    fun quotaReached(uses: Long, wait: TranslatableText): Message = (if (uses == 1L) QUOTA_REACHED_ONE else QUOTA_REACHED_MANY).with(uses, wait)
}
