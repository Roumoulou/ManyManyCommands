// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Les cooldowns des joueurs (cahier des charges, chapitre 2.5.4) : chaque famille tient le sien, pour chaque joueur.
 *
 * Un cooldown suit le joueur et non sa session : il se range sous son UUID, et une reconnexion ne l'efface pas. Il vit en mémoire, et s'efface
 * avec l'état du mod, au redémarrage du serveur.
 */
class Cooldowns {

    private val arrivals = HashMap<Pair<UUID, String>, Instant>()

    /** Le cooldown de [family] démarre pour [player] : il vient d'arriver. */
    fun start(player: UUID, family: String, now: Instant) {
        arrivals[player to family] = now
    }

    /**
     * Ce qui reste du cooldown de [family] pour [player], en secondes arrondies au-dessus, et 0 quand il est écoulé ou n'a jamais démarré.
     *
     * Seule l'arrivée est gardée : un cooldown en cours se mesure à la durée qu'on lui donne ici, celle des règles en vigueur.
     *
     * @param cooldownSeconds le cooldown du jeu de règles du joueur, ou 0 pour qui en est dispensé
     */
    fun remainingSeconds(player: UUID, family: String, cooldownSeconds: Long, now: Instant): Long {
        val arrival = arrivals[player to family] ?: return 0
        val remaining = Duration.between(now, arrival.plusSeconds(cooldownSeconds))
        return if (remaining.isNegative) 0 else Math.ceilDiv(remaining.toMillis(), 1_000L)
    }
}
