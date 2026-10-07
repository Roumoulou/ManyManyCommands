// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import java.util.UUID

/**
 * La plage en cours du quota d'un joueur dans une famille (cahier des charges, chapitre 2.5.9).
 *
 * @property clock l'horloge sur laquelle la plage s'est ouverte
 * @property windowStart l'instant du premier usage de la plage, en secondes sur cette horloge
 * @property uses les usages comptés dans la plage
 */
data class QuotaUsage(val clock: QuotaClock, val windowStart: Long, val uses: Long)

/**
 * La règle du quota (cahier des charges, chapitre 2.5.9) : un nombre d'usages par plage de temps.
 *
 * L'instant se reçoit en secondes sur l'horloge du quota : l'heure réelle, comptée depuis l'époque Unix, ou le temps de jeu du joueur. La règle
 * ne lit ni l'une ni l'autre. Une plage se mesure au quota qu'on donne ici, celui des règles en vigueur.
 */
object QuotaRules {

    /**
     * Les secondes à attendre avant le prochain usage, et 0 quand il est possible tout de suite.
     *
     * @param quota le quota du jeu de règles du joueur, `null` quand la famille n'en a pas ou que le joueur en est dispensé
     * @param usage la plage en cours du joueur, `null` quand il n'en a pas
     */
    fun waitSeconds(quota: Quota?, usage: QuotaUsage?, now: Long): Long {
        if (quota == null) return 0
        val window = openWindow(quota, usage, now) ?: return 0
        return if (window.uses < quota.uses) 0 else window.windowStart + quota.periodSeconds - now
    }

    /**
     * La plage du joueur après un usage, qui se compte à l'arrivée. Le premier usage ouvre la plage, et l'usage qui suit une plage finie en ouvre
     * une nouvelle.
     */
    fun afterUse(quota: Quota, usage: QuotaUsage?, now: Long): QuotaUsage {
        val window = openWindow(quota, usage, now) ?: return QuotaUsage(quota.clock, windowStart = now, uses = 1)
        return window.copy(uses = window.uses + 1)
    }

    /** La plage de [usage] si elle dure encore à [now]. Une plage ouverte sur une autre horloge que celle du quota ne se mesure plus : elle est finie. */
    private fun openWindow(quota: Quota, usage: QuotaUsage?, now: Long): QuotaUsage? = usage?.takeIf { it.clock == quota.clock && now < it.windowStart + quota.periodSeconds }
}

/**
 * Où se lisent et s'écrivent les plages de quota des joueurs, chaque famille tenant les siennes (cahier des charges, chapitre 2.5.9) : dans le
 * fichier de données de la famille, qui les conserve au redémarrage du serveur, ou en mémoire.
 */
interface QuotaLedger {

    fun usage(player: UUID, family: String): QuotaUsage?

    /**
     * Garde la plage de [player] dans [family] après un usage.
     *
     * @param playerName le nom du joueur du moment, que le fichier de données écrit avec son entrée
     */
    fun record(player: UUID, playerName: String, family: String, usage: QuotaUsage)
}

/** Les plages de quota en mémoire, pour les familles dont aucun fichier de données ne les conserve encore : elles s'effacent au redémarrage. */
class QuotaUsages : QuotaLedger {

    private val usages = HashMap<Pair<UUID, String>, QuotaUsage>()

    override fun usage(player: UUID, family: String): QuotaUsage? = usages[player to family]

    override fun record(player: UUID, playerName: String, family: String, usage: QuotaUsage) {
        usages[player to family] = usage
    }
}
