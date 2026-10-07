// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.teleport.TeleportSteps

/** Les refus propres de `/wild` et de `/wild timed` (`wild.md` des specs, chapitre 5), l'étape 1 du déroulé : la dimension interdite, puis le quota. */
object WildRefusals {

    /**
     * Le premier refus, ou `null` quand rien ne refuse.
     *
     * @param departure la dimension où se tient le joueur
     * @param quota le quota du joueur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun first(ruleSet: RuleValues, departure: String, quota: Quota?, quotaWaitSeconds: () -> Long): Message? {
        if (TeleportSteps.isDepartureForbidden(WildRules, ruleSet, departure)) return WildMessages.DEPARTURE_FORBIDDEN.with(departure)
        if (quota != null) {
            val wait = quotaWaitSeconds()
            if (wait > 0) return WildMessages.quotaReached(quota.uses, DurationWording.text(wait))
        }
        return null
    }
}
