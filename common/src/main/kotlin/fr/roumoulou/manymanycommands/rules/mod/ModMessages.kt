// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.mod

import fr.roumoulou.manymanycommands.rules.message.MessageFamily

/** Les messages de `manymanycommands.md` des specs (chapitre 5). */
object ModMessages : MessageFamily("mod") {

    // L'état : la version, la source des droits, puis une ligne par fichier.
    val STATUS_VERSION = display("status.version")
    val STATUS_SOURCE = display("status.source")
    val STATUS_FILE_VALID = display("status.file_valid")
    val STATUS_FILE_REFUSED = display("status.file_refused")

    val RELOAD_FILE_APPLIED = success("reload.file_applied")

    // Le fichier refusé, puis une ligne par constat : avec son chemin et sa ligne, ou ce qui en est connu.
    val RELOAD_FILE_REFUSED = refusal("reload.file_refused")
    val RELOAD_FINDING = refusal("reload.finding")
    val RELOAD_FINDING_WITHOUT_LINE = refusal("reload.finding.without_line")
    val RELOAD_FINDING_WITHOUT_PATH = refusal("reload.finding.without_path")
    val RELOAD_FINDING_ALONE = refusal("reload.finding.alone")

    val RELOAD_RESTART_REQUIRED = display("reload.restart_required")

    val STARTUP_INVALID_SETTINGS = refusal("startup.invalid_settings")
}
