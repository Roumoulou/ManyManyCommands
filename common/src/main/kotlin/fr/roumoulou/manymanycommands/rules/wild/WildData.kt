// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyData
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyDataRules

/** Le contenu de `wild.json` : la plage de quota de chaque joueur, et rien d'autre (cahier des charges, chapitre 2.7.5). Les créneaux du tirage commun vivent en mémoire. */
typealias WildData = QuotaOnlyData

/** `wild.json` en règles. */
object WildDataRules : QuotaOnlyDataRules(WildRules.family)
