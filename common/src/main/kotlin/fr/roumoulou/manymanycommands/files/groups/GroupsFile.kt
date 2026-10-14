// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.groups

import fr.roumoulou.manymanycommands.files.SettingsStore
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.groups.Groups
import fr.roumoulou.manymanycommands.rules.groups.GroupsRules
import fr.roumoulou.manymanycommands.rules.groups.WrittenGroup
import fr.roumoulou.manymanycommands.rules.groups.WrittenGroups
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `groups.json`, la définition des groupes (`groups.md` des specs, chapitre 4), un fichier de réglages que l'administrateur écrit. */
object GroupsFile {

    /** Le fichier des groupes de [directory], le dossier du mod dans celui des configurations. */
    fun at(directory: Path): SettingsFile<Groups> {
        val path = directory.resolve(GroupsRules.FILE_NAME)
        return SettingsFile(GroupsRules.FILE_NAME, Groups.SHIPPED) { read(path) }
    }

    /** Lit [path], que le mod crée quand il manque, avec le groupe `default` vide : les groupes d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<Groups> = when (val reading = SettingsStore.read<GroupsDocument>(path) { GroupsRules.findings(it.toWritten()) }) {
        is FileReading.Applied -> FileReading.Applied(GroupsRules.groups(reading.content.toWritten()))
        is FileReading.Refused -> reading
    }
}

/** `groups.json` tel qu'il s'écrit. Le constructeur sans argument est le fichier livré : le groupe `default`, vide. */
@Serializable
data class GroupsDocument(
    @SerialName(GroupsRules.SCHEMA_VERSION_KEY) val schemaVersion: Int = GroupsRules.SCHEMA_VERSION,
    @SerialName(GroupsRules.GROUPS_KEY) val groups: Map<String, GroupDocument> = mapOf(Groups.DEFAULT_GROUP to GroupDocument()),
) {

    fun toWritten(): WrittenGroups = WrittenGroups(schemaVersion, groups.mapValues { it.value.toWritten() })
}

/** Un groupe tel que `groups.json` l'écrit : ses trois clés, chacune vide quand elle manque. */
@Serializable
data class GroupDocument(
    @SerialName(GroupsRules.RULES_KEY) val rules: Map<String, String> = emptyMap(),
    @SerialName(GroupsRules.ALLOW_KEY) val allow: List<String> = emptyList(),
    @SerialName(GroupsRules.DENY_KEY) val deny: List<String> = emptyList(),
) {

    fun toWritten(): WrittenGroup = WrittenGroup(rules, allow, deny)
}
