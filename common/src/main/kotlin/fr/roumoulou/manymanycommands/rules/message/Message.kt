// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

/**
 * Ce qu'une situation envoie à un destinataire : un texte, le type qui décide de son emplacement et de sa couleur, ce que le message propose
 * au joueur qui le lit, quand il propose quelque chose, et les mots cliquables qui suivent son texte, quand il en a.
 */
data class Message(val type: MessageType, val text: TranslatableText, val interaction: Interaction? = null, val choices: List<Choice> = emptyList()) {

    val key: String get() = text.key

    /** Le même message, qui se clique et se survole. */
    fun interactive(clickCommand: String? = null, hover: TranslatableText? = null): Message = copy(interaction = Interaction(clickCommand, hover))

    /** Le même message, suivi de [choices]. */
    fun withChoices(vararg choices: Choice): Message = copy(choices = choices.toList())
}

/**
 * Ce qu'un message propose au joueur qui le lit : la commande qu'un clic lance, le texte qu'un survol affiche. La console, où rien ne se clique ni
 * ne se survole, reçoit le texte du survol en clair à la suite du message.
 *
 * @property clickCommand la commande, avec sa barre oblique, ou `null`
 * @property hover le texte du survol, ou `null`
 */
data class Interaction(val clickCommand: String? = null, val hover: TranslatableText? = null)

/**
 * Un mot cliquable qui suit le texte d'un message : son libellé, traduit à son tour, et la commande qu'un clic lance, avec sa barre oblique.
 * La console reçoit le libellé et la commande en clair à la suite du message.
 */
data class Choice(val label: TranslatableText, val clickCommand: String)
