// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable

/**
 * Les refus que partagent les familles qui téléportent vers un endroit connu d'avance (`homes.md` des specs, chapitre 3.2, `back.md`, chapitre 3),
 * dans l'ordre de leurs fiches, après le refus de l'endroit lui-même : la dimension de départ interdite, la dimension de l'arrivée introuvable,
 * le quota atteint. Chaque famille donne ses messages.
 */
object DestinationRefusals {

    /**
     * Le premier de ces refus, ou `null` quand rien ne refuse.
     *
     * @param departure la dimension où se tient le joueur
     * @param destination la dimension de l'endroit à rejoindre
     * @param isDimensionKnown le serveur porte la dimension
     * @param quota le quota du joueur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun first(table: RulesTable, ruleSet: RuleValues, departure: String, destination: String, isDimensionKnown: (String) -> Boolean, quota: Quota?, quotaWaitSeconds: () -> Long, texts: DestinationTexts): Message? {
        if (TeleportSteps.isDepartureForbidden(table, ruleSet, departure)) return texts.departureForbidden(departure)
        if (!isDimensionKnown(destination)) return texts.dimensionMissing(destination)
        if (quota != null) {
            val wait = quotaWaitSeconds()
            if (wait > 0) return texts.quotaReached(quota.uses, DurationWording.text(wait))
        }
        return null
    }
}

/** Les messages d'une famille pour les refus de [DestinationRefusals], chacun avec ce qu'il nomme. */
class DestinationTexts(val departureForbidden: (dimension: String) -> Message, val dimensionMissing: (dimension: String) -> Message, val quotaReached: (uses: Long, wait: TranslatableText) -> Message)
