// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.commands

import com.mojang.brigadier.CommandDispatcher
import fr.roumoulou.manymanycommands.ModRuntime
import fr.roumoulou.manymanycommands.commands.back.BackCommand
import fr.roumoulou.manymanycommands.commands.homes.HomesCommand
import fr.roumoulou.manymanycommands.commands.mod.ManyManyCommandsCommand
import fr.roumoulou.manymanycommands.commands.warps.WarpsCommand
import fr.roumoulou.manymanycommands.commands.wild.WildCommand
import net.minecraft.commands.CommandSourceStack

/** L'enregistrement de toutes les commandes du mod. */
object ModCommands {

    /** Enregistre les commandes dans [dispatcher]. Chacune garde [runtime] : elle répond pour lui, où que le répartiteur soit joué. */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, runtime: ModRuntime) {
        ManyManyCommandsCommand(runtime).register(dispatcher)
        HomesCommand(runtime).register(dispatcher)
        WarpsCommand(runtime).register(dispatcher)
        BackCommand(runtime).register(dispatcher)
        WildCommand(runtime).register(dispatcher)
    }
}
