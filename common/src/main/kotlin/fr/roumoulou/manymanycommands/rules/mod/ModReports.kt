// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.mod

import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.ModFile
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.settings.PermissionSourceSetting

/** Les réponses de `/manymanycommands` (`manymanycommands.md` des specs, chapitres 1 et 5), bâties sur l'état des fichiers, de réglages et de données. */
object ModReports {

    /** L'état : la version du mod, la source des droits du démarrage, puis chaque fichier, valide ou refusé. */
    fun status(version: String, permissionSource: PermissionSourceSetting, files: List<ModFile>): List<Message> = buildList {
        add(ModMessages.STATUS_VERSION.with(version))
        add(ModMessages.STATUS_SOURCE.with(permissionSource.value))
        files.forEach { add((if (it.isValid) ModMessages.STATUS_FILE_VALID else ModMessages.STATUS_FILE_REFUSED).with(it.name)) }
    }

    /**
     * La réponse du rechargement : une ligne par fichier, appliqué ou refusé avec ses constats, puis les réglages qui attendent le redémarrage.
     *
     * @param files les fichiers, leur lecture faite
     * @param awaitingRestart les clés des réglages de `settings.json` lus au seul démarrage dont la valeur a changé
     * @param awaitingRestartInRules les mêmes réglages dans les fichiers de règles, chacun avec son fichier : cinq d'entre eux portent `enabled`
     */
    fun reload(files: List<ModFile>, awaitingRestart: List<String>, awaitingRestartInRules: List<FileSetting> = emptyList()): List<Message> = buildList {
        files.forEach { file -> if (file.isValid) add(ModMessages.RELOAD_FILE_APPLIED.with(file.name)) else addAll(refusal(file)) }
        awaitingRestart.forEach { add(ModMessages.RELOAD_RESTART_REQUIRED.with(it)) }
        awaitingRestartInRules.forEach { add(ModMessages.RELOAD_RESTART_REQUIRED_IN_FILE.with(it.file, it.key)) }
    }

    /** Le refus d'un fichier : son nom, puis une ligne par constat. Vide pour un fichier valide. */
    fun refusal(file: ModFile): List<Message> {
        val refusal = file.refusal ?: return emptyList()
        return listOf(ModMessages.RELOAD_FILE_REFUSED.with(file.name)) + refusal.findings.map(::finding)
    }

    private fun finding(finding: FileFinding): Message = when {
        finding.path != null && finding.line != null -> ModMessages.RELOAD_FINDING.with(finding.path, finding.line, finding.text)
        finding.path != null -> ModMessages.RELOAD_FINDING_WITHOUT_LINE.with(finding.path, finding.text)
        finding.line != null -> ModMessages.RELOAD_FINDING_WITHOUT_PATH.with(finding.line, finding.text)
        else -> ModMessages.RELOAD_FINDING_ALONE.with(finding.text)
    }
}

/** Un réglage désigné par son fichier et sa clé : `enabled` dans `rules/homes.json`. */
data class FileSetting(val file: String, val key: String)
