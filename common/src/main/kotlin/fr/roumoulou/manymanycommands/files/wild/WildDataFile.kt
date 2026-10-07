// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.wild

import fr.roumoulou.manymanycommands.files.data.QuotaOnlyDataFiles
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.wild.WildData
import fr.roumoulou.manymanycommands.rules.wild.WildDataRules
import java.nio.file.Path

/** `wild.json` dans le dossier du monde : la plage de quota de chaque joueur (`wild.md` des specs, chapitre 6). */
object WildDataFile {

    /** Le fichier de données de la téléportation aléatoire dans [worldDirectory], le dossier du mod dans celui du monde. */
    fun at(worldDirectory: Path): DataFile<WildData> = QuotaOnlyDataFiles.at(worldDirectory, WildDataRules)

    /** Lit [path], que le mod crée vide quand il manque. */
    fun read(path: Path): FileReading<WildData> = QuotaOnlyDataFiles.read(path, WildDataRules)
}
