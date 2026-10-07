// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageFamily
import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/** Les messages de `warps.md` des specs (chapitre 6). Le nom invalide, le placement refusé et la console sont ceux des règles communes. */
object WarpsMessages : MessageFamily("warps") {

    // La création (chapitre 3.1).
    val CREATED = success("created")
    val ALREADY_EXISTS = refusal("already_exists")

    /** Le warp absent, ou réservé à d'autres : un seul et même refus pour toute commande qui le cible. */
    val ABSENT = refusal("absent")

    // Rejoindre (chapitre 2.1) : les refus propres après le warp absent, dans l'ordre de la fiche, puis l'arrivée.
    val DEPARTURE_FORBIDDEN = refusal("departure_forbidden")
    val DIMENSION_MISSING = refusal("dimension_missing")
    val QUOTA_REACHED_ONE = refusal("quota_reached.one")
    val QUOTA_REACHED_MANY = refusal("quota_reached.many")
    val ARRIVED = success("arrived")

    // L'envoi (chapitre 3.6) : chez l'administrateur, le nombre de joueurs envoyés, et chez chaque joueur envoyé.
    val SENT_ONE = success("sent.one")
    val SENT_MANY = success("sent.many")
    val SENT_TO = display("sent_to")

    val DELETED = success("deleted")

    // La liste (chapitre 2.2) : l'en-tête, chaque warp, le warp réservé avec sa clé pour qui a le nœud de restrict, la liste vide, et la position d'un warp.
    val LIST_HEADER = display("list.header")
    val LIST_WARP = display("list.warp")
    val LIST_RESERVED = display("list.reserved")
    val LIST_EMPTY = display("list.empty")
    val LIST_POSITION = fragment("list.position")

    val RENAMED = success("renamed")
    val SAME_NAME = refusal("same_name")

    val MOVED = success("moved")

    // Réserver (chapitre 3.5).
    val RESTRICTED = success("restricted")
    val KEY_INVALID = refusal("key_invalid")
    val UNRESTRICTED = success("unrestricted")

    /** Le fichier de données est invalide (cahier des charges, chapitre 2.7.3). */
    val UNAVAILABLE = refusal("unavailable")

    /** Le quota atteint : le nombre d'usages, une quantité, et ce qui reste à attendre, une durée. */
    fun quotaReached(uses: Long, wait: TranslatableText): Message = (if (uses == 1L) QUOTA_REACHED_ONE else QUOTA_REACHED_MANY).with(uses, wait)

    /** Les joueurs envoyés, une quantité, au warp [name]. */
    fun sent(count: Int, name: String): Message = (if (count == 1) SENT_ONE else SENT_MANY).with(count, name)
}
