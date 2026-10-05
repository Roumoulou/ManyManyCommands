// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

/** Ce qu'une situation envoie à un destinataire : un texte, et le type qui décide de son emplacement et de sa couleur. */
data class Message(val type: MessageType, val text: TranslatableText) {

    val key: String get() = text.key
}
