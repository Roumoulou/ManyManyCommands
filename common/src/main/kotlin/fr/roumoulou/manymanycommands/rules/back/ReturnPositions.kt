// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.back

import fr.roumoulou.manymanycommands.rules.common.Position
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.ruleset.Quota
import fr.roumoulou.manymanycommands.rules.ruleset.RuleValues
import fr.roumoulou.manymanycommands.rules.teleport.DestinationRefusals
import fr.roumoulou.manymanycommands.rules.teleport.DestinationTexts
import java.util.UUID

/**
 * Les positions de retour des joueurs (`back.md` des specs, chapitres 1 et 2) : l'endroit que chacun a quitté en dernier, une par joueur. Elles
 * vivent en mémoire, suivent le joueur d'une reconnexion à l'autre et s'effacent au redémarrage du serveur.
 */
class ReturnPositions {

    private val positions = HashMap<UUID, Position>()

    operator fun get(player: UUID): Position? = positions[player]

    fun record(player: UUID, position: Position) {
        positions[player] = position
    }
}

/** Les règles du retour (`back.md` des specs, chapitres 2 et 3), en valeurs. */
object ReturnRules {

    /** [source] enregistre l'endroit quitté quand le jeu de règles du joueur l'active. */
    fun records(ruleSet: RuleValues, source: BackSource): Boolean = ruleSet[BackRules.sources][source.key] == true

    /**
     * `/back` (chapitre 3) : la position à rejoindre par le déroulé commun, ou le premier des refus propres au retour, dans l'ordre de la fiche.
     *
     * @param position la position de retour du joueur, `null` quand il n'en a pas
     * @param departure la dimension où se tient le joueur
     * @param isDimensionKnown le serveur porte la dimension
     * @param quota le quota du joueur, `null` quand il n'en a pas ou en est dispensé
     * @param quotaWaitSeconds ce qui reste à attendre avant le prochain usage, consulté seulement quand le quota existe
     */
    fun teleport(position: Position?, departure: String, ruleSet: RuleValues, isDimensionKnown: (String) -> Boolean, quota: Quota?, quotaWaitSeconds: () -> Long): BackTeleport {
        val target = position ?: return BackTeleport.Refused(BackMessages.NO_POSITION.with())
        val texts = DestinationTexts({ BackMessages.DEPARTURE_FORBIDDEN.with(it) }, { BackMessages.DIMENSION_MISSING.with(it) }, BackMessages::quotaReached)
        DestinationRefusals.first(BackRules, ruleSet, departure, target.dimension, isDimensionKnown, quota, quotaWaitSeconds, texts)?.let { return BackTeleport.Refused(it) }
        return BackTeleport.Target(target)
    }
}

/** L'issue des refus propres de `/back` : un refus, ou la position à rejoindre par le déroulé commun. */
sealed interface BackTeleport {

    data class Refused(val message: Message) : BackTeleport

    data class Target(val position: Position) : BackTeleport
}
