// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.message

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.homes.HomesMessages
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** La clé d'un texte et la situation qui la porte (cahier des charges, chapitre 2.6.5). */
class MessageFamilyTest {

    private val families = listOf(CommonMessages, ModMessages, HomesMessages)

    @Test
    fun `une clé s'écrit manymanycommands, la famille, puis la situation`() {
        assertEquals("manymanycommands.mod.reload.file_applied", ModMessages.RELOAD_FILE_APPLIED.key)
        assertEquals("manymanycommands.common.duration.pair", CommonMessages.DURATION_PAIR.key)
    }

    @Test
    fun `toutes les clés déclarées suivent ce format, en minuscules`() {
        val format = Regex("""manymanycommands\.[a-z]+(\.[a-z0-9_]+)+""")
        families.forEach { family ->
            assertTrue(family.keys.isNotEmpty(), "Family '${family.name}' declares no key")
            family.keys.forEach { key ->
                assertTrue(format.matches(key), "Key '$key' does not follow manymanycommands.<family>.<situation>")
                assertTrue(key.startsWith("manymanycommands.${family.name}."), "Key '$key' is not under its family '${family.name}'")
            }
        }
    }

    @Test
    fun `aucune clé n'est déclarée deux fois`() {
        val keys = families.flatMap { it.keys }
        assertEquals(keys.size, keys.toSet().size, "Duplicate keys: ${keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}")
    }

    @Test
    fun `une situation donne à son message sa clé, son type et ses arguments`() {
        val message = ModMessages.RELOAD_FILE_REFUSED.with("settings.json")

        assertEquals(ModMessages.RELOAD_FILE_REFUSED.key, message.key)
        assertEquals(MessageType.REFUSAL, message.type)
        assertEquals(listOf<Any>("settings.json"), message.text.arguments)
    }
}
