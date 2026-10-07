// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files

import fr.moulou.storify.JsonFormat
import fr.moulou.storify.utils.AtomicFiles
import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Path

/**
 * La lecture et l'écriture d'un fichier de données (cahier des charges, chapitres 2.7.1, 2.7.3 et 2.7.5).
 *
 * Un fichier de données se lit comme un fichier de réglages, par [SettingsStore] : créé quand il manque, JSON strict, tout ce qui le refuse rendu
 * en constats, et un fichier fautif jamais réécrit. Il s'écrit entier et d'un coup, par l'écrivain atomique de Storify : le fichier du disque est
 * toujours une version complète, et le contenu en mémoire reste celui des règles, que l'écriture ait abouti ou non.
 */
object DataStore {

    @PublishedApi
    internal val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    /** Le format de l'écriture : lisible, et sans les clés dont la valeur manque, parce qu'une clé absente a un sens dans un fichier de données. */
    @PublishedApi
    internal val WRITER = JsonFormat(Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false })

    /** Lit [path] en [DOCUMENT], la classe sérialisée du fichier, dont le constructeur sans argument donne le fichier vide. [check] rend les constats d'un document décodé. */
    inline fun <reified DOCUMENT : Any> read(path: Path, noinline check: (DOCUMENT) -> List<FileFinding>): FileReading<DOCUMENT> = SettingsStore.read(path, check)

    /** Écrit [document] dans [path]. Une écriture qui échoue se dit en erreur au journal, et rien ne remonte au serveur : le changement suivant réessaie. */
    inline fun <reified DOCUMENT : Any> write(path: Path, document: DOCUMENT) = write(path, serializer<DOCUMENT>(), document)

    @PublishedApi
    internal fun <DOCUMENT : Any> write(path: Path, serializer: SerializationStrategy<DOCUMENT>, document: DOCUMENT) {
        try {
            AtomicFiles.write(path, LOGGER) { temp -> WRITER.encodeToPath(serializer, document, temp) }
        } catch (e: IOException) {
            LOGGER.error("[ManyManyCommands] Cannot write {}", path, e)
        } catch (e: UncheckedIOException) {
            LOGGER.error("[ManyManyCommands] Cannot write {}", path, e)
        }
    }
}
