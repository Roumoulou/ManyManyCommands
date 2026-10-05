// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.file

import fr.roumoulou.manymanycommands.rules.message.TranslatableText

/**
 * Ce qu'un fichier refusé a de fautif (cahier des charges, chapitre 2.7.4).
 *
 * @property path le chemin de la valeur dans le fichier, `null` quand la faute ne tient pas à une valeur (un JSON mal formé)
 * @property line la ligne du fichier, `null` quand elle n'est pas connue
 */
data class FileFinding(val path: String?, val line: Int?, val text: TranslatableText)
