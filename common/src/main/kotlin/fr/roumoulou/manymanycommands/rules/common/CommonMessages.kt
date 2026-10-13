// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import fr.roumoulou.manymanycommands.rules.message.MessageFamily

/** Les textes du cahier des charges, communs à toutes les familles. */
object CommonMessages : MessageFamily("common") {

    // Les durées (chapitre 2.6.6) : une clé au singulier et une au pluriel par unité, et l'assemblage de deux unités.
    val DURATION_DAYS_ONE = fragment("duration.days.one")
    val DURATION_DAYS_MANY = fragment("duration.days.many")
    val DURATION_HOURS_ONE = fragment("duration.hours.one")
    val DURATION_HOURS_MANY = fragment("duration.hours.many")
    val DURATION_MINUTES_ONE = fragment("duration.minutes.one")
    val DURATION_MINUTES_MANY = fragment("duration.minutes.many")
    val DURATION_SECONDS_ONE = fragment("duration.seconds.one")
    val DURATION_SECONDS_MANY = fragment("duration.seconds.many")
    val DURATION_PAIR = fragment("duration.pair")

    // Les noms (chapitre 2.3) : le refus d'un nom invalide.
    val NAME_INVALID = refusal("name.invalid")

    // La source d'une commande (chapitre 2.8) : le refus de la console pour une commande qui a besoin d'une position.
    val IN_GAME_ONLY = refusal("source.in_game_only")

    // Le placement (chapitre 2.9) : le refus d'un point hors de la bordure du monde ou de la hauteur de la dimension.
    val PLACEMENT_OUT_OF_BOUNDS = refusal("placement.out_of_bounds")

    // Ce qu'un joueur lit au survol d'un message, la console le reçoit en clair à la suite du message ; de même pour chaque mot cliquable, avec sa commande.
    val CONSOLE_DETAIL = fragment("console.detail")
    val CONSOLE_CHOICE = fragment("console.choice")

    // Les constats d'un fichier refusé (chapitre 2.7.4).
    val FINDING_UNKNOWN_KEY = fragment("finding.unknown_key")
    val FINDING_WRONG_NATURE = fragment("finding.wrong_nature")
    val FINDING_NOT_ALLOWED = fragment("finding.not_allowed")
    val FINDING_SCHEMA_TOO_RECENT = fragment("finding.schema_too_recent")
    val FINDING_UNREADABLE = fragment("finding.unreadable")
    val FINDING_MISSING_KEY = fragment("finding.missing_key")

    // La nature attendue d'une valeur, argument de FINDING_WRONG_NATURE.
    val NATURE_TEXT = fragment("nature.text")
    val NATURE_INTEGER = fragment("nature.integer")
    val NATURE_NUMBER = fragment("nature.number")
    val NATURE_BOOLEAN = fragment("nature.boolean")
    val NATURE_OBJECT = fragment("nature.object")
    val NATURE_LIST = fragment("nature.list")

    // Les valeurs admises qui ne s'énumèrent pas, argument de FINDING_NOT_ALLOWED.
    val ALLOWED_AT_LEAST = fragment("allowed.at_least")
    val ALLOWED_RANGE = fragment("allowed.range")
    val ALLOWED_NAME = fragment("allowed.name")
    val ALLOWED_LOWERCASE_NAME = fragment("allowed.lowercase_name")
    val ALLOWED_DIMENSION = fragment("allowed.dimension")
    val ALLOWED_PLAYER_ID = fragment("allowed.player_id")
    val ALLOWED_NONE = fragment("allowed.none")

    // Le déroulé d'une téléportation (chapitre 2.5.8) : ses refus, son compte à rebours, ses annulations.
    val TELEPORT_PENDING = refusal("teleport.pending")
    val TELEPORT_COOLDOWN = refusal("teleport.cooldown")
    val TELEPORT_ARRIVAL_OBSTRUCTED = refusal("teleport.arrival.obstructed")
    val TELEPORT_ARRIVAL_LAVA_OR_FIRE = refusal("teleport.arrival.lava_or_fire")
    val TELEPORT_ARRIVAL_VOID = refusal("teleport.arrival.void")
    val TELEPORT_COUNTDOWN = countdown("teleport.countdown")
    val TELEPORT_CANCELLED_MOVED = refusal("teleport.cancelled.moved")
    val TELEPORT_CANCELLED_DAMAGED = refusal("teleport.cancelled.damaged")
    val TELEPORT_CANCELLED_DIED = refusal("teleport.cancelled.died")
    val TELEPORT_CANCELLED_CHANGED_DIMENSION = refusal("teleport.cancelled.changed_dimension")
}
