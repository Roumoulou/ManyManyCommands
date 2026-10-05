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

    // Les constats d'un fichier refusé (chapitre 2.7.4).
    val FINDING_UNKNOWN_KEY = fragment("finding.unknown_key")
    val FINDING_WRONG_NATURE = fragment("finding.wrong_nature")
    val FINDING_NOT_ALLOWED = fragment("finding.not_allowed")
    val FINDING_SCHEMA_TOO_RECENT = fragment("finding.schema_too_recent")
    val FINDING_UNREADABLE = fragment("finding.unreadable")

    // La nature attendue d'une valeur, argument de FINDING_WRONG_NATURE.
    val NATURE_TEXT = fragment("nature.text")
    val NATURE_INTEGER = fragment("nature.integer")
    val NATURE_NUMBER = fragment("nature.number")
    val NATURE_BOOLEAN = fragment("nature.boolean")
    val NATURE_OBJECT = fragment("nature.object")
    val NATURE_LIST = fragment("nature.list")
}
