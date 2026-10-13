// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.tpa

import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.message.Choice
import fr.roumoulou.manymanycommands.rules.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** La demande reçue (`tpa.md` des specs, chapitres 2 et 5) : le demandeur, le sens du voyage, le temps pour répondre, les deux mots cliquables. */
class TpaMessagesTest {

    @Test
    fun `la demande reçue nomme le demandeur, dit le temps pour répondre, et porte ses deux mots cliquables, qui le nomment`() {
        val tpa = TpaMessages.received(RequestKind.TPA, "Steve", 60)
        val tpahere = TpaMessages.received(RequestKind.TPAHERE, "Steve", 90)

        val choices = listOf(Choice(TpaMessages.ACCEPT_WORD.with(), "/tpaaccept Steve"), Choice(TpaMessages.DENY_WORD.with(), "/tpadeny Steve"))
        assertEquals(TpaMessages.RECEIVED_TPA.with("Steve", DurationWording.text(60)).withChoices(*choices.toTypedArray()), tpa)
        assertEquals(TpaMessages.RECEIVED_TPAHERE.key, tpahere.key)
        assertEquals(listOf("Steve", DurationWording.text(90)), tpahere.text.arguments)
        assertEquals(choices, tpahere.choices)
        assertEquals(MessageType.DISPLAY, tpa.type)
    }
}
