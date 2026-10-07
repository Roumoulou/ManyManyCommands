// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.warps

import fr.roumoulou.manymanycommands.files.DataStore
import fr.roumoulou.manymanycommands.files.data.QuotaEntryDocument
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenPosition
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.warps.WarpsData
import fr.roumoulou.manymanycommands.rules.warps.WarpsDataRules
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import fr.roumoulou.manymanycommands.rules.warps.WrittenWarp
import fr.roumoulou.manymanycommands.rules.warps.WrittenWarpsData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `warps.json` dans le dossier du monde : les warps du serveur et la plage de quota de chaque joueur (cahier des charges, chapitre 2.7.5). */
object WarpsDataFile {

    /** Le fichier de données des warps dans [worldDirectory], le dossier du mod dans celui du monde. */
    fun at(worldDirectory: Path): DataFile<WarpsData> {
        val path = worldDirectory.resolve("${WarpsRules.family}.json")
        return DataFile(WarpsDataRules.fileName, read = { read(path) }, write = { write(path, it) })
    }

    /** Lit [path], que le mod crée vide quand il manque : le contenu d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<WarpsData> = when (val reading = DataStore.read<WarpsDocument>(path) { WarpsDataRules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(WarpsDataRules.data(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }

    /** Écrit [data] dans [path], entier et d'un coup. */
    fun write(path: Path, data: WarpsData) = DataStore.write(path, WarpsDocument.of(WarpsDataRules.written(data)))
}

/** `warps.json` tel qu'il s'écrit. Le constructeur sans argument est le fichier vide : c'est de lui que naît le fichier qui manque. */
@Serializable
data class WarpsDocument(
    @SerialName(DataRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = DataRules.SCHEMA_VERSION,
    @SerialName(WarpsDataRules.WARPS_KEY) val warps: Map<String, WarpDocument> = emptyMap(),
    @SerialName(DataRules.PLAYERS_KEY) val players: Map<String, QuotaEntryDocument> = emptyMap(),
) {

    companion object {

        fun of(written: WrittenWarpsData): WarpsDocument = WarpsDocument(written.schemaVersion, written.warps.mapValues { WarpDocument.of(it.value) }, written.players.mapValues { QuotaEntryDocument.of(it.value) })
    }

    fun toWritten(): WrittenWarpsData = WrittenWarpsData(schemaVersion, warps.mapValues { it.value.toWritten() }, players.mapValues { it.value.toWritten() })
}

/** Un warp dans `warps.json` : sa position, et sa clé d'accès quand il est réservé. */
@Serializable
data class WarpDocument(
    @SerialName(WrittenPosition.DIMENSION_KEY) val dimension: String? = null,
    @SerialName(WrittenPosition.X_KEY) val x: Double? = null,
    @SerialName(WrittenPosition.Y_KEY) val y: Double? = null,
    @SerialName(WrittenPosition.Z_KEY) val z: Double? = null,
    @SerialName(WrittenPosition.YAW_KEY) val yaw: Float? = null,
    @SerialName(WrittenPosition.PITCH_KEY) val pitch: Float? = null,
    @SerialName(WrittenWarp.KEY_KEY) val key: String? = null,
) {

    companion object {

        fun of(written: WrittenWarp): WarpDocument {
            val position = written.position
            return WarpDocument(position.dimension, position.x, position.y, position.z, position.yaw, position.pitch, written.key)
        }
    }

    fun toWritten(): WrittenWarp = WrittenWarp(WrittenPosition(dimension, x, y, z, yaw, pitch), key)
}
