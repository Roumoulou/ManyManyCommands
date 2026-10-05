// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.ruleset

import fr.roumoulou.manymanycommands.files.SettingsStore
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import java.nio.file.Path

/** La lecture d'un fichier de règles (cahier des charges, chapitres 2.7.1 et 2.7.3), commune aux familles : chacune y passe sa table et sa classe. */
object RulesFiles {

    /**
     * Le fichier de règles de [table] dans [directory], le dossier du mod dans celui des configurations.
     *
     * @param read la lecture de la famille, qui nomme sa classe sérialisée
     */
    fun at(directory: Path, table: RulesTable, read: (Path) -> FileReading<FamilyRules>): SettingsFile<FamilyRules> {
        val path = directory.resolve(table.fileName)
        return SettingsFile(table.fileName, table.shipped) { read(path) }
    }

    /** Lit [path] en [DOCUMENT], que le mod crée quand il manque : les règles d'un fichier valide, ou les constats qui le refusent. */
    inline fun <reified DOCUMENT : RulesDocument> read(path: Path, table: RulesTable): FileReading<FamilyRules> =
        when (val reading = SettingsStore.read<DOCUMENT>(path) { table.findings(it.written) }) {
            is FileReading.Applied -> FileReading.Applied(table.values(reading.content.written))
            is FileReading.Refused -> reading
        }
}
