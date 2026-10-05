// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.ruleset.DimensionsRule
import fr.roumoulou.manymanycommands.rules.ruleset.IntegerRule
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaRule
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.SwitchesRule

/** Les règles et les réglages du retour, ceux de `rules\back.json` (`back.md` des specs, chapitre 4). La famille n'a pas d'alias. */
object BackRules : RulesTable("back") {

    /** Un interrupteur par source : toutes actives, sauf la mort. */
    val sources = rule(SwitchesRule("sources", shipped = BackSource.entries.associate { it.key to (it != BackSource.DEATH) }))

    override val forbiddenDepartureDimensions = rule(DimensionsRule("forbidden-departure-dimensions", shipped = emptySet()))

    override val delay = rule(IntegerRule("delay", shipped = 3, min = 0))

    override val cooldown = rule(IntegerRule("cooldown", shipped = 10, min = 0))

    override val quota = rule(QuotaRule("quota", shipped = null))
}

/** Ce qui enregistre la position de retour (`back.md` des specs, chapitre 2), sous la clé de son interrupteur. */
enum class BackSource(val key: String) {

    HOMES("homes"),

    WARPS("warps"),

    WILD("wild"),

    TPA("tpa"),

    BACK("back"),

    SEND("send"),

    DEATH("death"),
}
