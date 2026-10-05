// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

import fr.roumoulou.manymanycommands.MOD_ID

/**
 * Les textes d'une fiche des specs. Chaque texte s'y déclare une fois, sous la clé `manymanycommands.<famille>.<nom>` : les fichiers de langue
 * doivent porter toutes les clés de [keys], et aucune autre.
 *
 * @param name le nom de la fiche : `homes`, `warps`, `mod` pour `manymanycommands.md`, `common` pour le cahier des charges
 */
abstract class MessageFamily(val name: String) {

    private val declaredKeys = mutableListOf<String>()

    val keys: List<String> get() = declaredKeys.toList()

    protected fun refusal(situation: String): Situation = Situation(declare(situation), MessageType.REFUSAL)

    protected fun success(situation: String): Situation = Situation(declare(situation), MessageType.SUCCESS)

    protected fun countdown(situation: String): Situation = Situation(declare(situation), MessageType.COUNTDOWN)

    protected fun display(situation: String): Situation = Situation(declare(situation), MessageType.DISPLAY)

    protected fun fragment(name: String): Fragment = Fragment(declare(name))

    private fun declare(name: String): String = "$MOD_ID.${this.name}.$name".also { declaredKeys += it }
}

/** Une situation du tableau des messages d'une fiche : sa clé et son type. Un test reconnaît un message à sa situation, jamais à sa formulation. */
class Situation(val key: String, val type: MessageType) {

    fun with(vararg arguments: Any): Message = Message(type, TranslatableText(key, arguments.toList()))
}

/** Un texte qui ne fait pas un message à lui seul : il entre dans un message comme argument. */
class Fragment(val key: String) {

    fun with(vararg arguments: Any): TranslatableText = TranslatableText(key, arguments.toList())
}
