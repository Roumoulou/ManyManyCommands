// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Une horloge que le test tient : elle n'avance que quand il le demande. Le serveur GameTest tique sans cadence, et une seconde n'y vaut rien :
 * un délai, un cooldown ou une plage de quota s'y éprouvent en avançant l'heure à la main.
 */
class ManualClock(private var now: Instant = Instant.parse("2026-10-05T12:00:00Z")) : Clock() {

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}
