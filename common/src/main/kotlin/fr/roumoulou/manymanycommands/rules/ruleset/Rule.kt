// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.ruleset

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DimensionIds
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath

/**
 * Une ligne du tableau « Règles et réglages » d'une fiche : sa clé, sa valeur livrée et ses valeurs admises. Elle vaut pour un réglage du serveur
 * comme pour une règle d'un jeu.
 *
 * Chaque sorte de règle a sa nature dans le fichier : c'est la couche `files` qui la lit, et qui refuse la valeur d'une autre nature.
 *
 * @param WRITTEN ce que le fichier écrit pour elle
 * @param VALUE sa valeur en vigueur
 * @property shipped la valeur livrée, celle de la colonne « Défaut »
 */
sealed class Rule<WRITTEN, VALUE>(val key: String, val shipped: VALUE) {

    /** Les constats d'une valeur écrite qui sort des valeurs admises, sans leur ligne. Vide quand elle est admise. */
    abstract fun findings(path: String, written: WRITTEN): List<FileFinding>

    /**
     * La valeur en vigueur d'une valeur écrite que [findings] admet.
     *
     * @param inherited la valeur de la règle quand rien n'est écrit : celle du jeu `default`, ou la valeur livrée
     */
    abstract fun value(written: WRITTEN, inherited: VALUE): VALUE

    /** Ce que le fichier écrit pour [value]. */
    abstract fun written(value: VALUE): WRITTEN

    /** La clé et sa valeur écrite, comme un fichier lu les donne. */
    fun writes(written: WRITTEN): Pair<String, Any?> = key to written

    override fun toString(): String = key

    internal fun shippedWritten(): WRITTEN = written(shipped)

    // Un fichier lu donne ses valeurs sans leur type : chacune a celui de sa règle, parce que c'est sur elle que la couche `files` l'a décodée.

    @Suppress("UNCHECKED_CAST")
    internal fun findingsOfRead(path: String, written: Any?): List<FileFinding> = findings(path, written as WRITTEN)

    @Suppress("UNCHECKED_CAST")
    internal fun valueOfRead(written: Any?, inherited: Any?): VALUE = value(written as WRITTEN, inherited as VALUE)
}

/** Un interrupteur, actif ou inactif : `true` ou `false`. */
class SwitchRule(key: String, shipped: Boolean) : Rule<Boolean, Boolean>(key, shipped) {

    override fun findings(path: String, written: Boolean): List<FileFinding> = emptyList()

    override fun value(written: Boolean, inherited: Boolean): Boolean = written

    override fun written(value: Boolean): Boolean = value
}

/** Un nombre entier, [min] ou plus : une limite, une distance en blocs, une durée en secondes entières. */
class IntegerRule(key: String, shipped: Long, val min: Long) : Rule<Long, Long>(key, shipped) {

    override fun findings(path: String, written: Long): List<FileFinding> = if (written < min) listOf(notAllowed(path, written, CommonMessages.ALLOWED_AT_LEAST.with(min))) else emptyList()

    override fun value(written: Long, inherited: Long): Long = written

    override fun written(value: Long): Long = value
}

/** Une liste de dimensions, chacune écrite par son identifiant. La valeur en vigueur porte les identifiants complets. */
class DimensionsRule(key: String, shipped: Set<String>) : Rule<List<String>, Set<String>>(key, shipped) {

    override fun findings(path: String, written: List<String>): List<FileFinding> = written.mapIndexedNotNull { index, dimension ->
        if (DimensionIds.normalize(dimension) == null) notAllowed(FindingPath.index(path, index), dimension, CommonMessages.ALLOWED_DIMENSION.with()) else null
    }

    override fun value(written: List<String>, inherited: Set<String>): Set<String> = written.mapNotNullTo(LinkedHashSet(), DimensionIds::normalize)

    override fun written(value: Set<String>): List<String> = value.toList()
}

/**
 * Un interrupteur par clé, dans un objet. Un jeu n'écrit que ceux qui le distinguent : un interrupteur absent garde sa valeur héritée.
 *
 * @param shipped la valeur livrée de chaque interrupteur, dans l'ordre où le fichier les écrit
 */
class SwitchesRule(key: String, shipped: Map<String, Boolean>) : Rule<Map<String, Boolean>, Map<String, Boolean>>(key, shipped) {

    val switches: Set<String> get() = shipped.keys

    override fun findings(path: String, written: Map<String, Boolean>): List<FileFinding> = emptyList()

    override fun value(written: Map<String, Boolean>, inherited: Map<String, Boolean>): Map<String, Boolean> = inherited + written

    override fun written(value: Map<String, Boolean>): Map<String, Boolean> = value
}

/**
 * Un quota, ou `null` quand la famille n'en a pas (cahier des charges, chapitre 2.5.9).
 *
 * Un quota s'écrit entier : `uses` et `period` y sont obligatoires, seul `clock` peut manquer, et rien n'y est repris du jeu `default`.
 */
class QuotaRule(key: String, shipped: Quota?) : Rule<WrittenQuota?, Quota?>(key, shipped) {

    private companion object {
        const val MIN_USES = 1L
        const val MIN_PERIOD = 1L
    }

    override fun findings(path: String, written: WrittenQuota?): List<FileFinding> {
        if (written == null) return emptyList()
        return buildList {
            addAll(wholeNumber(path, WrittenQuota.USES_KEY, written.uses, MIN_USES))
            addAll(wholeNumber(path, WrittenQuota.PERIOD_KEY, written.period, MIN_PERIOD))
            if (written.clock != null && QuotaClock.of(written.clock) == null) {
                add(notAllowed(FindingPath.property(path, WrittenQuota.CLOCK_KEY), written.clock, QuotaClock.entries.joinToString(", ") { it.value }))
            }
        }
    }

    override fun value(written: WrittenQuota?, inherited: Quota?): Quota? {
        if (written == null) return null
        val uses = written.uses ?: error("Unvalidated quota without '${WrittenQuota.USES_KEY}'")
        val period = written.period ?: error("Unvalidated quota without '${WrittenQuota.PERIOD_KEY}'")
        val clock = if (written.clock == null) QuotaClock.REAL else QuotaClock.of(written.clock) ?: error("Unvalidated quota clock '${written.clock}'")
        return Quota(uses, period, clock)
    }

    override fun written(value: Quota?): WrittenQuota? = value?.let { WrittenQuota(it.uses, it.periodSeconds, it.clock.value) }

    private fun wholeNumber(path: String, key: String, written: Long?, min: Long): List<FileFinding> = when {
        written == null -> listOf(missingKey(path, key))
        written < min -> listOf(notAllowed(FindingPath.property(path, key), written, CommonMessages.ALLOWED_AT_LEAST.with(min)))
        else -> emptyList()
    }
}

/** Le constat d'une clé obligatoire absente, sans sa ligne : au chemin de l'objet qui devrait la porter, qui a une ligne dans le fichier, là où la clé n'en a pas. */
internal fun missingKey(path: String, key: String): FileFinding = FileFinding(path, null, CommonMessages.FINDING_MISSING_KEY.with(key))

/** Le constat d'une valeur hors des valeurs admises, sans sa ligne. */
internal fun notAllowed(path: String, value: Any, allowed: Any): FileFinding = FileFinding(path, null, CommonMessages.FINDING_NOT_ALLOWED.with(value, allowed))
