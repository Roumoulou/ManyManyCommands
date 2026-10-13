// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.FamilyData
import fr.roumoulou.manymanycommands.rules.data.Holdings
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaUsage
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath

/** Ce que les demandes tiennent d'un joueur dans `tpa.json` (cahier des charges, chapitre 2.7.5) : la réception fermée (`tpa.md` des specs, chapitre 3.5). */
data class TpaHoldings(val requestsClosed: Boolean = false) : Holdings {

    override val isEmpty: Boolean get() = !requestsClosed

    /** `/tpatoggle` : la réception fermée se rouvre, l'ouverte se ferme. */
    fun toggled(): TpaHoldings = TpaHoldings(!requestsClosed)
}

/** Le contenu de `tpa.json` : la réception et la plage de quota de chaque joueur (cahier des charges, chapitre 2.7.5). Les demandes elles-mêmes vivent en mémoire. */
typealias TpaData = FamilyData<TpaHoldings>

/** `tpa.json` en règles : ses constats et son contenu, par le mécanisme commun des fichiers de données. */
object TpaDataRules {

    val fileName: String = DataRules.fileName(TpaRules.family)

    fun findings(written: WrittenData<WrittenTpaEntry>): List<FileFinding> = DataRules.findings(written) { path, entry -> entry.findings(path) }

    /** Le contenu d'un fichier que [findings] juge valide. */
    fun data(written: WrittenData<WrittenTpaEntry>): TpaData = DataRules.players(written, WrittenTpaEntry::value)

    fun written(data: TpaData): WrittenData<WrittenTpaEntry> = DataRules.written(data, WrittenTpaEntry::of)
}

/**
 * L'entrée d'un joueur dans `tpa.json` telle qu'elle s'écrit (cahier des charges, chapitre 2.7.5) : son nom, `requests-closed` et sa plage de
 * quota. Une clé qui manque vaut `null`, et la réception est alors ouverte.
 */
data class WrittenTpaEntry(val name: String?, val requestsClosed: Boolean?, val quota: WrittenQuotaUsage?) {

    companion object {

        const val REQUESTS_CLOSED_KEY = "requests-closed"

        /** `requests-closed` ne s'écrit que fermée : absente, la réception est ouverte. */
        fun of(entry: PlayerEntry<TpaHoldings>): WrittenTpaEntry = WrittenTpaEntry(entry.name, true.takeIf { entry.holdings.requestsClosed }, entry.quota?.let(WrittenQuotaUsage::of))
    }

    /** Les constats de l'entrée écrite à [path], sans leur ligne : ceux de sa plage. */
    fun findings(path: String): List<FileFinding> = quota?.findings(FindingPath.property(path, DataRules.QUOTA_KEY)).orEmpty()

    /** L'entrée d'une écriture que [findings] admet. */
    fun value(): PlayerEntry<TpaHoldings> = PlayerEntry(name, TpaHoldings(requestsClosed == true), quota?.value())
}
