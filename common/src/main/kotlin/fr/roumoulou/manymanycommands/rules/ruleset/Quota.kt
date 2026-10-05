// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

/**
 * Le quota d'une famille, réglé dans un jeu de règles (cahier des charges, chapitre 2.5.9) : un nombre d'usages par plage de temps.
 *
 * @property periodSeconds la durée de la plage
 * @property clock l'horloge qui mesure la plage
 */
data class Quota(val uses: Long, val periodSeconds: Long, val clock: QuotaClock = QuotaClock.REAL)

/** L'horloge d'un quota, telle que la clé `clock` l'écrit. */
enum class QuotaClock(val value: String) {

    /** L'heure réelle. */
    REAL("real"),

    /** Le temps de jeu du joueur, pris dans la statistique que le jeu tient pour lui. */
    PLAYTIME("playtime");

    companion object {

        fun of(value: String): QuotaClock? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Un quota tel qu'un fichier l'écrit : chaque clé vaut `null` quand elle manque. C'est [QuotaRule] qui juge qu'il est écrit entier.
 */
data class WrittenQuota(val uses: Long?, val period: Long?, val clock: String?) {

    companion object {

        const val USES_KEY = "uses"
        const val PERIOD_KEY = "period"
        const val CLOCK_KEY = "clock"
    }
}
