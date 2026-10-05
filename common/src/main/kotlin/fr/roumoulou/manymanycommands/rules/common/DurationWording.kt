// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import fr.roumoulou.manymanycommands.rules.message.Fragment
import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/**
 * L'écriture d'une durée (cahier des charges, chapitre 2.6.6) : en secondes sous la minute, puis par ses deux plus grandes unités.
 *
 * Les deux unités se suivent : la plus grande qui compte, et celle juste en dessous. La seconde ne s'écrit pas quand elle vaut zéro, et ce qui
 * reste sous elle est tronqué : 2 heures et 30 secondes s'écrivent `2 heures`.
 */
object DurationWording {

    fun parts(totalSeconds: Long): List<DurationPart> {
        require(totalSeconds >= 0) { "A duration cannot be negative, was $totalSeconds seconds" }
        val units = DurationUnit.entries
        val largest = units.firstOrNull { totalSeconds >= it.seconds } ?: DurationUnit.SECONDS
        val first = DurationPart(largest, totalSeconds / largest.seconds)
        val below = units.getOrNull(largest.ordinal + 1) ?: return listOf(first)
        val second = DurationPart(below, totalSeconds % largest.seconds / below.seconds)
        return if (second.count == 0L) listOf(first) else listOf(first, second)
    }

    fun text(totalSeconds: Long): TranslatableText {
        val parts = parts(totalSeconds).map { it.text() }
        return if (parts.size == 1) parts.single() else CommonMessages.DURATION_PAIR.with(parts[0], parts[1])
    }
}

/** Une unité de durée, de la plus grande à la plus petite, avec ses deux clés : chaque unité s'accorde avec son nombre. */
enum class DurationUnit(val seconds: Long, val one: Fragment, val many: Fragment) {

    DAYS(86_400, CommonMessages.DURATION_DAYS_ONE, CommonMessages.DURATION_DAYS_MANY),

    HOURS(3_600, CommonMessages.DURATION_HOURS_ONE, CommonMessages.DURATION_HOURS_MANY),

    MINUTES(60, CommonMessages.DURATION_MINUTES_ONE, CommonMessages.DURATION_MINUTES_MANY),

    SECONDS(1, CommonMessages.DURATION_SECONDS_ONE, CommonMessages.DURATION_SECONDS_MANY),
}

data class DurationPart(val unit: DurationUnit, val count: Long) {

    /** Le singulier pour 1 seul, le pluriel pour tout autre nombre. */
    fun text(): TranslatableText = (if (count == 1L) unit.one else unit.many).with(count)
}
