// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageFamily
import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/** Les messages de `wild.md` des specs (chapitre 8). La console est refusée par le texte des règles communes. */
object WildMessages : MessageFamily("wild") {

    /** L'arrivée : les coordonnées entières du point rejoint. */
    val ARRIVED = success("arrived")

    // Le tirage commun (chapitre 4) : le joueur ouvre un créneau, ou en rejoint un.
    val TIMED_OPENED = display("timed.opened")
    val TIMED_JOINED = display("timed.joined")

    // Les refus propres (chapitre 5), dans l'ordre de la fiche.
    val DEPARTURE_FORBIDDEN = refusal("departure_forbidden")
    val QUOTA_REACHED_ONE = refusal("quota_reached.one")
    val QUOTA_REACHED_MANY = refusal("quota_reached.many")

    // La recherche du point sûr (chapitre 3).
    val NO_SAFE_POINT = refusal("no_safe_point")
    val BORDER_TOO_NARROW = refusal("border_too_narrow")

    /** Le fichier de données est invalide (cahier des charges, chapitre 2.7.3). */
    val UNAVAILABLE = refusal("unavailable")

    /** Le quota atteint : le nombre d'usages, une quantité, et ce qui reste à attendre, une durée. */
    fun quotaReached(uses: Long, wait: TranslatableText): Message = (if (uses == 1L) QUOTA_REACHED_ONE else QUOTA_REACHED_MANY).with(uses, wait)
}
