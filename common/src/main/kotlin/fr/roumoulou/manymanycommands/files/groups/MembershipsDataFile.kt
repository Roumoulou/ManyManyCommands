// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.groups

import fr.roumoulou.manymanycommands.files.DataStore
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.groups.Memberships
import fr.roumoulou.manymanycommands.rules.groups.MembershipsRules
import fr.roumoulou.manymanycommands.rules.groups.WrittenMembership
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `data\memberships.json` dans le dossier du mod : l'inscription et les permissions propres de chaque joueur (`groups.md` des specs, chapitre 4). */
object MembershipsDataFile {

    /** Les inscriptions de [configDirectory], le dossier du mod dans celui des configurations. */
    fun at(configDirectory: Path): DataFile<Memberships> {
        val path = configDirectory.resolve(DataRules.SERVER_DATA_DIRECTORY).resolve("memberships.json")
        return DataFile(MembershipsRules.fileName, read = { read(path) }, write = { write(path, it) })
    }

    /** Lit [path], que le mod crée vide quand il manque : le contenu d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<Memberships> = when (val reading = DataStore.read<MembershipsDocument>(path) { MembershipsRules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(MembershipsRules.data(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }

    /** Écrit [data] dans [path], entier et d'un coup. */
    fun write(path: Path, data: Memberships) = DataStore.write(path, MembershipsDocument.of(MembershipsRules.written(data)))
}

/** `data\memberships.json` tel qu'il s'écrit. Le constructeur sans argument est le fichier vide : c'est de lui que naît le fichier qui manque. */
@Serializable
data class MembershipsDocument(
    @SerialName(DataRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = DataRules.SCHEMA_VERSION,
    @SerialName(DataRules.PLAYERS_KEY) val players: Map<String, MembershipDocument> = emptyMap(),
) {

    companion object {

        fun of(written: WrittenData<WrittenMembership>): MembershipsDocument = MembershipsDocument(written.schemaVersion, written.players.mapValues { MembershipDocument.of(it.value) })
    }

    fun toWritten(): WrittenData<WrittenMembership> = WrittenData(schemaVersion, players.mapValues { it.value.toWritten() })
}

/** L'entrée d'un joueur dans `data\memberships.json`, dans l'ordre où le fichier l'écrit. */
@Serializable
data class MembershipDocument(
    @SerialName(DataRules.NAME_KEY) val name: String? = null,
    @SerialName(WrittenMembership.GROUP_KEY) val group: String? = null,
    @SerialName(WrittenMembership.ALLOW_KEY) val allow: List<String>? = null,
    @SerialName(WrittenMembership.DENY_KEY) val deny: List<String>? = null,
) {

    companion object {

        fun of(written: WrittenMembership): MembershipDocument = MembershipDocument(written.name, written.group, written.allow, written.deny)
    }

    fun toWritten(): WrittenMembership = WrittenMembership(name, group, allow, deny)
}
