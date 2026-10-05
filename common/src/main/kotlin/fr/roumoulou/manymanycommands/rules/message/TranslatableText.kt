// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

/**
 * Un texte du mod sans sa formulation : une clé de traduction et ses arguments.
 *
 * Un argument est une valeur, affichée telle quelle, ou un autre [TranslatableText], traduit à son tour.
 */
data class TranslatableText(val key: String, val arguments: List<Any> = emptyList())
