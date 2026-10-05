// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

/** L'amorçage du jeu pour les tests de l'étage 1 qui nomment un type du jeu. Le jeu ne s'amorce qu'une fois, les appels suivants ne font rien. */
object GameBootstrap {

    fun ensure() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }
}
