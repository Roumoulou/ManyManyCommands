// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import java.time.Duration
import java.time.Instant

/**
 * Le délai d'immobilité d'une téléportation en attente (cahier des charges, chapitre 2.5.2), et son compte à rebours.
 *
 * Il garde la durée de son lancement : un rechargement ne la change pas en cours de route. Il ne lit jamais l'heure, et dit à chaque instant
 * qu'on lui donne ce que cet instant demande.
 *
 * @param startedAt l'instant où la commande a lancé la téléportation
 * @param delaySeconds le délai du jeu de règles du joueur, ou 0 pour qui en est dispensé
 */
class ImmobilityDelay(startedAt: Instant, delaySeconds: Long) {

    /** L'instant de l'arrivée. */
    val endsAt: Instant = startedAt.plusSeconds(delaySeconds)

    private var shownSeconds: Long? = null

    /**
     * Ce que [now] demande : l'arrivée quand le délai est écoulé, sinon le compte à rebours à afficher, une fois par seconde. Un délai de 0
     * demande l'arrivée tout de suite.
     */
    fun step(now: Instant): DelayStep {
        if (!now.isBefore(endsAt)) return DelayStep.Arrive
        val seconds = secondsLeft(now)
        if (seconds == shownSeconds) return DelayStep.Wait
        shownSeconds = seconds
        return DelayStep.Show(seconds)
    }

    /** Les secondes qui restent, arrondies au-dessus : le compte à rebours part du délai entier et ne montre jamais 0. */
    private fun secondsLeft(now: Instant): Long = Math.ceilDiv(Duration.between(now, endsAt).toMillis(), 1_000L)
}

/** Ce qu'un instant demande à une téléportation en attente. */
sealed interface DelayStep {

    /** Le délai est écoulé : le joueur est téléporté. */
    data object Arrive : DelayStep

    /** Une seconde a passé : le compte à rebours s'affiche dans la barre d'action. */
    data class Show(val seconds: Long) : DelayStep {

        val message: Message get() = CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(seconds))
    }

    /** Rien : la seconde en cours est déjà affichée. */
    data object Wait : DelayStep
}
