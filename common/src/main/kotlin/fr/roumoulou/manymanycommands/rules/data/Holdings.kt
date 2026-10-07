// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

/**
 * Ce qu'une famille tient d'un joueur dans son fichier de données (cahier des charges, chapitre 2.7.5), hors son nom et sa plage de quota :
 * ses homes, la réception fermée de ses demandes.
 */
interface Holdings {

    /** Rien n'est tenu : avec un nom seul, l'entrée du joueur ne s'écrit pas. */
    val isEmpty: Boolean
}

/** Ce que tient une famille dont le fichier de données ne porte que les plages de quota : `back.json`, `wild.json`. */
object NoHoldings : Holdings {

    override val isEmpty: Boolean get() = true
}
