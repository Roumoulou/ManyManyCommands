// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.message

import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageColor
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

/** Le composant de texte d'un message, selon qui le reçoit (cahier des charges, chapitres 2.6.3 et 2.6.5). */
class MessageComponents(private val texts: TextCatalog) {

    /**
     * Pour un joueur : la clé de traduction, le texte de repli dans [language] et les arguments. Son client affiche la traduction de la clé s'il
     * la connaît, par un resourcepack du serveur ou parce qu'il porte le mod, et le repli sinon. Un message qui se clique ou se survole porte sa
     * commande et son texte de survol dans son style. Chaque mot cliquable suit le texte, séparé d'une espace, avec sa commande dans le sien.
     */
    fun forPlayer(message: Message, language: Language): MutableComponent {
        val component = translatable(message.text, language).withStyle(formatting(message.type.color))
        message.interaction?.let { interaction ->
            component.withStyle { style: Style ->
                val clickable = interaction.clickCommand?.let { style.withClickEvent(ClickEvent.RunCommand(it)) } ?: style
                interaction.hover?.let { clickable.withHoverEvent(HoverEvent.ShowText(translatable(it, language))) } ?: clickable
            }
        }
        message.choices.forEach { choice ->
            component.append(" ")
            component.append(translatable(choice.label, language).withStyle { style: Style -> style.withClickEvent(ClickEvent.RunCommand(choice.clickCommand)) })
        }
        return component
    }

    /** Pour la console : le texte que le mod résout lui-même, dans [language]. Ce qu'un joueur lirait au survol la suit, en clair, puis chaque mot cliquable avec sa commande. */
    fun forConsole(message: Message, language: Language): MutableComponent {
        val hover = message.interaction?.hover
        val text = if (hover == null) message.text else CommonMessages.CONSOLE_DETAIL.with(message.text, hover)
        val choices = message.choices.joinToString("") { " " + texts.resolve(language, CommonMessages.CONSOLE_CHOICE.with(it.label, it.clickCommand)) }
        return Component.literal(texts.resolve(language, text) + choices).withStyle(formatting(message.type.color))
    }

    private fun translatable(text: TranslatableText, language: Language): MutableComponent {
        val arguments = text.arguments.map { if (it is TranslatableText) translatable(it, language) else it.toString() }
        return Component.translatableWithFallback(text.key, texts.pattern(language, text.key), *arguments.toTypedArray())
    }

    private fun formatting(color: MessageColor): ChatFormatting = when (color) {
        MessageColor.RED -> ChatFormatting.RED
        MessageColor.GREEN -> ChatFormatting.GREEN
        MessageColor.GOLD -> ChatFormatting.GOLD
    }
}
