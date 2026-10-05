// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.message

import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.LanguageSetting
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.Placement
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** Ce qui regarde passer les messages : le joueur qui reçoit, `null` pour la console, et le message, avant sa mise en texte. */
fun interface MessageObserver {

    fun onMessage(player: ServerPlayer?, message: Message)
}

/**
 * L'envoi des messages du mod, leur seule porte de sortie (cahier des charges, chapitre 2.6).
 *
 * Chaque message passe d'abord devant les observateurs : c'est là qu'un test lit la situation produite, console comprise, puisque la console ne
 * reçoit qu'un texte résolu.
 *
 * @param language le réglage `language` en vigueur, relu à chaque envoi parce que le rechargement le change
 * @param machineLanguage la langue de la machine, qui décide de celle de la console sous `auto`
 */
class Messenger(private val texts: TextCatalog, private val language: () -> LanguageSetting, private val machineLanguage: String = Locale.getDefault().language) {

    private val components = MessageComponents(texts)

    private val observers = CopyOnWriteArrayList<MessageObserver>()

    val consoleLanguage: Language get() = language().forConsole(machineLanguage)

    /** Regarde passer les messages jusqu'à la fermeture de ce que la fonction rend. */
    fun observe(observer: MessageObserver): AutoCloseable {
        observers += observer
        return AutoCloseable { observers -= observer }
    }

    /**
     * Répond à la source d'une commande. Le destinataire est le joueur que la source désigne, la console sinon.
     *
     * @param logged le message se journalise, comme la réponse d'une commande d'opérateur du jeu (cahier des charges, chapitre 2.11). Le jeu ne
     * journalise jamais un refus.
     */
    fun reply(source: CommandSourceStack, message: Message, logged: Boolean = false) {
        val player = source.player
        if (player != null && message.type.placement == Placement.ACTION_BAR) return tell(player, message)
        notifyObservers(player, message)
        val component = component(player, message)
        if (message.type == MessageType.REFUSAL) source.sendFailure(component) else source.sendSuccess({ component }, logged)
    }

    /** Envoie un message à un joueur hors de la réponse d'une commande : au chat ou dans la barre d'action, selon son type. */
    fun tell(player: ServerPlayer, message: Message) {
        notifyObservers(player, message)
        player.sendSystemMessage(component(player, message), message.type.placement == Placement.ACTION_BAR)
    }

    /** Le texte de [message] pour le journal du serveur, dans la langue de la console. */
    fun forJournal(message: Message): String = texts.resolve(consoleLanguage, message.text)

    private fun component(player: ServerPlayer?, message: Message): Component =
        if (player != null) components.forPlayer(message, language().forPlayer(player.clientInformation().language())) else components.forConsole(message, consoleLanguage)

    private fun notifyObservers(player: ServerPlayer?, message: Message) = observers.forEach { it.onMessage(player, message) }
}
