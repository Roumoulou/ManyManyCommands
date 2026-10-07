// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

/** Un fichier du mod en service, de réglages ou de données : son nom dans les messages, sa lecture, et le refus de la dernière. */
interface ModFile {

    /** Le nom du fichier, tel que les messages le donnent : son chemin dans le dossier du mod, `rules/homes.json`, ou sous `world/` pour un fichier du monde. */
    val name: String

    /** Le refus de la dernière lecture, `null` quand elle s'est appliquée. */
    val refusal: FileReading.Refused?

    val isValid: Boolean get() = refusal == null

    /** Relit le fichier, qui le crée quand il manque et ne le réécrit jamais. */
    fun load(): FileReading<*>
}
