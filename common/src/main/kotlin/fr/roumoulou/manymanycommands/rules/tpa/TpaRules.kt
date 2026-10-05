// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.ruleset.DimensionsRule
import fr.roumoulou.manymanycommands.rules.ruleset.IntegerRule
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaRule
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable

/** Les règles et les réglages des demandes de téléportation, ceux de `rules\tpa.json` (`tpa.md` des specs, chapitre 4). La famille n'a pas d'alias. */
object TpaRules : RulesTable("tpa") {

    /** La durée de vie d'une demande. */
    val requestLifetime = setting(IntegerRule("request-lifetime", shipped = 60, min = 1))

    /** L'intervalle entre deux demandes au même joueur. */
    val requestInterval = setting(IntegerRule("request-interval", shipped = 60, min = 0))

    override val forbiddenDepartureDimensions = rule(DimensionsRule("forbidden-departure-dimensions", shipped = emptySet()))

    override val delay = rule(IntegerRule("delay", shipped = 3, min = 0))

    override val cooldown = rule(IntegerRule("cooldown", shipped = 10, min = 0))

    override val quota = rule(QuotaRule("quota", shipped = null))
}
