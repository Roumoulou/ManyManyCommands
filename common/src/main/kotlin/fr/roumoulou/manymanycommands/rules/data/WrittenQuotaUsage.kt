// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.ruleset.missingKey
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage

/**
 * La plage en cours d'un quota telle que le fichier de données l'écrit (cahier des charges, chapitre 2.7.5) : chaque clé vaut `null` quand
 * elle manque, et les trois sont obligatoires.
 */
data class WrittenQuotaUsage(val clock: String?, val windowStart: Long?, val uses: Long?) {

    companion object {

        const val CLOCK_KEY = "clock"
        const val WINDOW_START_KEY = "window-start"
        const val USES_KEY = "uses"

        fun of(usage: QuotaUsage): WrittenQuotaUsage = WrittenQuotaUsage(usage.clock.value, usage.windowStart, usage.uses)
    }

    /** Les constats de la plage écrite à [path], sans leur ligne : une clé qui manque, une horloge inconnue, un compte négatif. */
    fun findings(path: String): List<FileFinding> = buildList {
        when {
            clock == null -> add(missingKey(path, CLOCK_KEY))
            QuotaClock.of(clock) == null -> add(notAllowed(FindingPath.property(path, CLOCK_KEY), clock, QuotaClock.entries.joinToString(", ") { it.value }))
        }
        addAll(wholeNumber(path, WINDOW_START_KEY, windowStart))
        addAll(wholeNumber(path, USES_KEY, uses))
    }

    /** La plage d'une écriture que [findings] admet. */
    fun value(): QuotaUsage = QuotaUsage(
        clock = QuotaClock.of(clock ?: error("Unvalidated quota usage without '$CLOCK_KEY'")) ?: error("Unvalidated quota clock '$clock'"),
        windowStart = windowStart ?: error("Unvalidated quota usage without '$WINDOW_START_KEY'"),
        uses = uses ?: error("Unvalidated quota usage without '$USES_KEY'"),
    )

    private fun wholeNumber(path: String, key: String, written: Long?): List<FileFinding> = when {
        written == null -> listOf(missingKey(path, key))
        written < 0 -> listOf(notAllowed(FindingPath.property(path, key), written, CommonMessages.ALLOWED_AT_LEAST.with(0L)))
        else -> emptyList()
    }
}
