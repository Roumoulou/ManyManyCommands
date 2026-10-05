// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.homes

import fr.roumoulou.manymanycommands.files.ruleset.RulesDocument
import fr.roumoulou.manymanycommands.files.ruleset.RulesDocumentSerializer
import fr.roumoulou.manymanycommands.files.ruleset.RulesFiles
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `rules\homes.json`, les réglages et les jeux de règles des homes. */
object HomesRulesFile {

    /** Le fichier de règles de [directory], le dossier du mod dans celui des configurations. */
    fun at(directory: Path): SettingsFile<FamilyRules> = RulesFiles.at(directory, HomesRules, ::read)

    /** Lit [path], que le mod crée quand il manque : les règles d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<FamilyRules> = RulesFiles.read<HomesRulesDocument>(path, HomesRules)
}

/** `rules\homes.json` tel qu'il s'écrit. Le constructeur sans argument porte les valeurs livrées : c'est de lui que naît le fichier qui manque. */
@Serializable(with = HomesRulesDocument.Format::class)
class HomesRulesDocument(written: WrittenRules = HomesRules.shippedFile()) : RulesDocument(written) {

    object Format : RulesDocumentSerializer<HomesRulesDocument>(HomesRules, ::HomesRulesDocument)
}
