// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.PlayerIds
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/**
 * Un fichier de données tel qu'il s'écrit (cahier des charges, chapitre 2.7.5) : sa version de schéma, et l'entrée de chaque joueur sous son
 * UUID, tel que le fichier l'écrit.
 *
 * @param ENTRY l'entrée d'un joueur telle que la famille l'écrit
 * @property schemaVersion la version de schéma, celle du mod quand le fichier ne l'écrit pas
 */
data class WrittenData<ENTRY>(val schemaVersion: Int = DataRules.SCHEMA_VERSION, val players: Map<String, ENTRY> = emptyMap())

/**
 * Ce qu'un fichier de données a de commun à toutes les familles (cahier des charges, chapitres 2.7.2, 2.7.3 et 2.7.5) : sa version de schéma,
 * l'UUID de chaque joueur, et sous lui une entrée que la famille juge.
 */
object DataRules {

    /** La version de schéma que ce mod écrit et sait lire : un fichier qui en annonce une plus récente est invalide. */
    const val SCHEMA_VERSION = 1

    const val SCHEMA_VERSION_KEY = "schema-version"
    const val PLAYERS_KEY = "players"
    const val NAME_KEY = "name"
    const val QUOTA_KEY = "quota"

    /** Le nom du fichier de données de [family] dans les messages : sous `world/`, quel que soit le nom du monde (`manymanycommands.md` des specs, chapitre 5). */
    fun fileName(family: String): String = "world/$family.json"

    /**
     * Les constats d'un fichier lu, sans leur ligne. Vide quand le fichier est valide.
     *
     * @param entryFindings les constats de l'entrée d'un joueur, au chemin de l'entrée
     */
    fun <ENTRY> findings(written: WrittenData<ENTRY>, entryFindings: (path: String, entry: ENTRY) -> List<FileFinding>): List<FileFinding> = buildList {
        if (written.schemaVersion > SCHEMA_VERSION) add(FileFinding(SCHEMA_VERSION_KEY, null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(written.schemaVersion, SCHEMA_VERSION)))
        written.players.forEach { (id, entry) ->
            val path = FindingPath.entry(PLAYERS_KEY, id)
            if (PlayerIds.parse(id) == null) add(notAllowed(path, id, CommonMessages.ALLOWED_PLAYER_ID.with()))
            addAll(entryFindings(path, entry))
        }
    }

    /**
     * Le contenu d'un fichier que [findings] juge valide : l'entrée de chaque joueur, par son UUID.
     *
     * @param entryValue l'entrée d'un joueur telle que la famille la tient
     */
    fun <ENTRY, HOLDINGS : Holdings> players(written: WrittenData<ENTRY>, entryValue: (ENTRY) -> PlayerEntry<HOLDINGS>): FamilyData<HOLDINGS> =
        FamilyData(written.players.entries.associate { (id, entry) -> (PlayerIds.parse(id) ?: error("Unvalidated player id '$id'")) to entryValue(entry) })

    /**
     * Ce qu'un fichier écrit pour [data] : l'entrée de chaque joueur qui tient autre chose que son nom, par son UUID en texte.
     *
     * @param writtenEntry l'entrée d'un joueur telle que la famille l'écrit
     */
    fun <ENTRY, HOLDINGS : Holdings> written(data: FamilyData<HOLDINGS>, writtenEntry: (PlayerEntry<HOLDINGS>) -> ENTRY): WrittenData<ENTRY> =
        WrittenData(SCHEMA_VERSION, data.players.filterValues { !it.isEmpty }.entries.associate { (id, entry) -> id.toString() to writtenEntry(entry) })
}
