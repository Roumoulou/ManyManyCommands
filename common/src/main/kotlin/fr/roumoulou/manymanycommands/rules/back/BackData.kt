// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyData
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyDataRules

/** Le contenu de `back.json` : la plage de quota de chaque joueur, et rien d'autre (`back.md` des specs, chapitre 4). La position de retour vit en mémoire. */
typealias BackData = QuotaOnlyData

/** `back.json` en règles. */
object BackDataRules : QuotaOnlyDataRules(BackRules.family)
