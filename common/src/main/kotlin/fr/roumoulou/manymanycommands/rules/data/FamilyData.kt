// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import java.util.UUID

/**
 * L'entrée d'un joueur dans le fichier de données d'une famille (cahier des charges, chapitre 2.7.5).
 *
 * @property name le dernier nom connu du joueur, que le mod récrit avec l'entrée et ne relit pas ; `null` quand le fichier ne l'écrit pas
 * @property holdings ce que la famille tient de lui
 * @property quota la plage en cours de son quota dans la famille, `null` quand aucune n'est ouverte
 */
data class PlayerEntry<HOLDINGS : Holdings>(val name: String?, val holdings: HOLDINGS, val quota: QuotaUsage? = null) {

    /** L'entrée ne porte que son nom : elle n'a pas de place dans le fichier. */
    val isEmpty: Boolean get() = holdings.isEmpty && quota == null
}

/**
 * Le contenu du fichier de données d'une famille : l'entrée de chaque joueur, par son UUID, dans l'ordre du fichier. Un joueur sans entrée
 * n'y tient rien.
 */
data class FamilyData<HOLDINGS : Holdings>(val players: Map<UUID, PlayerEntry<HOLDINGS>> = emptyMap()) {

    operator fun get(player: UUID): PlayerEntry<HOLDINGS>? = players[player]

    /** Les données où [player] a [entry], à sa place s'il en avait une, ou plus d'entrée quand elle ne porte que son nom. */
    fun with(player: UUID, entry: PlayerEntry<HOLDINGS>): FamilyData<HOLDINGS> = FamilyData(if (entry.isEmpty) players - player else players + (player to entry))
}
