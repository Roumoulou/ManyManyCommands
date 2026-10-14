// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.players

import fr.roumoulou.manymanycommands.files.DataStore
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.players.PlayerRegistry
import fr.roumoulou.manymanycommands.rules.players.PlayerRegistryRules
import fr.roumoulou.manymanycommands.rules.players.WrittenPlayerRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `data\players.json` dans le dossier du mod : le registre des joueurs (cahier des charges, chapitres 2.7.2 et 2.7.5). */
object PlayersDataFile {

    /** Le registre de [configDirectory], le dossier du mod dans celui des configurations. */
    fun at(configDirectory: Path): DataFile<PlayerRegistry> {
        val path = configDirectory.resolve(DataRules.SERVER_DATA_DIRECTORY).resolve("players.json")
        return DataFile(PlayerRegistryRules.fileName, read = { read(path) }, write = { write(path, it) })
    }

    /** Lit [path], que le mod crée vide quand il manque : le registre d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<PlayerRegistry> = when (val reading = DataStore.read<PlayersDocument>(path) { PlayerRegistryRules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(PlayerRegistryRules.registry(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }

    /** Écrit [registry] dans [path], entier et d'un coup. */
    fun write(path: Path, registry: PlayerRegistry) = DataStore.write(path, PlayersDocument.of(PlayerRegistryRules.written(registry)))
}

/** `data\players.json` tel qu'il s'écrit. Le constructeur sans argument est le fichier vide : c'est de lui que naît le fichier qui manque. */
@Serializable
data class PlayersDocument(
    @SerialName(DataRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = DataRules.SCHEMA_VERSION,
    @SerialName(DataRules.PLAYERS_KEY) val players: Map<String, PlayerRecordDocument> = emptyMap(),
) {

    companion object {

        fun of(written: WrittenData<WrittenPlayerRecord>): PlayersDocument = PlayersDocument(written.schemaVersion, written.players.mapValues { PlayerRecordDocument.of(it.value) })
    }

    fun toWritten(): WrittenData<WrittenPlayerRecord> = WrittenData(schemaVersion, players.mapValues { it.value.toWritten() })
}

/** L'entrée d'un joueur dans `data\players.json`, dans l'ordre où le fichier l'écrit. */
@Serializable
data class PlayerRecordDocument(
    @SerialName(DataRules.NAME_KEY) val name: String? = null,
    @SerialName(WrittenPlayerRecord.LAST_SEEN_KEY) val lastSeen: String? = null,
) {

    companion object {

        fun of(written: WrittenPlayerRecord): PlayerRecordDocument = PlayerRecordDocument(written.name, written.lastSeen)
    }

    fun toWritten(): WrittenPlayerRecord = WrittenPlayerRecord(name, lastSeen)
}
