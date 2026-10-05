// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.message

import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageColor
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/** Le composant de texte d'un message, selon qui le reçoit (cahier des charges, chapitres 2.6.3 et 2.6.5). */
class MessageComponents(private val texts: TextCatalog) {

    /**
     * Pour un joueur : la clé de traduction, le texte de repli dans [language] et les arguments. Son client affiche la traduction de la clé s'il
     * la connaît, par un resourcepack du serveur ou parce qu'il porte le mod, et le repli sinon.
     */
    fun forPlayer(message: Message, language: Language): MutableComponent = translatable(message.text, language).withStyle(formatting(message.type.color))

    /** Pour la console : le texte que le mod résout lui-même, dans [language]. */
    fun forConsole(message: Message, language: Language): MutableComponent = Component.literal(texts.resolve(language, message.text)).withStyle(formatting(message.type.color))

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
