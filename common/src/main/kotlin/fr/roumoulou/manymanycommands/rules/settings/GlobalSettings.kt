// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.settings

import fr.roumoulou.manymanycommands.rules.message.LanguageSetting

/**
 * Les réglages qui valent pour tout le mod, ceux de `settings.json` (`manymanycommands.md` des specs, chapitre 4). Les défauts du constructeur
 * sont les valeurs livrées.
 */
data class GlobalSettings(
    val language: LanguageSetting = LanguageSetting.AUTO,
    val permissionSource: PermissionSourceSetting = PermissionSourceSetting.INTERNAL,
    val mmcAlias: Boolean = true,
) {

    companion object {

        const val FILE_NAME = "settings.json"

        /** La version de schéma que ce mod écrit et sait lire : un fichier qui en annonce une plus récente est invalide. */
        const val SCHEMA_VERSION = 1

        const val SCHEMA_VERSION_KEY = "schema-version"
        const val LANGUAGE_KEY = "language"
        const val PERMISSION_SOURCE_KEY = "permission-source"
        const val MMC_ALIAS_KEY = "mmc-alias"
    }

    /**
     * Les clés des réglages qui ne prennent effet qu'au redémarrage du serveur et dont la valeur n'est plus celle du démarrage
     * (`manymanycommands.md` des specs, chapitre 2).
     */
    fun awaitingRestart(startup: GlobalSettings): List<String> = buildList {
        if (permissionSource != startup.permissionSource) add(PERMISSION_SOURCE_KEY)
        if (mmcAlias != startup.mmcAlias) add(MMC_ALIAS_KEY)
    }
}
