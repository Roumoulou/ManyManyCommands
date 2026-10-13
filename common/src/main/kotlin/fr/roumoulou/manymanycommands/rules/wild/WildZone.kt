// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.wild

import kotlin.math.abs
import kotlin.random.Random

/**
 * Un rectangle de colonnes du monde, en coordonnées de blocs, bornes comprises. Il est vide quand une borne basse dépasse la haute.
 *
 * La bordure du monde en est un : les colonnes dont le centre est dans la bordure.
 */
data class Bounds(val minX: Long, val maxX: Long, val minZ: Long, val maxZ: Long) {

    val isEmpty: Boolean get() = minX > maxX || minZ > maxZ

    /** Le nombre de colonnes. */
    val area: Long get() = if (isEmpty) 0 else (maxX - minX + 1) * (maxZ - minZ + 1)

    fun intersect(other: Bounds): Bounds = Bounds(maxOf(minX, other.minX), minOf(maxX, other.maxX), maxOf(minZ, other.minZ), minOf(maxZ, other.maxZ))

    operator fun contains(column: Column): Boolean = column.x in minX..maxX && column.z in minZ..maxZ
}

/** Une colonne du monde : les coordonnées x et z de ses blocs. */
data class Column(val x: Long, val z: Long)

/**
 * La zone du tirage (`wild.md` des specs, chapitre 2) : une couronne carrée autour du point d'apparition du monde, dont le plus grand des deux
 * écarts au centre, en x ou en z, vaut au moins [minDistance] et au plus [maxDistance].
 *
 * La couronne se découpe en quatre bandes disjointes qui la couvrent entière : au nord et au sud, toute la largeur ; à l'ouest et à l'est, ce
 * qui reste entre les deux. Chacune se rogne à la bordure du monde, et un tirage choisit une bande au prorata de ses colonnes, puis une colonne
 * de la bande : chaque colonne de la zone que la bordure admet a la même chance.
 *
 * @property centerX la coordonnée x du point d'apparition du monde
 * @property centerZ sa coordonnée z
 */
class WildZone(val centerX: Long, val centerZ: Long, val minDistance: Long, val maxDistance: Long) {

    init {
        require(minDistance in 0 until maxDistance) { "The minimal distance $minDistance must be at least 0 and below the maximal distance $maxDistance" }
    }

    /** Les bandes de la couronne. Une distance minimale de 0 ne laisse rien entre le nord et le sud : l'ouest et l'est sont vides. */
    private val bands: List<Bounds> = listOf(
        Bounds(centerX - maxDistance, centerX + maxDistance, centerZ + minDistance, centerZ + maxDistance),
        Bounds(centerX - maxDistance, centerX + maxDistance, centerZ - maxDistance, centerZ - maxOf(minDistance, 1)),
        Bounds(centerX - maxDistance, centerX - minDistance, centerZ - minDistance + 1, centerZ + minDistance - 1),
        Bounds(centerX + minDistance, centerX + maxDistance, centerZ - minDistance + 1, centerZ + minDistance - 1),
    ).filterNot { it.isEmpty }

    /** La colonne est dans la couronne. */
    operator fun contains(column: Column): Boolean = maxOf(abs(column.x - centerX), abs(column.z - centerZ)) in minDistance..maxDistance

    /** Ce que [border] laisse de la zone : ses bandes rognées, et rien quand la bordure exclut toute la zone. */
    fun within(border: Bounds): List<Bounds> = bands.map { it.intersect(border) }.filterNot { it.isEmpty }

    /**
     * Tire une colonne, chaque colonne de la zone que [border] admet ayant la même chance.
     *
     * @return `null` quand la bordure exclut toute la zone : rien n'est tiré
     */
    fun draw(border: Bounds, random: Random): Column? {
        val bands = within(border)
        if (bands.isEmpty()) return null
        var pick = random.nextLong(bands.sumOf { it.area })
        val band = bands.first { band -> pick -= band.area; pick < 0 }
        return Column(random.nextLong(band.minX, band.maxX + 1), random.nextLong(band.minZ, band.maxZ + 1))
    }
}
