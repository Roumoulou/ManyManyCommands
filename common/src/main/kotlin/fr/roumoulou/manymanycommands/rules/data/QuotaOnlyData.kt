// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath

/** Le contenu d'un fichier de données qui ne tient que la plage de quota de chaque joueur (cahier des charges, chapitre 2.7.5) : `back.json`, `wild.json`. */
typealias QuotaOnlyData = FamilyData<NoHoldings>

/** L'entrée d'un joueur dans un tel fichier, telle qu'elle s'écrit : son nom et sa plage. Une clé qui manque vaut `null`. */
data class WrittenQuotaEntry(val name: String?, val quota: WrittenQuotaUsage?) {

    companion object {

        fun of(entry: PlayerEntry<NoHoldings>): WrittenQuotaEntry = WrittenQuotaEntry(entry.name, entry.quota?.let(WrittenQuotaUsage::of))
    }

    /** Les constats de l'entrée écrite à [path], sans leur ligne : ceux de sa plage. */
    fun findings(path: String): List<FileFinding> = quota?.findings(FindingPath.property(path, DataRules.QUOTA_KEY)).orEmpty()

    /** L'entrée d'une écriture que [findings] admet. */
    fun value(): PlayerEntry<NoHoldings> = PlayerEntry(name, NoHoldings, quota?.value())
}

/**
 * Un tel fichier en règles, pour une famille : ses constats et son contenu, par le mécanisme commun des fichiers de données.
 *
 * @property family la commande principale de la famille, qui nomme le fichier
 */
open class QuotaOnlyDataRules(val family: String) {

    val fileName: String = DataRules.fileName(family)

    fun findings(written: WrittenData<WrittenQuotaEntry>): List<FileFinding> = DataRules.findings(written) { path, entry -> entry.findings(path) }

    /** Le contenu d'un fichier que [findings] juge valide. */
    fun data(written: WrittenData<WrittenQuotaEntry>): QuotaOnlyData = DataRules.players(written, WrittenQuotaEntry::value)

    fun written(data: QuotaOnlyData): WrittenData<WrittenQuotaEntry> = DataRules.written(data, WrittenQuotaEntry::of)
}
