// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyData
import fr.roumoulou.manymanycommands.rules.data.WrittenPosition
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaEntry
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/** Le contenu de `warps.json` (cahier des charges, chapitre 2.7.5) : les warps du serveur, et la plage de quota de chaque joueur. */
data class WarpsData(val warps: Warps = Warps(), val players: QuotaOnlyData = QuotaOnlyData())

/** Un warp tel qu'il s'écrit : sa position, et sa clé d'accès quand il en a une. */
data class WrittenWarp(val position: WrittenPosition, val key: String?) {

    companion object {

        const val KEY_KEY = "key"

        fun of(warp: Warp): WrittenWarp = WrittenWarp(WrittenPosition.of(warp.position), warp.key)
    }

    /** Les constats du warp écrit à [path], sans leur ligne : ceux de sa position, et une clé qui sort de la règle des noms en minuscules. */
    fun findings(path: String): List<FileFinding> = buildList {
        addAll(position.findings(path))
        if (key != null && !NameRules.isLowercaseName(key)) add(notAllowed(FindingPath.property(path, KEY_KEY), key, CommonMessages.ALLOWED_LOWERCASE_NAME.with()))
    }

    /** Le warp d'une écriture que [findings] admet. */
    fun value(): Warp = Warp(position.value(), key)
}

/**
 * `warps.json` tel qu'il s'écrit : sa version de schéma, ses warps par leur nom, puis l'entrée de chaque joueur, qui ne tient que sa plage.
 *
 * @property schemaVersion la version de schéma, celle du mod quand le fichier ne l'écrit pas
 */
data class WrittenWarpsData(val schemaVersion: Int = DataRules.SCHEMA_VERSION, val warps: Map<String, WrittenWarp> = emptyMap(), val players: Map<String, WrittenQuotaEntry> = emptyMap())

/** `warps.json` en règles : ses constats et son contenu, sur le mécanisme commun des fichiers de données, les warps en plus. */
object WarpsDataRules {

    const val WARPS_KEY = "warps"

    val fileName: String = DataRules.fileName(WarpsRules.family)

    /** Les constats, sans leur ligne : la version de schéma, chaque warp, puis chaque joueur. Un nom de warp suit la règle des noms. */
    fun findings(written: WrittenWarpsData): List<FileFinding> = buildList {
        addAll(DataRules.schemaFindings(written.schemaVersion))
        written.warps.forEach { (name, warp) ->
            val path = FindingPath.entry(WARPS_KEY, name)
            if (!NameRules.isName(name)) add(notAllowed(path, name, CommonMessages.ALLOWED_NAME.with()))
            addAll(warp.findings(path))
        }
        addAll(DataRules.playersFindings(written.players) { path, entry -> entry.findings(path) })
    }

    /** Le contenu d'un fichier que [findings] juge valide. */
    fun data(written: WrittenWarpsData): WarpsData = WarpsData(Warps(written.warps.mapValues { it.value.value() }), DataRules.players(written.players, WrittenQuotaEntry::value))

    fun written(data: WarpsData): WrittenWarpsData = WrittenWarpsData(DataRules.SCHEMA_VERSION, data.warps.warps.mapValues { WrittenWarp.of(it.value) }, DataRules.writtenPlayers(data.players, WrittenQuotaEntry::of))
}
