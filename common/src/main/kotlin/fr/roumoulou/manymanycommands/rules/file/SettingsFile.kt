// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

/**
 * Un fichier de réglages en service (cahier des charges, chapitre 2.7.3) : ses valeurs en vigueur, et le refus de sa dernière lecture.
 *
 * Une lecture refusée ne s'applique jamais. Les valeurs en vigueur partent des valeurs livrées : un fichier refusé au démarrage les laisse en
 * place, un fichier refusé au rechargement laisse celles d'avant.
 *
 * @param name le nom du fichier, tel que les messages le donnent
 * @param shipped les valeurs livrées
 * @param read la lecture du fichier, qui le crée quand il manque et ne le réécrit jamais
 */
class SettingsFile<CONTENT>(override val name: String, shipped: CONTENT, private val read: () -> FileReading<CONTENT>) : ModFile {

    var inForce: CONTENT = shipped
        private set

    override var refusal: FileReading.Refused? = null
        private set

    private var everApplied = false

    /** Les valeurs livrées tiennent la place du fichier : il a été refusé au démarrage, et aucune lecture ne s'est appliquée depuis. */
    val isShippedInPlace: Boolean get() = !everApplied && !isValid

    override fun load(): FileReading<CONTENT> {
        val reading = read()
        when (reading) {
            is FileReading.Applied -> {
                inForce = reading.content
                refusal = null
                everApplied = true
            }
            is FileReading.Refused -> refusal = reading
        }
        return reading
    }
}
