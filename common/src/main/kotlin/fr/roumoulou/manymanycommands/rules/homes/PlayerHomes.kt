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
 * Qui agit sur les homes d'un joueur (`homes.md` des specs, chapitres 3 et 4) : le joueur lui-même, sous son jeu de règles, ou l'administrateur
 * sur les homes d'un joueur nommé, sans la limite ni les règles de dimension, chaque message nommant alors le propriétaire.
 */
sealed interface HomesActor {

    /** Le joueur, sur ses propres homes. */
    data object Owner : HomesActor

    /** L'administrateur, dans la branche `player`, sur les homes de [ownerName]. */
    data class Administrator(val ownerName: String) : HomesActor
}

/**
 * Les homes d'un joueur (`homes.md` des specs, chapitres 1, 3 et 4) : chacun sous son nom, et le home désigné par défaut.
 *
 * Chaque commande y est une règle pure : elle reçoit des valeurs et rend un [HomesChange], le refus dans l'ordre de sa fiche ou les homes
 * changés avec leur message. Les homes ne changent jamais sur place : un changement est de nouveaux homes. L'[HomesActor] dit sous quelles
 * règles et avec quels messages.
 *
 * @property homes les homes, par leur nom, dans l'ordre de leur création
 * @property designated le home désigné par `/homes default`, qui en fait partie, ou `null`
 */
data class PlayerHomes(val homes: Map<String, Position> = emptyMap(), val designated: String? = null) : Holdings {

    init {
        require(designated == null || designated in homes) { "The designated home '$designated' is not one of the homes" }
    }

    override val isEmpty: Boolean get() = homes.isEmpty()

    /** Le home par défaut (chapitre 3.7) : le seul home du joueur, ou celui qu'il a désigné dès qu'il en a plusieurs. `null` sans home. */
    val defaultHome: String? get() = designated ?: homes.keys.singleOrNull()

    operator fun get(name: String): Position? = homes[name]

    /** Les noms dans l'ordre de la liste (chapitre 3.4). */
    fun sortedNames(): List<String> = homes.keys.sortedWith(NameRules.LISTING_ORDER)

    /**
     * `/homes create <name>` (chapitre 3.1) : un home à [position], qui ne remplace jamais un home existant. L'administrateur (chapitre 4) passe
     * outre la dimension interdite et la limite, et le placement se refuse en dernier.
     *
     * @param isWithinWorld le placement de l'administrateur est dans les limites du monde, consulté en dernier
     */
    fun create(name: String, position: Position, ruleSet: RuleValues, actor: HomesActor = HomesActor.Owner, isWithinWorld: () -> Boolean = { true }): HomesChange {
        if (!NameRules.isName(name)) return HomesChange.Refused(CommonMessages.NAME_INVALID.with())
        if (actor == HomesActor.Owner) {
            if (position.dimension in ruleSet[HomesRules.forbiddenDimensions]) return HomesChange.Refused(HomesMessages.FORBIDDEN_DIMENSION.with(position.dimension))
            val max = ruleSet[HomesRules.maxHomes]
            if (homes.size >= max) return HomesChange.Refused(HomesMessages.limitReached(max))
        }
        if (name in homes) return HomesChange.Refused(HomesMessages.ALREADY_EXISTS.with(name))
        if (!isWithinWorld()) return HomesChange.Refused(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with())
        return HomesChange.Applied(copy(homes = homes + (name to position)), placed(HomesMessages.CREATED, HomesMessages.CREATED_OF_PLAYER, name, position, actor))
    }

