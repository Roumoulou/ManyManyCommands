// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.back

import fr.roumoulou.manymanycommands.files.data.QuotaOnlyDataFiles
import fr.roumoulou.manymanycommands.rules.back.BackData
import fr.roumoulou.manymanycommands.rules.back.BackDataRules
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileReading
import java.nio.file.Path

/** `back.json` dans le dossier du monde : la plage de quota de chaque joueur (`back.md` des specs, chapitre 4). */
object BackDataFile {

    /** Le fichier de données du retour dans [worldDirectory], le dossier du mod dans celui du monde. */
    fun at(worldDirectory: Path): DataFile<BackData> = QuotaOnlyDataFiles.at(worldDirectory, BackDataRules)

    /** Lit [path], que le mod crée vide quand il manque. */
    fun read(path: Path): FileReading<BackData> = QuotaOnlyDataFiles.read(path, BackDataRules)
}
