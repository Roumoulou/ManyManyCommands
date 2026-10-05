// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

/**
 * L'identifiant Fabric du mod, sans un type du jeu ni du chargeur : l'étage 0 des tests le lit.
 *
 * Le build porte le même dans `mod_id` (`gradle.properties`), d'où `fabric.mod.json` le reçoit : l'étage 1 des tests confronte les deux.
 */
const val MOD_ID = "manymanycommands"
