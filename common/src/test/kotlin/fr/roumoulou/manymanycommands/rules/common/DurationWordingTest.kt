// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** L'écriture d'une durée (cahier des charges, chapitre 2.6.6). */
class DurationWordingTest {

    @Test
    fun `sous la minute, une durée s'écrit en secondes`() {
        assertEquals(listOf(DurationPart(DurationUnit.SECONDS, 45)), DurationWording.parts(45))
        assertEquals(listOf(DurationPart(DurationUnit.SECONDS, 59)), DurationWording.parts(59))
        assertEquals(listOf(DurationPart(DurationUnit.SECONDS, 0)), DurationWording.parts(0))
    }

    @Test
    fun `à partir de la minute, elle s'écrit par ses deux plus grandes unités`() {
        assertEquals(listOf(DurationPart(DurationUnit.MINUTES, 3), DurationPart(DurationUnit.SECONDS, 20)), DurationWording.parts(200))
        assertEquals(listOf(DurationPart(DurationUnit.HOURS, 1), DurationPart(DurationUnit.MINUTES, 30)), DurationWording.parts(5_400))
        assertEquals(listOf(DurationPart(DurationUnit.DAYS, 1), DurationPart(DurationUnit.HOURS, 1)), DurationWording.parts(90_061))
    }

    @Test
    fun `la seconde unité ne s'écrit pas quand elle vaut zéro`() {
        assertEquals(listOf(DurationPart(DurationUnit.MINUTES, 1)), DurationWording.parts(60))
        assertEquals(listOf(DurationPart(DurationUnit.HOURS, 2)), DurationWording.parts(7_200))
        assertEquals(listOf(DurationPart(DurationUnit.DAYS, 3)), DurationWording.parts(259_200))
    }

    @Test
    fun `ce qui reste sous la seconde unité est tronqué`() {
        assertEquals(listOf(DurationPart(DurationUnit.HOURS, 2)), DurationWording.parts(7_230))
        assertEquals(listOf(DurationPart(DurationUnit.DAYS, 1)), DurationWording.parts(86_400L + 59 * 60 + 59))
    }

    @Test
    fun `chaque unité s'accorde avec son nombre`() {
        assertEquals(CommonMessages.DURATION_SECONDS_ONE.with(1L), DurationWording.text(1))
        assertEquals(CommonMessages.DURATION_SECONDS_MANY.with(45L), DurationWording.text(45))
        assertEquals(CommonMessages.DURATION_HOURS_MANY.with(2L), DurationWording.text(7_200))
        assertEquals(CommonMessages.DURATION_DAYS_ONE.with(1L), DurationWording.text(86_400))
    }

    @Test
    fun `deux unités voyagent dans un seul texte, chacune avec sa clé`() {
        val expected = CommonMessages.DURATION_PAIR.with(CommonMessages.DURATION_MINUTES_MANY.with(3L), CommonMessages.DURATION_SECONDS_MANY.with(20L))

        assertEquals(expected, DurationWording.text(200))
    }

    @Test
    fun `une durée négative est une faute du code`() {
        assertThrows(IllegalArgumentException::class.java) { DurationWording.parts(-1) }
    }
}
