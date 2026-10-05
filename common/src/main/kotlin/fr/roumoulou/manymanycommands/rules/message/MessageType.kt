// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

/** Le type d'un message, qui décide de son emplacement et de sa couleur. */
enum class MessageType(val placement: Placement, val color: MessageColor) {

    REFUSAL(Placement.CHAT, MessageColor.RED),

    SUCCESS(Placement.CHAT, MessageColor.GREEN),

    COUNTDOWN(Placement.ACTION_BAR, MessageColor.GOLD),

    /** Une liste, une demande reçue, un état. */
    DISPLAY(Placement.CHAT, MessageColor.GOLD),
}

enum class Placement { CHAT, ACTION_BAR }

enum class MessageColor { RED, GREEN, GOLD }
