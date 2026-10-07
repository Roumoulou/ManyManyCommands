// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.homes

import fr.roumoulou.manymanycommands.files.DataStore
import fr.roumoulou.manymanycommands.files.data.PositionDocument
import fr.roumoulou.manymanycommands.files.data.QuotaUsageDocument
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.homes.HomesData
import fr.roumoulou.manymanycommands.rules.homes.HomesDataRules
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.homes.WrittenHomesEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `homes.json` dans le dossier du monde : les homes et la plage de quota de chaque joueur (cahier des charges, chapitre 2.7.5). */
object HomesDataFile {

    /** Le fichier de données des homes dans [worldDirectory], le dossier du mod dans celui du monde. */
    fun at(worldDirectory: Path): DataFile<HomesData> {
        val path = worldDirectory.resolve("${HomesRules.family}.json")
        return DataFile(HomesDataRules.fileName, read = { read(path) }, write = { write(path, it) })
    }

    /** Lit [path], que le mod crée vide quand il manque : le contenu d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<HomesData> = when (val reading = DataStore.read<HomesDocument>(path) { HomesDataRules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(HomesDataRules.data(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }

    /** Écrit [data] dans [path], entier et d'un coup. */
    fun write(path: Path, data: HomesData) = DataStore.write(path, HomesDocument.of(HomesDataRules.written(data)))
}

/** `homes.json` tel qu'il s'écrit. Le constructeur sans argument est le fichier vide : c'est de lui que naît le fichier qui manque. */
@Serializable
data class HomesDocument(
    @SerialName(DataRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = DataRules.SCHEMA_VERSION,
    @SerialName(DataRules.PLAYERS_KEY) val players: Map<String, HomesEntryDocument> = emptyMap(),
) {

    companion object {

        fun of(written: WrittenData<WrittenHomesEntry>): HomesDocument = HomesDocument(written.schemaVersion, written.players.mapValues { HomesEntryDocument.of(it.value) })
    }

    fun toWritten(): WrittenData<WrittenHomesEntry> = WrittenData(schemaVersion, players.mapValues { it.value.toWritten() })
}

/** L'entrée d'un joueur dans `homes.json`, dans l'ordre où le fichier l'écrit. */
@Serializable
data class HomesEntryDocument(
    @SerialName(DataRules.NAME_KEY) val name: String? = null,
    @SerialName(WrittenHomesEntry.DEFAULT_HOME_KEY) val defaultHome: String? = null,
    @SerialName(WrittenHomesEntry.HOMES_KEY) val homes: Map<String, PositionDocument> = emptyMap(),
    @SerialName(DataRules.QUOTA_KEY) val quota: QuotaUsageDocument? = null,
) {

    companion object {

        fun of(written: WrittenHomesEntry): HomesEntryDocument =
            HomesEntryDocument(written.name, written.defaultHome, written.homes.mapValues { PositionDocument.of(it.value) }, written.quota?.let(QuotaUsageDocument::of))
    }

    fun toWritten(): WrittenHomesEntry = WrittenHomesEntry(name, defaultHome, homes.mapValues { it.value.toWritten() }, quota?.toWritten())
}
