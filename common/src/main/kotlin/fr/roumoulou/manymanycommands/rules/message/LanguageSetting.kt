// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

/** Le réglage `language` (cahier des charges, chapitre 2.6.1) : la langue du texte de repli d'un joueur, et celle de la console. */
enum class LanguageSetting(val value: String) {

    AUTO("auto"),

    FRENCH("fr"),

    ENGLISH("en");

    companion object {

        fun of(value: String): LanguageSetting? = entries.firstOrNull { it.value == value }
    }

    /**
     * La langue d'un joueur : celle que le réglage impose, ou sous `auto` celle de son client, le français pour `fr_*` et l'anglais sinon.
     *
     * @param clientLanguage la langue que le client du joueur annonce, comme `fr_fr` ou `en_us`
     */
    fun forPlayer(clientLanguage: String): Language = imposed() ?: if (clientLanguage.lowercase().startsWith("fr_")) Language.FRENCH else Language.ENGLISH

    /**
     * La langue de la console : celle que le réglage impose, ou sous `auto` celle de la machine, le français pour `fr` et l'anglais sinon.
     *
     * @param machineLanguage la langue de la machine, en code ISO 639 (`fr`, `en`)
     */
    fun forConsole(machineLanguage: String): Language = imposed() ?: if (machineLanguage.lowercase() == "fr") Language.FRENCH else Language.ENGLISH

    private fun imposed(): Language? = when (this) {
        AUTO -> null
        FRENCH -> Language.FRENCH
        ENGLISH -> Language.ENGLISH
    }
}
