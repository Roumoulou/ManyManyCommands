// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.warps

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.Situation
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.teleport.DestinationRefusals
import fr.roumoulou.manymanycommands.rules.teleport.DestinationTexts

/**
 * Un warp (`warps.md` des specs, chapitre 1) : sa position, et sa clé d'accès quand il est réservé.
 *
 * @property key la clé des joueurs qui ont accès au warp, `null` quand il est ouvert à tous
 */
data class Warp(val position: Position, val key: String? = null) {

    /** Le warp est ouvert à tous, ou réservé à qui a le nœud de sa clé. */
    fun isAccessible(hasAccess: (key: String) -> Boolean): Boolean = key == null || hasAccess(key)
}

/**
 * Les warps du serveur (`warps.md` des specs, chapitres 1 à 3), par leur nom, dans l'ordre de leur création.
 *
 * Chaque commande y est une règle pure : elle reçoit des valeurs et rend un [WarpsChange], le refus dans l'ordre de sa fiche ou les warps
 * changés avec leur message. Les warps ne changent jamais sur place : un changement est de nouveaux warps.
 */
data class Warps(val warps: Map<String, Warp> = emptyMap()) {

    operator fun get(name: String): Warp? = warps[name]

    /** Les noms dans l'ordre de la liste (chapitre 2.2). */
    fun sortedNames(): List<String> = warps.keys.sortedWith(NameRules.LISTING_ORDER)

    /** Les warps auxquels [hasAccess] donne accès, dans l'ordre de la liste : les warps ouverts, et les réservés dont le lecteur a la clé. */
    fun accessible(hasAccess: (key: String) -> Boolean): List<String> = warps.filterValues { it.isAccessible(hasAccess) }.keys.sortedWith(NameRules.LISTING_ORDER)

    /** Les clés que portent les warps réservés, pour la complétion de `<key>`. */
    fun keys(): List<String> = warps.values.mapNotNull { it.key }.distinct().sorted()

    /**
     * `/warps create <name>` (chapitre 3.1) : un warp ouvert à tous, à [position], qui ne remplace jamais un warp existant.
     *
     * @param isPlacementAllowed le placement admet la position : dans la bordure du monde et la hauteur de la dimension
     */
    fun create(name: String, position: Position, isPlacementAllowed: () -> Boolean): WarpsChange {
        if (!NameRules.isName(name)) return WarpsChange.Refused(CommonMessages.NAME_INVALID.with())
        if (name in warps) return WarpsChange.Refused(WarpsMessages.ALREADY_EXISTS.with(name))
        if (!isPlacementAllowed()) return WarpsChange.Refused(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with())
        return WarpsChange.Applied(copy(warps = warps + (name to Warp(position))), placed(WarpsMessages.CREATED, name, position))
    }

    /** `/warps delete <name>` (chapitre 3.2). */
    fun delete(name: String): WarpsChange {
        val warp = warps[name] ?: return absent(name)
        return WarpsChange.Applied(copy(warps = warps - name), placed(WarpsMessages.DELETED, name, warp.position))
    }

    /** `/warps rename <name> <newName>` (chapitre 3.3) : le nom change, et rien d'autre. */
    fun rename(name: String, newName: String): WarpsChange {
        if (!NameRules.isName(newName)) return WarpsChange.Refused(CommonMessages.NAME_INVALID.with())
        if (name !in warps) return absent(name)
        if (newName == name) return WarpsChange.Refused(WarpsMessages.SAME_NAME.with(name))
        if (newName in warps) return WarpsChange.Refused(WarpsMessages.ALREADY_EXISTS.with(newName))
        return WarpsChange.Applied(copy(warps = warps.entries.associate { (key, warp) -> (if (key == name) newName else key) to warp }), WarpsMessages.RENAMED.with(name, newName))
    }

    /** `/warps move <name>` (chapitre 3.4) : la position, la dimension et l'orientation du warp deviennent [position]. Sa clé reste. */
    fun move(name: String, position: Position, isPlacementAllowed: () -> Boolean): WarpsChange {
        val warp = warps[name] ?: return absent(name)
        if (!isPlacementAllowed()) return WarpsChange.Refused(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with())
        return WarpsChange.Applied(copy(warps = warps + (name to warp.copy(position = position))), placed(WarpsMessages.MOVED, name, position))
    }

    /** `/warps restrict <name> <key>` (chapitre 3.5) : une nouvelle clé remplace l'ancienne. La clé s'écrit en minuscules. */
    fun restrict(name: String, key: String): WarpsChange {
        if (!NameRules.isLowercaseName(key)) return WarpsChange.Refused(WarpsMessages.KEY_INVALID.with())
        val warp = warps[name] ?: return absent(name)
        return WarpsChange.Applied(copy(warps = warps + (name to warp.copy(key = key))), WarpsMessages.RESTRICTED.with(name, key))
    }

    /** `/warps unrestrict <name>` (chapitre 3.5). Rouvrir un warp ouvert réussit comme si c'était la première fois (cahier des charges, chapitre 2.12). */
    fun unrestrict(name: String): WarpsChange {
        val warp = warps[name] ?: return absent(name)
        return WarpsChange.Applied(copy(warps = warps + (name to warp.copy(key = null))), WarpsMessages.UNRESTRICTED.with(name))
    }

    /**
     * `/warps teleport <name>` (chapitre 2.1) : le warp à rejoindre par le déroulé commun, ou le premier des refus propres, dans l'ordre de la
     * fiche. Un warp réservé à d'autres répond comme s'il n'existait pas.
     *
     * @param hasAccess le joueur a le nœud d'une clé
     * @param departure la dimension où se tient le joueur
     * @param isDimensionKnown le serveur porte la dimension
     * @param quota le quota du joueur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun teleport(name: String, hasAccess: (key: String) -> Boolean, departure: String, ruleSet: RuleValues, isDimensionKnown: (String) -> Boolean, quota: Quota?, quotaWaitSeconds: () -> Long): WarpsTeleport {
        val warp = warps[name]?.takeIf { it.isAccessible(hasAccess) } ?: return WarpsTeleport.Refused(WarpsMessages.ABSENT.with(name))
        val texts = DestinationTexts({ WarpsMessages.DEPARTURE_FORBIDDEN.with(it) }, { WarpsMessages.DIMENSION_MISSING.with(it, name) }, WarpsMessages::quotaReached)
        DestinationRefusals.first(WarpsRules, ruleSet, departure, warp.position.dimension, isDimensionKnown, quota, quotaWaitSeconds, texts)?.let { return WarpsTeleport.Refused(it) }
        return WarpsTeleport.Target(name, warp.position)
    }

    private fun absent(name: String): WarpsChange = WarpsChange.Refused(WarpsMessages.ABSENT.with(name))

    /** Le message qui nomme un warp et sa position : sa dimension et ses coordonnées entières. */
    private fun placed(situation: Situation, name: String, position: Position): Message = situation.with(name, position.dimension, position.blockX, position.blockY, position.blockZ)
}

/** L'issue d'une commande sur les warps : un refus, ou les warps changés et le succès qui le dit. */
sealed interface WarpsChange {

    val message: Message

    data class Refused(override val message: Message) : WarpsChange

    data class Applied(val warps: Warps, override val message: Message) : WarpsChange
}

/** L'issue des refus propres de `/warps teleport` : un refus, ou le warp à rejoindre par le déroulé commun. */
sealed interface WarpsTeleport {

    data class Refused(val message: Message) : WarpsTeleport

    data class Target(val name: String, val position: Position) : WarpsTeleport
}
