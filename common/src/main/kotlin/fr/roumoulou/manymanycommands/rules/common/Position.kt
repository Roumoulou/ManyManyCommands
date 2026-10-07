// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.common

import kotlin.math.floor

/**
 * Une position telle qu'un fichier de données l'écrit (cahier des charges, chapitre 2.7.5) : l'identifiant de sa dimension, ses coordonnées
 * exactes et l'orientation du regard. Un home, un warp en sont une.
 */
data class Position(val dimension: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {

    // Les coordonnées entières, celles des messages : le bloc où se tient le joueur.

    val blockX: Int get() = floor(x).toInt()

    val blockY: Int get() = floor(y).toInt()

    val blockZ: Int get() = floor(z).toInt()
}
