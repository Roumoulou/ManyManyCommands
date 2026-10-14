// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageFamily
import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/** Les messages de `homes.md` des specs (chapitre 7), la branche `player` comprise. */
object HomesMessages : MessageFamily("homes") {

    // La création (chapitre 3.1). Le nom invalide est celui de la règle commune.
    val CREATED = success("created")
    val FORBIDDEN_DIMENSION = refusal("forbidden_dimension")
    val LIMIT_REACHED_ONE = refusal("limit_reached.one")
    val LIMIT_REACHED_MANY = refusal("limit_reached.many")
    val ALREADY_EXISTS = refusal("already_exists")

    /** Le home absent : un seul et même refus pour toute commande qui le cible. */
    val ABSENT = refusal("absent")

    // Rejoindre (chapitre 3.2) : les refus propres, dans l'ordre de la fiche après le home absent, puis l'arrivée.
    val NO_DEFAULT = refusal("no_default")
    val DEPARTURE_FORBIDDEN = refusal("departure_forbidden")
    val DIMENSION_MISSING = refusal("dimension_missing")
    val QUOTA_REACHED_ONE = refusal("quota_reached.one")
    val QUOTA_REACHED_MANY = refusal("quota_reached.many")
    val ARRIVED = success("arrived")

    val DELETED = success("deleted")

    // La liste (chapitre 3.4) : l'en-tête, chaque home, le home par défaut marqué, la liste vide, et la position d'un home, au survol ou en clair à la console.
    val LIST_HEADER = display("list.header")
    val LIST_HOME = display("list.home")
    val LIST_DEFAULT_HOME = display("list.default_home")
    val LIST_EMPTY = display("list.empty")
    val LIST_POSITION = fragment("list.position")

    val RENAMED = success("renamed")
    val SAME_NAME = refusal("same_name")

    val MOVED = success("moved")

    val DEFAULT_SET = success("default_set")

    // La branche player (chapitre 4) : chaque succès nomme aussi le propriétaire, l'en-tête de sa liste, l'envoi chez l'administrateur et chez le joueur envoyé, le joueur inconnu.
    val CREATED_OF_PLAYER = success("player.created")
    val ARRIVED_OF_PLAYER = success("player.arrived")
    val SENT_ONE = success("player.sent.one")
    val SENT_MANY = success("player.sent.many")
    val SENT_TO = display("player.sent_to")
    val DELETED_OF_PLAYER = success("player.deleted")
    val LIST_HEADER_OF_PLAYER = display("player.list.header")
    val RENAMED_OF_PLAYER = success("player.renamed")
    val MOVED_OF_PLAYER = success("player.moved")
    val DEFAULT_SET_OF_PLAYER = success("player.default_set")
    val UNKNOWN_PLAYER = refusal("player.unknown")

    /** Le fichier de données est invalide (cahier des charges, chapitre 2.7.3). */
    val UNAVAILABLE = refusal("unavailable")

    /** La limite atteinte : une quantité, au singulier pour 1 et au pluriel sinon (cahier des charges, chapitre 2.6.4). */
    fun limitReached(max: Long): Message = (if (max == 1L) LIMIT_REACHED_ONE else LIMIT_REACHED_MANY).with(max)

    /** Le quota atteint : le nombre d'usages, une quantité, et ce qui reste à attendre, une durée. */
    fun quotaReached(uses: Long, wait: TranslatableText): Message = (if (uses == 1L) QUOTA_REACHED_ONE else QUOTA_REACHED_MANY).with(uses, wait)

    /** Les joueurs envoyés au home [name] de [owner], chez l'administrateur : une quantité. */
    fun sent(count: Int, name: String, owner: String): Message = (if (count == 1) SENT_ONE else SENT_MANY).with(count, name, owner)
}
