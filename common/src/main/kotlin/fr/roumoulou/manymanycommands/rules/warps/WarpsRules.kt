// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.ruleset.DimensionsRule
import fr.roumoulou.manymanycommands.rules.ruleset.IntegerRule
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaRule
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.SwitchRule

/** Les règles et les réglages des warps, ceux de `rules\warps.json` (`warps.md` des specs, chapitre 4). */
object WarpsRules : RulesTable("warps") {

    override val aliases = setting(SwitchRule("aliases", shipped = true))

    override val forbiddenDepartureDimensions = rule(DimensionsRule("forbidden-departure-dimensions", shipped = emptySet()))

    override val delay = rule(IntegerRule("delay", shipped = 3, min = 0))

    override val cooldown = rule(IntegerRule("cooldown", shipped = 10, min = 0))

    override val quota = rule(QuotaRule("quota", shipped = null))
}
