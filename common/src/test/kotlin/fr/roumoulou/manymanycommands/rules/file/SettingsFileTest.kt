// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le fichier de réglages invalide, au démarrage et au rechargement (cahier des charges, chapitre 2.7.3). */
class SettingsFileTest {

    private val refused = FileReading.Refused(listOf(FileFinding("language", 3, CommonMessages.FINDING_UNKNOWN_KEY.with())))

    /** Un fichier dont chaque lecture rend le résultat suivant de [readings]. */
    private fun fileReading(vararg readings: FileReading<String>): SettingsFile<String> {
        val remaining = readings.toMutableList()
        return SettingsFile("settings.json", shipped = "livré") { remaining.removeFirst() }
    }

    @Test
    fun `avant toute lecture, les valeurs livrées sont en vigueur`() {
        val file = fileReading()

        assertEquals("livré", file.inForce)
        assertTrue(file.isValid)
        assertFalse(file.isShippedInPlace)
    }

    @Test
    fun `un fichier valide s'applique`() {
        val file = fileReading(FileReading.Applied("lu"))

        file.load()

        assertEquals("lu", file.inForce)
        assertTrue(file.isValid)
        assertNull(file.refusal)
    }

    @Test
    fun `invalide au démarrage, les valeurs livrées prennent sa place`() {
        val file = fileReading(refused)

        file.load()

        assertEquals("livré", file.inForce)
        assertFalse(file.isValid)
        assertEquals(refused, file.refusal)
        assertTrue(file.isShippedInPlace)
    }

    @Test
    fun `invalide au rechargement, les valeurs en vigueur restent`() {
        val file = fileReading(FileReading.Applied("lu"), refused)

        file.load()
        file.load()

        assertEquals("lu", file.inForce)
        assertFalse(file.isValid)
        assertFalse(file.isShippedInPlace, "The shipped values are not in place: the file applied at startup")
    }

    @Test
    fun `corrigé puis rechargé, un fichier refusé au démarrage s'applique et les valeurs livrées s'effacent`() {
        val file = fileReading(refused, FileReading.Applied("corrigé"))

        file.load()
        file.load()

        assertEquals("corrigé", file.inForce)
        assertTrue(file.isValid)
        assertFalse(file.isShippedInPlace)
    }

    @Test
    fun `refusé de nouveau après avoir été corrigé, il garde les valeurs corrigées`() {
        val file = fileReading(refused, FileReading.Applied("corrigé"), refused)

        repeat(3) { file.load() }

        assertEquals("corrigé", file.inForce)
        assertFalse(file.isValid)
        assertFalse(file.isShippedInPlace)
    }
}
