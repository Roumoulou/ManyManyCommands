// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageFamily
import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/** Les messages de `back.md` des specs (chapitre 5). Le refus de la console est celui de la règle commune. */
object BackMessages : MessageFamily("back") {

    // Les refus propres de /back (chapitre 3), dans l'ordre de la fiche, puis l'arrivée.
    val NO_POSITION = refusal("no_position")
    val DEPARTURE_FORBIDDEN = refusal("departure_forbidden")
    val DIMENSION_MISSING = refusal("dimension_missing")
    val QUOTA_REACHED_ONE = refusal("quota_reached.one")
    val QUOTA_REACHED_MANY = refusal("quota_reached.many")
    val ARRIVED = success("arrived")

    /** Le fichier de données est invalide (cahier des charges, chapitre 2.7.3). */
    val UNAVAILABLE = refusal("unavailable")

    /** Le quota atteint : le nombre d'usages, une quantité, et ce qui reste à attendre, une durée. */
    fun quotaReached(uses: Long, wait: TranslatableText): Message = (if (uses == 1L) QUOTA_REACHED_ONE else QUOTA_REACHED_MANY).with(uses, wait)
}
