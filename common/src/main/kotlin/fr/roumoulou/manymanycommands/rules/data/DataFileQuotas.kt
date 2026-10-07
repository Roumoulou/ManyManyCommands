// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import java.util.UUID

/**
 * Les plages de quota d'une famille, conservées dans son fichier de données (cahier des charges, chapitres 2.5.9 et 2.7.5) : la plage d'un
 * joueur se lit dans son entrée, et un usage la récrit avec l'entrée, tout de suite. Elles survivent ainsi au redémarrage du serveur.
 *
 * Tant que le fichier est indisponible, rien ne se lit ni ne s'écrit : la famille l'est aussi.
 *
 * @param empty ce que la famille tient d'un joueur qui n'a encore rien, pour l'entrée dont seule la plage compte
 */
class DataFileQuotas<HOLDINGS : Holdings>(private val file: DataFile<FamilyData<HOLDINGS>>, private val empty: HOLDINGS) {

    fun usage(player: UUID): QuotaUsage? = file.content?.get(player)?.quota

    /** Garde la plage de [player] après un usage, sous [playerName], son nom du moment. */
    fun record(player: UUID, playerName: String, usage: QuotaUsage) {
        val data = file.content ?: return
        val entry = data[player]
        file.update(data.with(player, PlayerEntry(playerName, entry?.holdings ?: empty, usage)))
    }
}
