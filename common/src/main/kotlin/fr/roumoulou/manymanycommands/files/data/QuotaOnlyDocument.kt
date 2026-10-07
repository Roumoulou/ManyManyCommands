// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.data

import fr.roumoulou.manymanycommands.files.DataStore
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyData
import fr.roumoulou.manymanycommands.rules.data.QuotaOnlyDataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaEntry
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** La lecture et l'écriture d'un fichier de données qui ne tient que les plages de quota, communes aux familles qui en ont un : `back.json`, `wild.json`. */
object QuotaOnlyDataFiles {

    /** Le fichier de données de la famille de [rules] dans [worldDirectory], le dossier du mod dans celui du monde. */
    fun at(worldDirectory: Path, rules: QuotaOnlyDataRules): DataFile<QuotaOnlyData> {
        val path = worldDirectory.resolve("${rules.family}.json")
        return DataFile(rules.fileName, read = { read(path, rules) }, write = { write(path, rules, it) })
    }

    /** Lit [path], que le mod crée vide quand il manque : le contenu d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path, rules: QuotaOnlyDataRules): FileReading<QuotaOnlyData> = when (val reading = DataStore.read<QuotaOnlyDocument>(path) { rules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(rules.data(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }

    /** Écrit [data] dans [path], entier et d'un coup. */
    fun write(path: Path, rules: QuotaOnlyDataRules, data: QuotaOnlyData) = DataStore.write(path, QuotaOnlyDocument.of(rules.written(data)))
}

/** Un fichier de données qui ne tient que les plages de quota, tel qu'il s'écrit. Le constructeur sans argument est le fichier vide. */
@Serializable
data class QuotaOnlyDocument(
    @SerialName(DataRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = DataRules.SCHEMA_VERSION,
    @SerialName(DataRules.PLAYERS_KEY) val players: Map<String, QuotaEntryDocument> = emptyMap(),
) {

    companion object {

        fun of(written: WrittenData<WrittenQuotaEntry>): QuotaOnlyDocument = QuotaOnlyDocument(written.schemaVersion, written.players.mapValues { QuotaEntryDocument.of(it.value) })
    }

    fun toWritten(): WrittenData<WrittenQuotaEntry> = WrittenData(schemaVersion, players.mapValues { it.value.toWritten() })
}

/** L'entrée d'un joueur dans un tel fichier : son nom, sa plage. */
@Serializable
data class QuotaEntryDocument(
    @SerialName(DataRules.NAME_KEY) val name: String? = null,
    @SerialName(DataRules.QUOTA_KEY) val quota: QuotaUsageDocument? = null,
) {

    companion object {

        fun of(written: WrittenQuotaEntry): QuotaEntryDocument = QuotaEntryDocument(written.name, written.quota?.let(QuotaUsageDocument::of))
    }

    fun toWritten(): WrittenQuotaEntry = WrittenQuotaEntry(name, quota?.toWritten())
}
