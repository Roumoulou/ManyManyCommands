// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le garde de l'étage 0 : il éprouve le harnais, qui joue la logique pure de `main` sans le jeu au classpath. */
class ModIdentityTest {

    @Test
    fun `l'identifiant du mod est un espace de noms valide`() {
        assertTrue(Regex("[a-z0-9_.-]+").matches(MOD_ID), "MOD_ID must be a valid identifier namespace, was '$MOD_ID'")
    }
}
