// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.MOD_ID
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.gametest.framework.GameTestHelper

/** Le garde de l'étage 2 : il éprouve le harnais, un vrai serveur qui démarre avec le mod et joue un test. */
class ModLoadingGameTests {

    /** Le point d'entrée que `fabric.mod.json` déclare se résout sur le serveur, qui l'a appelé à son démarrage. */
    @GameTest
    fun modEntrypointResolvesOnTheServer(helper: GameTestHelper) {
        val entrypoints = FabricLoader.getInstance().getEntrypointContainers("main", ModInitializer::class.java)

        helper.assertTrue(entrypoints.any { it.provider.metadata.id == MOD_ID }, "le serveur ne porte aucun point d'entrée main du mod $MOD_ID")
        helper.succeed()
    }
}
