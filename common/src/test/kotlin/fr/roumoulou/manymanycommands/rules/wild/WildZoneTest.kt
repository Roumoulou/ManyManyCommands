// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** La zone du tirage (`wild.md` des specs, chapitre 2) : la couronne, ses bandes, la bordure qui la rogne ou l'exclut, le tirage qui y reste. */
class WildZoneTest {

    /** Une couronne de 4 à 9 blocs autour de (10, -20) : 19 × 19 colonnes moins le carré intérieur de 7 × 7, soit 312 colonnes. */
    private val zone = WildZone(centerX = 10, centerZ = -20, minDistance = 4, maxDistance = 9)

    private val wide = Bounds(-1_000, 1_000, -1_000, 1_000)

    private fun draws(zone: WildZone, border: Bounds, count: Int, seed: Int): List<Column> {
        val random = Random(seed)
        return List(count) { zone.draw(border, random) ?: throw AssertionError("The border lets columns, a draw must give one") }
    }

    @Test
    fun `la couronne garde les colonnes dont le plus grand écart au centre va de la distance minimale à la maximale`() {
        assertTrue(Column(14, -20) in zone, "4 blocs à l'est")
        assertTrue(Column(10, -24) in zone, "4 blocs au sud")
        assertTrue(Column(1, -11) in zone, "9 blocs à l'ouest et au nord, le coin")
        assertFalse(Column(13, -17) in zone, "3 blocs : trop près")
        assertFalse(Column(20, -20) in zone, "10 blocs : trop loin")
        assertFalse(Column(10, -20) in zone, "le centre")
    }

    @Test
    fun `les bandes couvrent la couronne entière sans se chevaucher, et un tirage y tombe toujours`() {
        assertEquals(312L, zone.within(wide).sumOf { it.area })
        draws(zone, wide, 1_000, seed = 42).forEach { assertTrue(it in zone, "$it must be in the ring") }
    }

    @Test
    fun `la bordure rogne la zone, et un tirage reste dans la bordure comme dans la couronne`() {
        val quarter = Bounds(10, 1_000, -1_000, -20)

        assertTrue(zone.within(quarter).sumOf { it.area } in 1L..311L)
        draws(zone, quarter, 1_000, seed = 7).forEach { assertTrue(it in zone && it in quarter, "$it must be in the ring and in the border") }
        assertEquals(1L, zone.within(Bounds(19, 19, -20, -20)).sumOf { it.area }, "A border reduced to a column on the edge keeps it")
    }

    @Test
    fun `la bordure qui exclut toute la zone ne laisse rien, et rien ne se tire`() {
        val apart = Bounds(100, 200, 100, 200)
        val inside = Bounds(7, 13, -23, -17)

        assertEquals(emptyList<Bounds>(), zone.within(apart), "A border apart from the ring")
        assertEquals(emptyList<Bounds>(), zone.within(inside), "A border within the inner square")
        assertNull(zone.draw(apart, Random(1)))
        assertNull(zone.draw(inside, Random(1)))
    }

    @Test
    fun `une distance minimale de 0 fait de la zone le carré entier, centre compris`() {
        val square = WildZone(0, 0, 0, 2)

        assertEquals(25L, square.within(wide).sumOf { it.area })
        assertTrue(Column(0, 0) in square)
        assertEquals(25, draws(square, wide, 2_000, seed = 3).toSet().size, "Every column of the square gets drawn")
    }

    @Test
    fun `la distance minimale vaut 0 ou plus et reste sous la maximale`() {
        assertThrows(IllegalArgumentException::class.java) { WildZone(0, 0, 5, 5) }
        assertThrows(IllegalArgumentException::class.java) { WildZone(0, 0, -1, 5) }
    }

    @Test
    fun `un rectangle dont une borne basse dépasse la haute est vide, et l'intersection de deux rectangles est leur partie commune`() {
        assertTrue(Bounds(1, 0, 0, 0).isEmpty)
        assertEquals(0L, Bounds(0, 0, 1, 0).area)
        assertEquals(Bounds(5, 10, -2, 3), Bounds(0, 10, -2, 8).intersect(Bounds(5, 20, -9, 3)))
        assertTrue(Bounds(0, 1, 0, 1).intersect(Bounds(2, 3, 0, 1)).isEmpty)
    }
}
