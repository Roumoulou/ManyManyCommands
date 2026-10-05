// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.settings

import fr.roumoulou.manymanycommands.files.SettingsStore
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.file.SettingsFile
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettingsRules
import java.nio.file.Path

/** `settings.json`, les réglages qui valent pour tout le mod. */
object GlobalSettingsFile {

    /** Le fichier de réglages de [directory], le dossier du mod dans celui des configurations. */
    fun at(directory: Path): SettingsFile<GlobalSettings> {
        val path = directory.resolve(GlobalSettings.FILE_NAME)
        return SettingsFile(GlobalSettings.FILE_NAME, GlobalSettings()) { read(path) }
    }

    /** Lit [path], que le mod crée quand il manque : les réglages d'un fichier valide, ou les constats qui le refusent. */
    fun read(path: Path): FileReading<GlobalSettings> {
        val reading = SettingsStore.read<GlobalSettingsDocument>(path) { GlobalSettingsRules.findings(it.schemaVersion, it.language, it.permissionSource) }
        return when (reading) {
            is FileReading.Applied -> FileReading.Applied(reading.content.toSettings())
            is FileReading.Refused -> reading
        }
    }
}
