// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

/** Un point du monde, en valeurs : l'identifiant de sa dimension (`minecraft:the_nether`) et ses coordonnées exactes. */
data class Spot(val dimension: String, val x: Double, val y: Double, val z: Double) {

    /** Le carré de la distance en ligne droite à [other], qui n'a de sens que dans une même dimension. */
    fun distanceSquaredTo(other: Spot): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }
}
