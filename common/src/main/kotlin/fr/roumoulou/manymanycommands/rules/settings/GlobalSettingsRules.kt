// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.settings

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting

/** Les valeurs admises de `settings.json`, jugées sur les valeurs telles que le fichier les écrit. */
object GlobalSettingsRules {

    /** Un constat par valeur fautive, à la clé du réglage, sans sa ligne. Vide quand tout est admis. */
    fun findings(schemaVersion: Int, language: String, permissionSource: String): List<FileFinding> = buildList {
        if (schemaVersion > GlobalSettings.SCHEMA_VERSION) {
            add(FileFinding(GlobalSettings.SCHEMA_VERSION_KEY, null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(schemaVersion, GlobalSettings.SCHEMA_VERSION)))
        }
        if (LanguageSetting.of(language) == null) add(notAllowed(GlobalSettings.LANGUAGE_KEY, language, LanguageSetting.entries.map { it.value }))
        if (PermissionSourceSetting.of(permissionSource) == null) add(notAllowed(GlobalSettings.PERMISSION_SOURCE_KEY, permissionSource, PermissionSourceSetting.entries.map { it.value }))
    }

    private fun notAllowed(key: String, value: String, allowed: List<String>): FileFinding = FileFinding(key, null, CommonMessages.FINDING_NOT_ALLOWED.with(value, allowed.joinToString(", ")))
}
