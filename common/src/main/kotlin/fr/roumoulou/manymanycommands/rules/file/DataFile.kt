// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

/**
 * Un fichier de données en service (cahier des charges, chapitres 2.7.1 et 2.7.3) : son contenu, que le mod écrit à chaque changement, tout de
 * suite, et le refus de sa dernière lecture.
 *
 * Le fichier du disque fait foi : une lecture appliquée remplace le contenu, une lecture refusée le retire, parce que la famille devient
 * indisponible tant que le fichier n'est pas corrigé puis rechargé. Rien ne s'écrit dans un fichier refusé.
 *
 * @param name le nom du fichier, tel que les messages le donnent
 * @param read la lecture du fichier, qui le crée quand il manque et ne le réécrit jamais
 * @param write l'écriture d'un contenu dans le fichier, entier et d'un coup
 */
class DataFile<CONTENT : Any>(override val name: String, private val read: () -> FileReading<CONTENT>, private val write: (CONTENT) -> Unit) : ModFile {

    /** Le contenu en service, `null` tant que le fichier n'a pas été lu ou qu'il est refusé. */
    var content: CONTENT? = null
        private set

    override var refusal: FileReading.Refused? = null
        private set

    /** La famille peut lire et écrire ses données. */
    val isAvailable: Boolean get() = content != null

    override fun load(): FileReading<CONTENT> {
        val reading = read()
        when (reading) {
            is FileReading.Applied -> {
                content = reading.content
                refusal = null
            }
            is FileReading.Refused -> {
                content = null
                refusal = reading
            }
        }
        return reading
    }

    /** Remplace le contenu par [content] et l'écrit tout de suite. Le fichier doit être disponible : rien ne s'écrit dans un fichier refusé. */
    fun update(content: CONTENT) {
        check(isAvailable) { "Nothing is written to '$name' while it is unavailable" }
        this.content = content
        write(content)
    }
}
