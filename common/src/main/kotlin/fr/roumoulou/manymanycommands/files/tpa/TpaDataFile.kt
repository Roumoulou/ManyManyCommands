// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.tpa

import fr.roumoulou.manymanycommands.files.DataStore
import fr.roumoulou.manymanycommands.files.data.QuotaUsageDocument
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.tpa.TpaData
import fr.roumoulou.manymanycommands.rules.tpa.TpaDataRules
import fr.roumoulou.manymanycommands.rules.tpa.TpaRules
import fr.roumoulou.manymanycommands.rules.tpa.WrittenTpaEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `tpa.json` dans le dossier du monde : la réception et la plage de quota de chaque joueur (cahier des charges, chapitre 2.7.5). */
object TpaDataFile {

    /** Le fichier de données des demandes dans [worldDirectory], le dossier du mod dans celui du monde. */
    fun at(worldDirectory: Path): DataFile<TpaData> {
        val path = worldDirectory.resolve("${TpaRules.family}.json")
        return DataFile(TpaDataRules.fileName, read = { read(path) }, write = { write(path, it) })
    }

    /** Lit [path], que le mod crée vide quand il manque : le contenu d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<TpaData> = when (val reading = DataStore.read<TpaDocument>(path) { TpaDataRules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(TpaDataRules.data(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }

    /** Écrit [data] dans [path], entier et d'un coup. */
    fun write(path: Path, data: TpaData) = DataStore.write(path, TpaDocument.of(TpaDataRules.written(data)))
}

/** `tpa.json` tel qu'il s'écrit. Le constructeur sans argument est le fichier vide : c'est de lui que naît le fichier qui manque. */
@Serializable
data class TpaDocument(
    @SerialName(DataRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = DataRules.SCHEMA_VERSION,
    @SerialName(DataRules.PLAYERS_KEY) val players: Map<String, TpaEntryDocument> = emptyMap(),
) {

    companion object {

        fun of(written: WrittenData<WrittenTpaEntry>): TpaDocument = TpaDocument(written.schemaVersion, written.players.mapValues { TpaEntryDocument.of(it.value) })
    }

    fun toWritten(): WrittenData<WrittenTpaEntry> = WrittenData(schemaVersion, players.mapValues { it.value.toWritten() })
}

/** L'entrée d'un joueur dans `tpa.json`, dans l'ordre où le fichier l'écrit. */
@Serializable
data class TpaEntryDocument(
    @SerialName(DataRules.NAME_KEY) val name: String? = null,
    @SerialName(WrittenTpaEntry.REQUESTS_CLOSED_KEY) val requestsClosed: Boolean? = null,
    @SerialName(DataRules.QUOTA_KEY) val quota: QuotaUsageDocument? = null,
) {

    companion object {

        fun of(written: WrittenTpaEntry): TpaEntryDocument = TpaEntryDocument(written.name, written.requestsClosed, written.quota?.let(QuotaUsageDocument::of))
    }

    fun toWritten(): WrittenTpaEntry = WrittenTpaEntry(name, requestsClosed, quota?.toWritten())
}
