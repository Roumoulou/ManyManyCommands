// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.ruleset.DimensionsRule
import fr.roumoulou.manymanycommands.rules.ruleset.IntegerRule
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaRule
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.SwitchRule
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/** Les règles et les réglages de la téléportation aléatoire, ceux de `rules\wild.json` (`wild.md` des specs, chapitre 6). */
object WildRules : RulesTable("wild") {

    override val aliases = setting(SwitchRule("aliases", shipped = true))

    /** La distance minimale au point d'apparition du monde, en blocs. Elle reste sous la distance maximale ([crossFindings]). */
    val minDistance = setting(IntegerRule("min-distance", shipped = 4_000, min = 0))

    /** La distance maximale, en blocs : supérieure à une distance minimale qui vaut 0 ou plus, donc 1 ou plus. */
    val maxDistance = setting(IntegerRule("max-distance", shipped = 10_000, min = 1))

    /** La durée du créneau du tirage commun. */
    val timedWindow = setting(IntegerRule("timed-window", shipped = 10, min = 1))

    /** Le Nether et l'End sont interdits par défaut : la surface n'y a pas de sens. */
    override val forbiddenDepartureDimensions = rule(DimensionsRule("forbidden-departure-dimensions", shipped = linkedSetOf("minecraft:the_nether", "minecraft:the_end")))

    /** 3 usages par plage de 8 heures, en heures réelles. Les deux tirages le partagent. */
    override val quota = rule(QuotaRule("quota", shipped = Quota(uses = 3, periodSeconds = 8 * 3_600L, clock = QuotaClock.REAL)))

    override val delay = rule(IntegerRule("delay", shipped = 3, min = 0))

    override val cooldown = rule(IntegerRule("cooldown", shipped = 3_600, min = 0))

    /**
     * La distance minimale est inférieure à la distance maximale. Le constat se pose sur la distance minimale, avec la plage que la maximale lui
     * laisse : une seule des deux clés le porte, pour qu'une seule faute ne donne pas deux constats.
     */
    override fun crossFindings(settings: RuleValues): List<FileFinding> {
        val min = settings[minDistance]
        val max = settings[maxDistance]
        return if (min >= max) listOf(notAllowed(minDistance.key, min, CommonMessages.ALLOWED_RANGE.with(minDistance.min, max - 1))) else emptyList()
    }
}
