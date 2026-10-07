// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DimensionIds
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.ruleset.missingKey
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/**
 * Une position telle que le fichier de données l'écrit (cahier des charges, chapitre 2.7.5) : chaque clé vaut `null` quand elle manque, et les
 * six sont obligatoires.
 */
data class WrittenPosition(val dimension: String?, val x: Double?, val y: Double?, val z: Double?, val yaw: Float?, val pitch: Float?) {

    companion object {

        const val DIMENSION_KEY = "dimension"
        const val X_KEY = "x"
        const val Y_KEY = "y"
        const val Z_KEY = "z"
        const val YAW_KEY = "yaw"
        const val PITCH_KEY = "pitch"

        fun of(position: Position): WrittenPosition = WrittenPosition(position.dimension, position.x, position.y, position.z, position.yaw, position.pitch)
    }

    /** Les constats de la position écrite à [path], sans leur ligne : une clé qui manque, une dimension qui n'est pas un identifiant. */
    fun findings(path: String): List<FileFinding> = buildList {
        when {
            dimension == null -> add(missingKey(path, DIMENSION_KEY))
            DimensionIds.normalize(dimension) == null -> add(notAllowed(FindingPath.property(path, DIMENSION_KEY), dimension, CommonMessages.ALLOWED_DIMENSION.with()))
        }
        listOf(X_KEY to x, Y_KEY to y, Z_KEY to z, YAW_KEY to yaw, PITCH_KEY to pitch).forEach { (key, value) -> if (value == null) add(missingKey(path, key)) }
    }

    /** La position d'une écriture que [findings] admet, sa dimension sous son identifiant complet. */
    fun value(): Position = Position(
        dimension = DimensionIds.normalize(dimension ?: error("Unvalidated position without '$DIMENSION_KEY'")) ?: error("Unvalidated dimension '$dimension'"),
        x = x ?: error("Unvalidated position without '$X_KEY'"),
        y = y ?: error("Unvalidated position without '$Y_KEY'"),
        z = z ?: error("Unvalidated position without '$Z_KEY'"),
        yaw = yaw ?: error("Unvalidated position without '$YAW_KEY'"),
        pitch = pitch ?: error("Unvalidated position without '$PITCH_KEY'"),
    )
}
