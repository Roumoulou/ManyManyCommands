// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.game.message

import fr.roumoulou.manymanycommands.GameBootstrap
import fr.roumoulou.manymanycommands.files.message.TextCatalog
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Language
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.TranslatableContents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** La clé et son repli, la console, la couleur du type (cahier des charges, chapitres 2.6.3 et 2.6.5). */
class MessageComponentsTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun bootstrapTheGame() = GameBootstrap.ensure()
    }

    private val texts = TextCatalog.load()

    private val components = MessageComponents(texts)

    private val applied = ModMessages.RELOAD_FILE_APPLIED.with("settings.json")

    private fun translatable(component: Component): TranslatableContents {
        val contents = component.contents
        assertTrue(contents is TranslatableContents, "A player receives a translation key, was ${contents::class.simpleName}")
        return contents as TranslatableContents
    }

    @Test
    fun `un joueur reçoit la clé, le repli dans sa langue et les arguments`() {
        val contents = translatable(components.forPlayer(applied, Language.FRENCH))

        assertEquals(ModMessages.RELOAD_FILE_APPLIED.key, contents.key)
        assertEquals("%s : appliqué.", contents.fallback)
        assertEquals(listOf<Any>("settings.json"), contents.args.toList())
    }

    @Test
    fun `le repli suit la langue du destinataire, la clé reste la même`() {
        val french = translatable(components.forPlayer(applied, Language.FRENCH))
        val english = translatable(components.forPlayer(applied, Language.ENGLISH))

        assertEquals(french.key, english.key)
        assertEquals(texts.pattern(Language.FRENCH, applied.key), french.fallback)
        assertEquals(texts.pattern(Language.ENGLISH, applied.key), english.fallback)
        assertFalse(french.fallback == english.fallback, "The two languages must not share this text")
    }

    @Test
    fun `un texte imbriqué voyage en clé, avec son propre repli`() {
        val message = Message(MessageType.COUNTDOWN, ModMessages.RELOAD_RESTART_REQUIRED.with(DurationWording.text(200)).text)

        val duration = translatable(translatable(components.forPlayer(message, Language.FRENCH)).args.single() as Component)
        val minutes = translatable(duration.args[0] as Component)

        assertEquals(CommonMessages.DURATION_PAIR.key, duration.key)
        assertEquals(CommonMessages.DURATION_MINUTES_MANY.key, minutes.key)
        assertEquals("%s minutes", minutes.fallback)
        assertEquals(listOf<Any>("3"), minutes.args.toList())
    }

    @Test
    fun `la console reçoit le texte que le mod résout, sans clé`() {
        val french = components.forConsole(applied, Language.FRENCH)
        val english = components.forConsole(applied, Language.ENGLISH)

        assertFalse(french.contents is TranslatableContents, "The console receives a resolved text, not a key")
        assertEquals("settings.json : appliqué.", french.string)
        assertEquals("settings.json: applied.", english.string)
    }

    @Test
    fun `le composant porte la couleur de son type, pour un joueur comme pour la console`() {
        val expected = mapOf(MessageType.REFUSAL to ChatFormatting.RED, MessageType.SUCCESS to ChatFormatting.GREEN, MessageType.COUNTDOWN to ChatFormatting.GOLD, MessageType.DISPLAY to ChatFormatting.GOLD)

        expected.forEach { (type, formatting) ->
            val message = Message(type, applied.text)
            assertEquals(TextColor.fromLegacyFormat(formatting), components.forPlayer(message, Language.FRENCH).style.color, "Player color of $type")
            assertEquals(TextColor.fromLegacyFormat(formatting), components.forConsole(message, Language.FRENCH).style.color, "Console color of $type")
        }
    }
}
