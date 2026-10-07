// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.common.Position
import java.time.Duration
import java.time.Instant

/**
 * Un créneau du tirage commun (`wild.md` des specs, chapitre 4) : le point que partagent les joueurs qui lancent `/wild timed` dans une
 * dimension pendant qu'il est ouvert.
 */
data class TimedWindow(val position: Position, val opensAt: Instant, val closesAt: Instant) {

    val durationSeconds: Long get() = Duration.between(opensAt, closesAt).seconds

    fun isOpen(now: Instant): Boolean = now < closesAt

    /** Ce qui reste du créneau à [now], en secondes, la dernière entamée comptée. */
    fun remainingSeconds(now: Instant): Long = (Duration.between(now, closesAt).toMillis().coerceAtLeast(0) + 999) / 1_000
}

/** Les créneaux du tirage commun, un par dimension, en mémoire : un créneau fini est oublié, le suivant repart d'un nouveau point. */
class TimedWindows {

    private val windows = HashMap<String, TimedWindow>()

    /** Le créneau ouvert dans [dimension] à [now], `null` quand il n'y en a pas ou que le dernier est fini. */
    fun current(dimension: String, now: Instant): TimedWindow? = windows[dimension]?.takeIf { it.isOpen(now) }

    /** Ouvre dans [dimension] un créneau de [durationSeconds] à partir de [now], sur [position]. */
    fun open(dimension: String, position: Position, now: Instant, durationSeconds: Long): TimedWindow =
        TimedWindow(position, now, now.plusSeconds(durationSeconds)).also { windows[dimension] = it }
}
