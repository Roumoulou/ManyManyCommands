// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files

import fr.moulou.storify.JsonFormat
import fr.moulou.storify.StoreDecodeException
import fr.moulou.storify.StorifyException
import fr.moulou.storify.core.Store
import fr.moulou.storify.core.StoreConfig
import fr.moulou.storify.core.StoreFactory
import fr.moulou.storify.validation.ErrorPath
import fr.moulou.storify.validation.JsonLineLocator
import fr.moulou.storify.validation.ValidationContext
import fr.moulou.storify.validation.ValidationError
import fr.moulou.storify.validation.ValidationException
import fr.moulou.storify.validation.Validator
import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * La lecture d'un fichier de réglages par un store Storify (cahier des charges, chapitres 2.7.1 et 2.7.3).
 *
 * Le store s'ouvre en lecture seule, le temps de la lecture : il crée le fichier quand il manque, avec les valeurs livrées de sa classe, et ne le
 * réécrit jamais. Le JSON est strict. Tout ce qui refuse le fichier se rend en constats, et rien ne remonte au serveur.
 */
object SettingsStore {

    /** Ni sauvegarde automatique ni crochet d'arrêt : le store ne vit que le temps d'une lecture. Ses lignes de journal vont sous le nom du mod. */
    val CONFIG = StoreConfig(withAutoSave = false, readOnly = true, loggerName = MOD_ID)

    /**
     * Lit [path] en [DOCUMENT], la classe sérialisée du fichier, dont le constructeur sans argument donne les valeurs livrées.
     *
     * @param check les valeurs admises : les constats d'un document décodé, sans leur ligne, que Storify retrouve
     */
    inline fun <reified DOCUMENT : Any> read(path: Path, noinline check: (DOCUMENT) -> List<FileFinding>): FileReading<DOCUMENT> =
        read(path, serializer<DOCUMENT>().descriptor, check) { validator -> StoreFactory.createFromConstructor<DOCUMENT>(path.toString(), JsonFormat(), CONFIG, validator) }

    /** La lecture elle-même. [open] ouvre le store : seule une fonction `inline` peut nommer [DOCUMENT] à la fabrique de Storify. */
    @PublishedApi
    internal fun <DOCUMENT : Any> read(path: Path, descriptor: SerialDescriptor, check: (DOCUMENT) -> List<FileFinding>, open: (Validator<DOCUMENT>) -> Store<DOCUMENT>): FileReading<DOCUMENT> = try {
        open(FindingsValidator(check)).use { FileReading.Applied(it.data) }
    } catch (e: ValidationException) {
        FileReading.Refused(e.errors.map(::located))
    } catch (e: StoreDecodeException) {
        FileReading.Refused(diagnose(path, descriptor, e))
    } catch (e: StorifyException) {
        FileReading.Refused(listOf(unreadable(e, line = null)))
    } catch (e: IOException) {
        FileReading.Refused(listOf(unreadable(e, line = null)))
    }

    /** Le constat qu'a rendu [FindingsValidator], avec la ligne que Storify lui a trouvée dans le fichier. */
    private fun located(error: ValidationError): FileFinding = (error.rejectedValue as FileFinding).copy(line = error.line)

    /** Un fichier que le lecteur strict refuse : ses clés inconnues et ses valeurs d'une autre nature, sinon le message du lecteur. */
    private fun diagnose(path: Path, descriptor: SerialDescriptor, failure: StoreDecodeException): List<FileFinding> {
        val text = try {
            path.readText()
        } catch (_: IOException) {
            return listOf(unreadable(failure, failure.line))
        }
        val lines = text.lines()
        return DocumentDiagnosis.findings(text, descriptor).map { it.copy(line = lineOf(lines, it.path)) }.ifEmpty { listOf(unreadable(failure, failure.line)) }
    }

    private fun lineOf(lines: List<String>, path: String?): Int? = path?.let { runCatching { JsonLineLocator.lineOf(lines, ErrorPath.parse(it)) }.getOrNull() }

    /** Le message du lecteur, sa première ligne : la suite recopie le fichier. */
    private fun unreadable(failure: Exception, line: Int?): FileFinding {
        val reason = failure.cause ?: failure
        val message = reason.message?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifEmpty { reason::class.simpleName.orEmpty() }
        return FileFinding(null, line, CommonMessages.FINDING_UNREADABLE.with(message))
    }

    /** Le pont vers la validation de Storify : chaque constat y voyage en erreur, à son chemin, et Storify lui retrouve sa ligne. */
    private class FindingsValidator<DOCUMENT : Any>(private val check: (DOCUMENT) -> List<FileFinding>) : Validator<DOCUMENT> {

        override fun validate(data: DOCUMENT, ctx: ValidationContext) = check(data).forEach { ctx.addError(field = it.path, message = it.text.key, rejectedValue = it) }
    }
}
