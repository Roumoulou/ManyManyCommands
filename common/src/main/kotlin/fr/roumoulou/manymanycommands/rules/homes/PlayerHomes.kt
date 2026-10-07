// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.homes

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.data.Holdings
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.Situation
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.teleport.DestinationRefusals
import fr.roumoulou.manymanycommands.rules.teleport.DestinationTexts

/**
 * Les homes d'un joueur (`homes.md` des specs, chapitres 1 et 3) : chacun sous son nom, et le home désigné par défaut.
 *
 * Chaque commande du joueur y est une règle pure : elle reçoit des valeurs et rend un [HomesChange], le refus dans l'ordre de sa fiche ou les
 * homes changés avec leur message. Les homes ne changent jamais sur place : un changement est de nouveaux homes.
 *
 * @property homes les homes, par leur nom, dans l'ordre de leur création
 * @property designated le home désigné par `/homes default`, qui en fait partie, ou `null`
 */
data class PlayerHomes(val homes: Map<String, Position> = emptyMap(), val designated: String? = null) : Holdings {

    companion object {

        /** L'ordre de la liste (chapitre 3.4) : alphabétique sans tenir compte de la casse, la majuscule d'abord à égalité. */
        val LISTING_ORDER: Comparator<String> = compareBy<String> { it.lowercase() }.thenBy { it }
    }

    init {
        require(designated == null || designated in homes) { "The designated home '$designated' is not one of the homes" }
    }

    override val isEmpty: Boolean get() = homes.isEmpty()

    /** Le home par défaut (chapitre 3.7) : le seul home du joueur, ou celui qu'il a désigné dès qu'il en a plusieurs. `null` sans home. */
    val defaultHome: String? get() = designated ?: homes.keys.singleOrNull()

    operator fun get(name: String): Position? = homes[name]

    /** Les noms dans l'ordre de la liste. */
    fun sortedNames(): List<String> = homes.keys.sortedWith(LISTING_ORDER)

    /** `/homes create <name>` (chapitre 3.1) : un home à [position], qui ne remplace jamais un home existant. */
    fun create(name: String, position: Position, ruleSet: RuleValues): HomesChange {
        if (!NameRules.isName(name)) return HomesChange.Refused(CommonMessages.NAME_INVALID.with())
        if (position.dimension in ruleSet[HomesRules.forbiddenDimensions]) return HomesChange.Refused(HomesMessages.FORBIDDEN_DIMENSION.with(position.dimension))
        val max = ruleSet[HomesRules.maxHomes]
        if (homes.size >= max) return HomesChange.Refused(HomesMessages.limitReached(max))
        if (name in homes) return HomesChange.Refused(HomesMessages.ALREADY_EXISTS.with(name))
        return HomesChange.Applied(copy(homes = homes + (name to position)), placed(HomesMessages.CREATED, name, position))
    }

    /**
     * `/homes teleport [<name>]` (chapitre 3.2) : le home à rejoindre par le déroulé commun, ou le premier des refus propres aux homes, dans
     * l'ordre de la fiche. Un home dont la dimension n'existe plus reste un home : seul `teleport` le refuse.
     *
     * @param name le home demandé, `null` pour le home par défaut
     * @param departure la dimension où se tient le joueur
     * @param isDimensionKnown le serveur porte la dimension
     * @param quota le quota du joueur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun teleport(name: String?, departure: String, ruleSet: RuleValues, isDimensionKnown: (String) -> Boolean, quota: Quota?, quotaWaitSeconds: () -> Long): HomesTeleport {
        val target = name ?: defaultHome ?: return HomesTeleport.Refused(HomesMessages.NO_DEFAULT.with())
        val position = homes[target] ?: return HomesTeleport.Refused(HomesMessages.ABSENT.with(target))
        val texts = DestinationTexts({ HomesMessages.DEPARTURE_FORBIDDEN.with(it) }, { HomesMessages.DIMENSION_MISSING.with(it, target) }, HomesMessages::quotaReached)
        DestinationRefusals.first(HomesRules, ruleSet, departure, position.dimension, isDimensionKnown, quota, quotaWaitSeconds, texts)?.let { return HomesTeleport.Refused(it) }
        return HomesTeleport.Target(target, position)
    }

    /** `/homes delete <name>` (chapitre 3.3). La suppression du home désigné efface la désignation. */
    fun delete(name: String): HomesChange {
        val position = homes[name] ?: return absent(name)
        return HomesChange.Applied(PlayerHomes(homes - name, designated.takeIf { it != name }), placed(HomesMessages.DELETED, name, position))
    }

    /** `/homes rename <name> <newName>` (chapitre 3.5) : le nom change, et rien d'autre. Le home par défaut le reste sous son nouveau nom. */
    fun rename(name: String, newName: String): HomesChange {
        if (!NameRules.isName(newName)) return HomesChange.Refused(CommonMessages.NAME_INVALID.with())
        if (name !in homes) return absent(name)
        if (newName == name) return HomesChange.Refused(HomesMessages.SAME_NAME.with(name))
        if (newName in homes) return HomesChange.Refused(HomesMessages.ALREADY_EXISTS.with(newName))
        val renamed = homes.entries.associate { (key, position) -> (if (key == name) newName else key) to position }
        return HomesChange.Applied(PlayerHomes(renamed, if (designated == name) newName else designated), HomesMessages.RENAMED.with(name, newName))
    }

    /** `/homes move <name>` (chapitre 3.6) : la position, la dimension et l'orientation du home deviennent [position]. */
    fun move(name: String, position: Position, ruleSet: RuleValues): HomesChange {
        if (name !in homes) return absent(name)
        if (position.dimension in ruleSet[HomesRules.forbiddenDimensions]) return HomesChange.Refused(HomesMessages.FORBIDDEN_DIMENSION.with(position.dimension))
        return HomesChange.Applied(copy(homes = homes + (name to position)), placed(HomesMessages.MOVED, name, position))
    }

    /** `/homes default <name>` (chapitre 3.7). Désigner le home déjà par défaut réussit comme si c'était la première fois (cahier des charges, chapitre 2.12). */
    fun designate(name: String): HomesChange {
        if (name !in homes) return absent(name)
        return HomesChange.Applied(copy(designated = name), HomesMessages.DEFAULT_SET.with(name))
    }

    private fun absent(name: String): HomesChange = HomesChange.Refused(HomesMessages.ABSENT.with(name))

    /** Le message qui nomme un home et sa position : sa dimension et ses coordonnées entières. */
    private fun placed(situation: Situation, name: String, position: Position): Message = situation.with(name, position.dimension, position.blockX, position.blockY, position.blockZ)
}

/** L'issue d'une commande sur les homes d'un joueur : un refus, ou les homes changés et le succès qui le dit. */
sealed interface HomesChange {

    val message: Message

    data class Refused(override val message: Message) : HomesChange

    data class Applied(val homes: PlayerHomes, override val message: Message) : HomesChange
}

/** L'issue des refus propres de `/homes teleport` : un refus, ou le home à rejoindre par le déroulé commun. */
sealed interface HomesTeleport {

    data class Refused(val message: Message) : HomesTeleport

    data class Target(val name: String, val position: Position) : HomesTeleport
}
