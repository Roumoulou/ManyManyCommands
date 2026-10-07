// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands

import fr.roumoulou.manymanycommands.commands.ModCommands
import fr.roumoulou.manymanycommands.game.teleport.Location
import fr.roumoulou.manymanycommands.rules.back.BackSource
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Point d'entrée du mod, sur un serveur dédié comme en solo : il branche les événements du jeu sur le [ModRuntime] du serveur.
 *
 * Le jeu construit ses commandes avant son serveur. Le `ModRuntime` s'ouvre donc à l'enregistrement des commandes qui précède un démarrage, et
 * se lâche à l'arrêt : en solo, chaque monde ouvert relit les fichiers. Le dossier du monde, lui, n'est connu qu'au démarrage du serveur : c'est
 * là que les fichiers de données s'ouvrent.
 */
class ManyManyCommands : ModInitializer {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

        /** L'état du mod pour le serveur en cours, ou pour celui qui démarre. `null` hors de la vie d'un serveur. */
        @Volatile
        var runtime: ModRuntime? = null
            private set

        @Volatile
        private var serverRunning = false
    }

    override fun onInitialize() {
        val version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().metadata.version.friendlyString
        LOGGER.info("ManyManyCommands {} loaded", version)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> ModCommands.register(dispatcher, runtimeForRegistration(version)) }
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            serverRunning = true
            runtime?.openWorld(server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID).normalize())
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            serverRunning = false
            runtime = null
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> runtime?.greet(handler.player) }

        // Les téléportations en attente : le tick fait s'écouler leur délai et annule celles dont le joueur a bougé, est mort ou parti.
        ServerTickEvents.END_SERVER_TICK.register { runtime?.teleports?.tick() }
        // Un coup paré par un bouclier ne blesse pas. Un coup mortel n'a pas d'après : la mort a le sien.
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, baseDamageTaken, _, blocked -> if (baseDamageTaken > 0f && !blocked) onPlayerHurt(entity) }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> if (entity is ServerPlayer) onPlayerDied(entity) }
    }

    private fun onPlayerHurt(entity: LivingEntity) {
        if (entity is ServerPlayer) runtime?.teleports?.onDamaged(entity)
    }

    /** La mort annule la téléportation en attente, et l'endroit de la mort devient la position de retour, si la source `death` du joueur l'enregistre. */
    private fun onPlayerDied(player: ServerPlayer) {
        val runtime = runtime ?: return
        runtime.teleports.onDamaged(player)
        runtime.recordReturn(player, BackSource.DEATH, Location.of(player))
    }

    /**
     * Avant un démarrage, un état neuf, qui lit les fichiers de réglages. Serveur en cours, le `/reload` du jeu reconstruit les commandes : elles
     * retrouvent l'état du serveur et ses réglages du démarrage.
     */
    private fun runtimeForRegistration(version: String): ModRuntime {
        runtime?.takeIf { serverRunning }?.let { return it }
        val configDirectory = FabricLoader.getInstance().configDir.resolve(MOD_ID)
        return ModRuntime.start(configDirectory, version).also { runtime = it }
    }
}
