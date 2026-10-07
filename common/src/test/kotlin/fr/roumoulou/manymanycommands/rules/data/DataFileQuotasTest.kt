// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.data

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.DataFile
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FileReading
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaClock
import fr.roumoulou.manymanycommands.rules.teleport.QuotaUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/** Les plages de quota conservées dans le fichier de données d'une famille (cahier des charges, chapitre 2.5.9). */
class DataFileQuotasTest {

    /** Ce qu'une famille d'essai tient d'un joueur : des marques. */
    private data class Marks(val marks: Set<String>) : Holdings {

        override val isEmpty: Boolean get() = marks.isEmpty()
    }

    /** Le contenu d'une famille d'essai qui range ses joueurs à côté d'autre chose, comme `warps.json`. */
    private data class Bag(val label: String, val players: FamilyData<Marks>)

    private val steve = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")

    private val window = QuotaUsage(QuotaClock.REAL, windowStart = 1_759_665_600, uses = 1)

    private val written = mutableListOf<FamilyData<Marks>>()

    /** Le fichier d'une famille d'essai, dont la lecture rend [reading] et dont chaque écriture va dans [written]. */
    private fun file(reading: FileReading<FamilyData<Marks>>): DataFile<FamilyData<Marks>> = DataFile("world/marks.json", read = { reading }, write = { written += it }).also { it.load() }

    @Test
    fun `un joueur sans plage n'en a pas, et son usage ouvre son entrée, sous son nom, avec ce que la famille ne tient pas encore de lui`() {
        val quotas = DataFileQuotas.ofFamily(file(FileReading.Applied(FamilyData())), empty = Marks(emptySet()))

        assertNull(quotas.usage(steve))
        quotas.record(steve, "Steve", window)

        assertEquals(window, quotas.usage(steve))
        assertEquals(listOf(FamilyData(mapOf(steve to PlayerEntry("Steve", Marks(emptySet()), window)))), written, "The window is written at once")
    }

    @Test
    fun `l'usage garde ce que la famille tient du joueur, et récrit son nom du moment`() {
        val data = FamilyData(mapOf(steve to PlayerEntry("Steve", Marks(setOf("base")), window)))
        val quotas = DataFileQuotas.ofFamily(file(FileReading.Applied(data)), empty = Marks(emptySet()))

        quotas.record(steve, "Steve2", window.copy(uses = 2))

        assertEquals(PlayerEntry("Steve2", Marks(setOf("base")), window.copy(uses = 2)), written.single()[steve])
    }

    @Test
    fun `sur un fichier indisponible, rien ne se lit ni ne s'écrit`() {
        val refused = FileReading.Refused(listOf(FileFinding("players[Steve]", 3, CommonMessages.FINDING_UNKNOWN_KEY.with())))
        val quotas = DataFileQuotas.ofFamily(file(refused), empty = Marks(emptySet()))

        assertNull(quotas.usage(steve))
        quotas.record(steve, "Steve", window)

        assertEquals(emptyList<Any>(), written)
    }

    @Test
    fun `une famille qui range ses joueurs à côté d'autre chose donne ses deux accès, et l'usage garde le reste du contenu`() {
        val bags = mutableListOf<Bag>()
        val file = DataFile("world/bags.json", read = { FileReading.Applied(Bag("kept", FamilyData())) }, write = { bags += it }).also { it.load() }
        val quotas = DataFileQuotas(file, { bag, player -> bag.players[player]?.quota }, { bag, player, name, usage -> bag.copy(players = bag.players.withQuota(player, name, usage, Marks(emptySet()))) })

        assertNull(quotas.usage(steve))
        quotas.record(steve, "Steve", window)

        assertEquals(window, quotas.usage(steve))
        assertEquals(listOf(Bag("kept", FamilyData(mapOf(steve to PlayerEntry("Steve", Marks(emptySet()), window))))), bags)
    }
}
