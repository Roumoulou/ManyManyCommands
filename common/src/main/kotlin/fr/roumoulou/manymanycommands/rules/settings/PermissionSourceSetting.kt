// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.settings

/** Le réglage `permission-source` : d'où viennent les droits des joueurs (cahier des charges, chapitre 2.4.5). */
enum class PermissionSourceSetting(val value: String) {

    INTERNAL("internal"),

    EXTERNAL("external");

    companion object {

        fun of(value: String): PermissionSourceSetting? = entries.firstOrNull { it.value == value }
    }
}
