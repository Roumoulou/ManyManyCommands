// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.settings

import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettings
import fr.roumoulou.manymanycommands.rules.settings.GlobalSettingsRules
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val SHIPPED = GlobalSettings()

/**
 * `settings.json` tel qu'il s'écrit. Les valeurs à choix y restent du texte : c'est [GlobalSettingsRules] qui juge leurs valeurs admises, pour que
 * le constat donne le chemin et la ligne. Les défauts sont les valeurs livrées : une clé absente prend la sienne, et le fichier créé les porte.
 */
@Serializable
data class GlobalSettingsDocument(
    @SerialName(GlobalSettings.SCHEMA_VERSION_KEY) val schemaVersion: Int = GlobalSettings.SCHEMA_VERSION,
    @SerialName(GlobalSettings.LANGUAGE_KEY) val language: String = SHIPPED.language.value,
    @SerialName(GlobalSettings.PERMISSION_SOURCE_KEY) val permissionSource: String = SHIPPED.permissionSource.value,
    @SerialName(GlobalSettings.MMC_ALIAS_KEY) val mmcAlias: Boolean = SHIPPED.mmcAlias,
) {

    /** Les réglages d'un document que [GlobalSettingsRules] a jugé valide. */
    fun toSettings(): GlobalSettings = GlobalSettings(
        language = LanguageSetting.of(language) ?: error("Unvalidated language '$language'"),
        permissionSource = PermissionSourceSetting.of(permissionSource) ?: error("Unvalidated permission source '$permissionSource'"),
        mmcAlias = mmcAlias,
    )
}
