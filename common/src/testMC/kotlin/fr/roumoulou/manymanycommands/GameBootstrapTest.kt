// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le garde de l'étage 1 : il éprouve le harnais, pas une règle du mod.
 *
 * `fabric-loader-junit` installe le chargeur Fabric avant les tests : les types et les registres du jeu répondent sans serveur, et le chargeur a
 * lu le `fabric.mod.json` que le build a rempli. Le jour où une montée de version casse cet amorçage, c'est ce fichier qui le dira.
 */
class GameBootstrapTest {

    @Test
    fun `le jeu s'amorce et le chargeur voit le mod`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()

        assertEquals("minecraft:stone", BuiltInRegistries.BLOCK.getKey(Blocks.STONE).toString())
        assertTrue(FabricLoader.getInstance().isModLoaded(MOD_ID), "Fabric Loader must see a mod under '$MOD_ID', the mod_id of gradle.properties")
    }
}
