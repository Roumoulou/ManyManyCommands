// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.teleport

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.Message
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.message.TranslatableText
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import fr.roumoulou.manymanycommands.rules.wild.WildRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Les étapes d'une téléportation avant son délai (cahier des charges, chapitres 2.5.1 et 2.5.6), et les refus du tableau du chapitre 2.5.8. */
class TeleportStepsTest {

    /** Le refus d'une famille, quel qu'il soit : c'est elle qui en donne le message. */
    private val ofFamily = Message(MessageType.REFUSAL, TranslatableText("manymanycommands.homes.absent", listOf("base")))

    private val dangerous = TeleportRefusal.DangerousArrival(ArrivalDanger.OBSTRUCTED)

    private fun firstRefusal(family: Message? = null, pending: Boolean = false, cooldown: Long = 0, arrival: TeleportRefusal? = null): TeleportRefusal? =
        TeleportSteps.firstRefusal({ family }, { pending }, { cooldown }, { arrival })

    @Test
    fun `quand aucune étape ne refuse, le délai d'immobilité peut s'écouler`() {
        assertNull(firstRefusal())
    }

    @Test
    fun `étape 1, le refus propre à la famille passe avant tous les autres`() {
        val refusal = firstRefusal(family = ofFamily, pending = true, cooldown = 30, arrival = dangerous)

        assertEquals(TeleportRefusal.OfFamily(ofFamily), refusal)
        assertEquals(ofFamily, refusal?.message)
    }

    @Test
    fun `étape 2, une téléportation déjà en attente refuse, avant le cooldown et l'arrivée`() {
        val refusal = firstRefusal(pending = true, cooldown = 30, arrival = dangerous)

        assertEquals(TeleportRefusal.AlreadyPending, refusal)
        assertEquals(CommonMessages.TELEPORT_PENDING.with(), refusal?.message)
    }

    @Test
    fun `étape 3, le cooldown en cours refuse en donnant le temps restant, avant l'arrivée`() {
        val refusal = firstRefusal(cooldown = 200, arrival = dangerous)

        assertEquals(TeleportRefusal.OnCooldown(200), refusal)
        assertEquals(CommonMessages.TELEPORT_COOLDOWN.with(DurationWording.text(200)), refusal?.message)
    }

    @Test
    fun `étape 4, une arrivée dangereuse refuse en nommant sa cause`() {
        val situations = mapOf(
            ArrivalDanger.OBSTRUCTED to CommonMessages.TELEPORT_ARRIVAL_OBSTRUCTED,
            ArrivalDanger.LAVA_OR_FIRE to CommonMessages.TELEPORT_ARRIVAL_LAVA_OR_FIRE,
            ArrivalDanger.VOID to CommonMessages.TELEPORT_ARRIVAL_VOID,
        )

        assertEquals(ArrivalDanger.entries.toSet(), situations.keys)
        situations.forEach { (danger, situation) ->
            val refusal = firstRefusal(arrival = TeleportRefusal.DangerousArrival(danger))

            assertEquals(TeleportRefusal.DangerousArrival(danger), refusal)
            assertEquals(situation.with(), refusal?.message)
        }
    }

    @Test
    fun `étape 4, la recherche d'un point sûr d'une famille refuse à la place du contrôle de l'arrivée`() {
        assertEquals(TeleportRefusal.OfFamily(ofFamily), firstRefusal(arrival = TeleportRefusal.OfFamily(ofFamily)))
    }

    @Test
    fun `une étape n'est consultée que si les précédentes passent`() {
        val consulted = mutableListOf<Int>()
        fun steps(family: Message?, pending: Boolean, cooldown: Long): List<Int> {
            consulted.clear()
            TeleportSteps.firstRefusal({ consulted += 1; family }, { consulted += 2; pending }, { consulted += 3; cooldown }, { consulted += 4; null })
            return consulted.toList()
        }

        assertEquals(listOf(1), steps(ofFamily, pending = true, cooldown = 30))
        assertEquals(listOf(1, 2), steps(null, pending = true, cooldown = 30))
        assertEquals(listOf(1, 2, 3), steps(null, pending = false, cooldown = 30))
        assertEquals(listOf(1, 2, 3, 4), steps(null, pending = false, cooldown = 0))
    }

    @Test
    fun `chaque refus du déroulé est un refus, envoyé au chat`() {
        val refusals = listOf(TeleportRefusal.AlreadyPending, TeleportRefusal.OnCooldown(5)) + ArrivalDanger.entries.map { TeleportRefusal.DangerousArrival(it) }

        assertEquals(setOf(MessageType.REFUSAL), refusals.map { it.message.type }.toSet())
    }

    // ── La dimension de départ (chapitre 2.5.6) ──

    @Test
    fun `une famille ne se lance pas depuis une dimension que le jeu de règles du joueur interdit au départ`() {
        val default = WildRules.shipped.default

        assertTrue(TeleportSteps.isDepartureForbidden(WildRules, default, "minecraft:the_nether"))
        assertTrue(TeleportSteps.isDepartureForbidden(WildRules, default, "minecraft:the_end"))
        assertFalse(TeleportSteps.isDepartureForbidden(WildRules, default, "minecraft:overworld"))
        assertFalse(TeleportSteps.isDepartureForbidden(WildRules, default, "mymod:moon"), "A dimension brought by a datapack is allowed as long as it is not listed")
    }

    @Test
    fun `chaque famille et chaque jeu tiennent leur liste de dimensions de départ`() {
        val homes = HomesRules.values(
            WrittenRules(sets = mapOf("default" to mapOf(HomesRules.forbiddenDepartureDimensions.writes(listOf("the_end"))), "vip" to mapOf(HomesRules.forbiddenDepartureDimensions.writes(emptyList())))),
        )

        assertTrue(TeleportSteps.isDepartureForbidden(HomesRules, homes.default, "minecraft:the_end"))
        assertFalse(TeleportSteps.isDepartureForbidden(HomesRules, homes.default, "minecraft:the_nether"), "The forbidden dimensions of /wild are not those of the homes")
        assertFalse(TeleportSteps.isDepartureForbidden(HomesRules, homes.sets.getValue("vip"), "minecraft:the_end"))
    }
}
