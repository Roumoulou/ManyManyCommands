// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.wild

import fr.roumoulou.manymanycommands.files.ruleset.RulesDocument
import fr.roumoulou.manymanycommands.files.ruleset.RulesDocumentSerializer
import fr.roumoulou.manymanycommands.files.ruleset.RulesFiles
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.ruleset.FamilyRules
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** `rules\wild.json`, les réglages et les jeux de règles de la téléportation aléatoire. */
object WildRulesFile {

    /** Le fichier de règles de [directory], le dossier du mod dans celui des configurations. */
    fun at(directory: Path): SettingsFile<FamilyRules> = RulesFiles.at(directory, WildRules, ::read)

    /** Lit [path], que le mod crée quand il manque : les règles d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<FamilyRules> = RulesFiles.read<WildRulesDocument>(path, WildRules)
}

/** `rules\wild.json` tel qu'il s'écrit. Le constructeur sans argument porte les valeurs livrées : c'est de lui que naît le fichier qui manque. */
@Serializable(with = WildRulesDocument.Format::class)
class WildRulesDocument(written: WrittenRules = WildRules.shippedFile()) : RulesDocument(written) {

    object Format : RulesDocumentSerializer<WildRulesDocument>(WildRules, ::WildRulesDocument)
}
