// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.ruleset.DimensionsRule
import fr.roumoulou.manymanycommands.rules.ruleset.IntegerRule
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaRule
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.SwitchRule

/** Les règles et les réglages des homes, ceux de `rules\homes.json` (`homes.md` des specs, chapitre 5). */
object HomesRules : RulesTable("homes") {

    override val aliases = setting(SwitchRule("aliases", shipped = true))

    /** La limite de homes : aucune valeur ne la lève, et 0 interdit toute création. */
    val maxHomes = rule(IntegerRule("max-homes", shipped = 3, min = 0))

    /** Les dimensions où les homes sont interdits, pour `create` et `move`. */
    val forbiddenDimensions = rule(DimensionsRule("forbidden-dimensions", shipped = emptySet()))

    override val forbiddenDepartureDimensions = rule(DimensionsRule("forbidden-departure-dimensions", shipped = emptySet()))

    override val delay = rule(IntegerRule("delay", shipped = 3, min = 0))

    override val cooldown = rule(IntegerRule("cooldown", shipped = 10, min = 0))

    override val quota = rule(QuotaRule("quota", shipped = null))
}
