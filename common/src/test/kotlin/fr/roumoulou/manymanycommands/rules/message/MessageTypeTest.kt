// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Le type d'un message, son emplacement et sa couleur (cahier des charges, chapitre 2.6.3). */
class MessageTypeTest {

    @Test
    fun `le refus va au chat, en rouge`() {
        assertEquals(Placement.CHAT, MessageType.REFUSAL.placement)
        assertEquals(MessageColor.RED, MessageType.REFUSAL.color)
    }

    @Test
    fun `le succès va au chat, en vert`() {
        assertEquals(Placement.CHAT, MessageType.SUCCESS.placement)
        assertEquals(MessageColor.GREEN, MessageType.SUCCESS.color)
    }

    @Test
    fun `le compte à rebours va à la barre d'action, en doré`() {
        assertEquals(Placement.ACTION_BAR, MessageType.COUNTDOWN.placement)
        assertEquals(MessageColor.GOLD, MessageType.COUNTDOWN.color)
    }

    @Test
    fun `l'affichage va au chat, en doré`() {
        assertEquals(Placement.CHAT, MessageType.DISPLAY.placement)
        assertEquals(MessageColor.GOLD, MessageType.DISPLAY.color)
    }
}
