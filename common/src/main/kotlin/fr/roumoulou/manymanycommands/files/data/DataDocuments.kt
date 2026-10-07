// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.data

import fr.roumoulou.manymanycommands.rules.data.WrittenPosition
import fr.roumoulou.manymanycommands.rules.data.WrittenQuotaUsage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
Les formes sérialisées communes aux fichiers de données (cahier des charges, chapitre 2.7.5). Chaque clé y a un défaut à null : une clé qui
manque se décode, et c'est la règle de la forme écrite qui la dit obligatoire, au chemin de l'objet, là où le lecteur strict s'arrêterait sans
chemin. Un fichier s'écrit sans ses clés nulles (DataStore).
*/

/** Une position telle qu'elle s'écrit : sa dimension, ses coordonnées exactes et l'orientation du regard. */
@Serializable
data class PositionDocument(
    @SerialName(WrittenPosition.DIMENSION_KEY) val dimension: String? = null,
    @SerialName(WrittenPosition.X_KEY) val x: Double? = null,
    @SerialName(WrittenPosition.Y_KEY) val y: Double? = null,
    @SerialName(WrittenPosition.Z_KEY) val z: Double? = null,
    @SerialName(WrittenPosition.YAW_KEY) val yaw: Float? = null,
    @SerialName(WrittenPosition.PITCH_KEY) val pitch: Float? = null,
) {

    companion object {

        fun of(written: WrittenPosition): PositionDocument = PositionDocument(written.dimension, written.x, written.y, written.z, written.yaw, written.pitch)
    }

    fun toWritten(): WrittenPosition = WrittenPosition(dimension, x, y, z, yaw, pitch)
}

/** La plage en cours d'un quota telle qu'elle s'écrit : son horloge, son début sur cette horloge, ses usages. */
@Serializable
data class QuotaUsageDocument(
    @SerialName(WrittenQuotaUsage.CLOCK_KEY) val clock: String? = null,
    @SerialName(WrittenQuotaUsage.WINDOW_START_KEY) val windowStart: Long? = null,
    @SerialName(WrittenQuotaUsage.USES_KEY) val uses: Long? = null,
) {

    companion object {

        fun of(written: WrittenQuotaUsage): QuotaUsageDocument = QuotaUsageDocument(written.clock, written.windowStart, written.uses)
    }

    fun toWritten(): WrittenQuotaUsage = WrittenQuotaUsage(clock, windowStart, uses)
}