    /**
     * `/homes teleport [<name>]` (chapitre 3.2) : le home à rejoindre par le déroulé commun, ou le premier des refus propres aux homes, dans
     * l'ordre de la fiche. Un home dont la dimension n'existe plus reste un home : seul `teleport` le refuse. L'administrateur (chapitre 4)
     * passe outre la dimension de départ interdite, et nomme toujours le home.
     *
     * @param name le home demandé, `null` pour le home par défaut
     * @param departure la dimension où se tient le joueur
     * @param isDimensionKnown le serveur porte la dimension
     * @param quota le quota du joueur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun teleport(name: String?, departure: String, ruleSet: RuleValues, isDimensionKnown: (String) -> Boolean, quota: Quota?, actor: HomesActor = HomesActor.Owner, quotaWaitSeconds: () -> Long): HomesTeleport {
        val target = name ?: defaultHome ?: return HomesTeleport.Refused(HomesMessages.NO_DEFAULT.with())
        val position = homes[target] ?: return HomesTeleport.Refused(HomesMessages.ABSENT.with(target))
        val texts = DestinationTexts({ HomesMessages.DEPARTURE_FORBIDDEN.with(it) }, { HomesMessages.DIMENSION_MISSING.with(it, target) }, HomesMessages::quotaReached)
        val checkedDeparture = departure.takeIf { actor == HomesActor.Owner }
        DestinationRefusals.first(HomesRules, ruleSet, checkedDeparture, position.dimension, isDimensionKnown, quota, quotaWaitSeconds, texts)?.let { return HomesTeleport.Refused(it) }
        return HomesTeleport.Target(target, position)
    }

    /** `/homes delete <name>` (chapitre 3.3). La suppression du home désigné efface la désignation. */
    fun delete(name: String, actor: HomesActor = HomesActor.Owner): HomesChange {
        val position = homes[name] ?: return absent(name)
        return HomesChange.Applied(PlayerHomes(homes - name, designated.takeIf { it != name }), placed(HomesMessages.DELETED, HomesMessages.DELETED_OF_PLAYER, name, position, actor))
    }

    /** `/homes rename <name> <newName>` (chapitre 3.5) : le nom change, et rien d'autre. Le home par défaut le reste sous son nouveau nom. */
    fun rename(name: String, newName: String, actor: HomesActor = HomesActor.Owner): HomesChange {
        if (!NameRules.isName(newName)) return HomesChange.Refused(CommonMessages.NAME_INVALID.with())
        if (name !in homes) return absent(name)
        if (newName == name) return HomesChange.Refused(HomesMessages.SAME_NAME.with(name))
        if (newName in homes) return HomesChange.Refused(HomesMessages.ALREADY_EXISTS.with(newName))
        val renamed = homes.entries.associate { (key, position) -> (if (key == name) newName else key) to position }
        val message = when (actor) {
            HomesActor.Owner -> HomesMessages.RENAMED.with(name, newName)
            is HomesActor.Administrator -> HomesMessages.RENAMED_OF_PLAYER.with(name, actor.ownerName, newName)
        }
        return HomesChange.Applied(PlayerHomes(renamed, if (designated == name) newName else designated), message)
    }

    /**
     * `/homes move <name>` (chapitre 3.6) : la position, la dimension et l'orientation du home deviennent [position]. L'administrateur
     * (chapitre 4) passe outre la dimension interdite, et le placement se refuse en dernier.
     */
    fun move(name: String, position: Position, ruleSet: RuleValues, actor: HomesActor = HomesActor.Owner, isWithinWorld: () -> Boolean = { true }): HomesChange {
        if (name !in homes) return absent(name)
        if (actor == HomesActor.Owner && position.dimension in ruleSet[HomesRules.forbiddenDimensions]) return HomesChange.Refused(HomesMessages.FORBIDDEN_DIMENSION.with(position.dimension))
        if (!isWithinWorld()) return HomesChange.Refused(CommonMessages.PLACEMENT_OUT_OF_BOUNDS.with())
        return HomesChange.Applied(copy(homes = homes + (name to position)), placed(HomesMessages.MOVED, HomesMessages.MOVED_OF_PLAYER, name, position, actor))
    }

    /** `/homes default <name>` (chapitre 3.7). Désigner le home déjà par défaut réussit comme si c'était la première fois (cahier des charges, chapitre 2.12). */
    fun designate(name: String, actor: HomesActor = HomesActor.Owner): HomesChange {
        if (name !in homes) return absent(name)
        val message = when (actor) {
            HomesActor.Owner -> HomesMessages.DEFAULT_SET.with(name)
            is HomesActor.Administrator -> HomesMessages.DEFAULT_SET_OF_PLAYER.with(actor.ownerName, name)
        }
        return HomesChange.Applied(copy(designated = name), message)
    }

    private fun absent(name: String): HomesChange = HomesChange.Refused(HomesMessages.ABSENT.with(name))

    /** Le message qui nomme un home et sa position, sa dimension et ses coordonnées entières : [own] pour le joueur, [ofPlayer] pour l'administrateur, le propriétaire en second. */
    private fun placed(own: Situation, ofPlayer: Situation, name: String, position: Position, actor: HomesActor): Message = when (actor) {
        HomesActor.Owner -> own.with(name, position.dimension, position.blockX, position.blockY, position.blockZ)
        is HomesActor.Administrator -> ofPlayer.with(name, actor.ownerName, position.dimension, position.blockX, position.blockY, position.blockZ)
    }
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
