// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le fichier de données en service : le fichier du disque fait foi, et rien ne s'écrit dans un fichier refusé (cahier des charges, chapitre 2.7.3). */
class DataFileTest {

    private val refused = FileReading.Refused(listOf(FileFinding("players[Steve]", 3, CommonMessages.FINDING_NOT_ALLOWED.with("Steve", CommonMessages.ALLOWED_PLAYER_ID.with()))))

    private val written = mutableListOf<String>()

    /** Un fichier dont chaque lecture rend le résultat suivant de [readings], et dont chaque écriture va dans [written]. */
    private fun file(vararg readings: FileReading<String>): DataFile<String> {
        val remaining = readings.toMutableList()
        return DataFile("world/homes.json", read = { remaining.removeFirst() }, write = { written += it })
    }

    @Test
    fun `avant toute lecture, le fichier n'est pas disponible`() {
        val file = file()

        assertFalse(file.isAvailable)
        assertNull(file.content)
        assertTrue(file.isValid)
    }

    @Test
    fun `un fichier valide s'applique et devient disponible`() {
        val file = file(FileReading.Applied("lu"))

        file.load()

        assertEquals("lu", file.content)
        assertTrue(file.isAvailable)
        assertTrue(file.isValid)
        assertNull(file.refusal)
    }

    @Test
    fun `un fichier refusé rend ses données indisponibles`() {
        val file = file(refused)

        file.load()

        assertFalse(file.isAvailable)
        assertFalse(file.isValid)
        assertEquals(refused, file.refusal)
    }

    @Test
    fun `refusé au rechargement, le fichier retire les données en service, parce que le disque fait foi`() {
        val file = file(FileReading.Applied("lu"), refused)

        file.load()
        file.load()

        assertNull(file.content)
        assertFalse(file.isAvailable)
        assertEquals(refused, file.refusal)
    }

    @Test
    fun `corrigé puis rechargé, le fichier remet ses données en service`() {
        val file = file(refused, FileReading.Applied("corrigé"))

        file.load()
        file.load()

        assertEquals("corrigé", file.content)
        assertTrue(file.isValid)
    }

    @Test
    fun `un changement remplace le contenu et s'écrit tout de suite`() {
        val file = file(FileReading.Applied("lu"))
        file.load()

        file.update("changé")

        assertEquals("changé", file.content)
        assertEquals(listOf("changé"), written)
    }

    @Test
    fun `rien ne s'écrit dans un fichier refusé`() {
        val file = file(refused)
        file.load()

        assertThrows(IllegalStateException::class.java) { file.update("changé") }

        assertEquals(emptyList<String>(), written)
    }
}
