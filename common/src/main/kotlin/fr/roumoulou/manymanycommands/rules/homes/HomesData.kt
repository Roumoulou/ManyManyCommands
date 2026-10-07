// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.FamilyData
import fr.roumoulou.manymanycommands.rules.data.PlayerEntry
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.data.WrittenPosition
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaUsage
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/** Le contenu de `homes.json` : les homes et la plage de quota de chaque joueur (cahier des charges, chapitre 2.7.5). */
typealias HomesData = FamilyData<PlayerHomes>

/** `homes.json` en règles : ses constats et son contenu, par le mécanisme commun des fichiers de données. */
object HomesDataRules {

    val fileName: String = DataRules.fileName(HomesRules.family)

    fun findings(written: WrittenData<WrittenHomesEntry>): List<FileFinding> = DataRules.findings(written) { path, entry -> entry.findings(path) }

    /** Le contenu d'un fichier que [findings] juge valide. */
    fun data(written: WrittenData<WrittenHomesEntry>): HomesData = DataRules.players(written, WrittenHomesEntry::value)

    fun written(data: HomesData): WrittenData<WrittenHomesEntry> = DataRules.written(data, WrittenHomesEntry::of)
}

/**
 * L'entrée d'un joueur dans `homes.json` telle qu'elle s'écrit (cahier des charges, chapitre 2.7.5) : son nom, son home désigné, ses homes
 * et sa plage de quota. Une clé qui manque vaut `null`, et les homes qui manquent sont aucun home.
 */
data class WrittenHomesEntry(val name: String?, val defaultHome: String?, val homes: Map<String, WrittenPosition>, val quota: WrittenQuotaUsage?) {

    companion object {

        const val DEFAULT_HOME_KEY = "default-home"
        const val HOMES_KEY = "homes"

        fun of(entry: PlayerEntry<PlayerHomes>): WrittenHomesEntry =
            WrittenHomesEntry(entry.name, entry.holdings.designated, entry.holdings.homes.mapValues { WrittenPosition.of(it.value) }, entry.quota?.let(WrittenQuotaUsage::of))
    }

    /**
     * Les constats de l'entrée écrite à [path], sans leur ligne : un nom de home qui sort de la règle des noms, une position incomplète, un
     * `default-home` qui ne nomme aucun home du joueur, une plage de quota fautive.
     */
    fun findings(path: String): List<FileFinding> = buildList {
        homes.forEach { (name, position) ->
            val homePath = FindingPath.entry(FindingPath.property(path, HOMES_KEY), name)
            if (!NameRules.isName(name)) add(notAllowed(homePath, name, CommonMessages.ALLOWED_NAME.with()))
            addAll(position.findings(homePath))
        }
        if (defaultHome != null && defaultHome !in homes) {
            add(notAllowed(FindingPath.property(path, DEFAULT_HOME_KEY), defaultHome, homes.keys.joinToString(", ").ifEmpty { CommonMessages.ALLOWED_NONE.with() }))
        }
        if (quota != null) addAll(quota.findings(FindingPath.property(path, DataRules.QUOTA_KEY)))
    }

    /** L'entrée d'une écriture que [findings] admet. */
    fun value(): PlayerEntry<PlayerHomes> = PlayerEntry(name, PlayerHomes(homes.mapValues { it.value.value() }, defaultHome), quota?.value())
}
