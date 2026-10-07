// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.gametest

import fr.roumoulou.manymanycommands.game.teleport.Arrival
import fr.roumoulou.manymanycommands.game.teleport.SearchOutcome
import fr.roumoulou.manymanycommands.gametest.Harness.Lab
import fr.roumoulou.manymanycommands.gametest.TeleportStage.Outcome
import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.DurationWording
import fr.roumoulou.manymanycommands.rules.homes.HomesRules
import fr.roumoulou.manymanycommands.rules.message.MessageType
import fr.roumoulou.manymanycommands.rules.mod.ModMessages
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenQuota
import fr.roumoulou.manymanycommands.rules.teleport.ArrivalDanger
import fr.roumoulou.manymanycommands.rules.teleport.BypassNodes
import fr.roumoulou.manymanycommands.rules.teleport.TeleportRefusal
import fr.roumoulou.manymanycommands.rules.warps.WarpsRules
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.stats.Stats
import net.minecraft.world.entity.Leashable
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Le déroulé d'une téléportation joué dans le jeu, pour un joueur simulé (cahier des charges, chapitre 2.5) : c'est le test qui fait la demande
 * d'une famille, pour éprouver le moteur sans une commande. Chaque test ouvre son état du mod, sur une horloge qu'il tient, et fait tiquer
 * lui-même le moteur : le temps n'y passe que quand il le dit.
 */
class TeleportGameTests {

    private fun countdown(seconds: Long) = CommonMessages.TELEPORT_COUNTDOWN.with(DurationWording.text(seconds))

    /**
     * Une téléportation menée à son terme : le délai d'immobilité s'écoule, son compte à rebours s'affiche chaque seconde dans la barre d'action,
     * puis le joueur est téléporté au point exact, regard compris, la famille l'apprend, et le cooldown de la famille démarre.
     */
    @GameTest
    fun teleportRunsToItsEndAfterTheDelay(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("teleport-runs-to-its-end-after-the-delay", clock = clock)
        val engine = lab.runtime.teleports
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5).copy(yaw = 90f, pitch = 10f)
        val outcome = Outcome()

        val shown = Harness.record(lab.runtime) {
            helper.assertTrue(engine.launch(stage.request(destination, listener = outcome)) == null, "le lancement n'est pas refusé")
            helper.assertTrue(engine.hasPending(stage.player), "la téléportation est en attente")
            engine.tick()
            repeat(2) {
                clock.advance(1)
                engine.tick()
                engine.tick()
            }
            stage.assertPlayerAt(stage.departure, "tant que le délai court")
            helper.assertTrue(outcome.arrival == null, "la famille n'apprend aucune arrivée tant que le délai court")
        }
        helper.assertValueEqual(shown, listOf(3L, 2L, 1L).map { Delivery(stage.player, countdown(it)) }, "le compte à rebours, une fois par seconde")
        helper.assertValueEqual(shown.map { it.message.type }.toSet(), setOf(MessageType.COUNTDOWN), "le compte à rebours va dans la barre d'action")

        clock.advance(1)
        val atArrival = Harness.record(lab.runtime) { engine.tick() }

        helper.assertValueEqual(atArrival, emptyList(), "le déroulé n'envoie rien à l'arrivée : le message d'arrivée est celui de la famille")
        stage.assertPlayerAt(destination, "le délai écoulé")
        helper.assertValueEqual(stage.player.yRot to stage.player.xRot, 90f to 10f, "l'orientation du regard à l'arrivée")
        helper.assertFalse(engine.hasPending(stage.player), "la téléportation n'est plus en attente")
        val (from, to) = outcome.arrival ?: throw helper.assertionException("la famille n'a pas appris l'arrivée")
        helper.assertValueEqual(from, stage.departure, "l'endroit quitté, que la famille reçoit")
        helper.assertValueEqual(to, destination, "l'arrivée, que la famille reçoit")

        // Le cooldown de la famille a démarré à l'arrivée, pour sa durée entière.
        stage.place(stage.player, stage.departure)
        stage.assertRefused(engine.launch(stage.request(destination)), TeleportRefusal.OnCooldown(10), "le cooldown démarre à l'arrivée")
        helper.succeed()
    }

    /** Un délai de 0 téléporte tout de suite, sans compte à rebours. */
    @GameTest
    fun delayOfZeroTeleportsAtOnce(helper: GameTestHelper) {
        val lab = Lab.open("delay-of-zero-teleports-at-once", clock = ManualClock())
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)

        val messages = Harness.record(lab.runtime) {
            helper.assertTrue(lab.runtime.teleports.launch(stage.request(destination, TeleportStage.rules(delay = 0))) == null, "le lancement n'est pas refusé")
        }

        stage.assertPlayerAt(destination, "dès le lancement")
        helper.assertFalse(lab.runtime.teleports.hasPending(stage.player), "aucune téléportation ne reste en attente")
        helper.assertValueEqual(messages, emptyList(), "aucun compte à rebours pour un délai de 0")
        helper.succeed()
    }

    /**
     * Les dispenses : le maître du jeu reçoit par défaut les nœuds `bypass`, et n'a ni délai ni cooldown. La source des droits accorde la dispense
     * du délai à un joueur, et retire celle du cooldown à un maître du jeu.
     */
    @GameTest
    fun bypassNodesWaiveTheDelayAndTheCooldown(helper: GameTestHelper) {
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)
        var cooldownWithdrawn = false
        val lab = Lab.open("bypass-nodes-waive-the-delay-and-the-cooldown", clock = ManualClock(), permissionSource = { _, node ->
            when (node) {
                BypassNodes.COOLDOWN -> if (cooldownWithdrawn) false else null
                else -> null
            }
        })
        val engine = lab.runtime.teleports
        val gameMaster = Harness.asGameMaster(stage.player)

        helper.assertTrue(engine.launch(stage.request(destination, source = gameMaster)) == null, "le maître du jeu n'est pas refusé")
        stage.assertPlayerAt(destination, "le maître du jeu, dispensé du délai, arrive tout de suite")

        stage.place(stage.player, stage.departure)
        helper.assertTrue(engine.launch(stage.request(destination, source = gameMaster)) == null, "le maître du jeu, dispensé du cooldown, relance tout de suite")
        stage.assertPlayerAt(destination, "le maître du jeu, de nouveau")

        stage.place(stage.player, stage.departure)
        cooldownWithdrawn = true
        stage.assertRefused(engine.launch(stage.request(destination, source = gameMaster)), TeleportRefusal.OnCooldown(10), "la source des droits lui retire la dispense du cooldown")
        helper.assertValueEqual(engine.dispensations(stage.player.createCommandSourceStack()).fromDelay, false, "un joueur n'est dispensé de rien par défaut")
        helper.succeed()
    }

    /** La source des droits accorde la dispense du délai à un joueur : il arrive tout de suite, et son cooldown, dont il n'est pas dispensé, démarre. */
    @GameTest
    fun grantedBypassNodeWaivesTheDelayOfAPlayer(helper: GameTestHelper) {
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)
        val lab = Lab.open("granted-bypass-node-waives-the-delay-of-a-player", clock = ManualClock(), permissionSource = { source, node -> if (source.player === stage.player && node == BypassNodes.DELAY) true else null })
        val engine = lab.runtime.teleports

        helper.assertTrue(engine.launch(stage.request(destination)) == null, "le joueur n'est pas refusé")
        stage.assertPlayerAt(destination, "le joueur dispensé du délai arrive tout de suite")

        stage.place(stage.player, stage.departure)
        stage.assertRefused(engine.launch(stage.request(destination)), TeleportRefusal.OnCooldown(10), "son cooldown court")
        helper.succeed()
    }

    /** La téléportation mène d'une dimension à l'autre : le joueur de l'Overworld arrive dans le Nether, au point exact. */
    @GameTest
    fun teleportCrossesDimensions(helper: GameTestHelper) {
        val lab = Lab.open("teleport-crosses-dimensions", clock = ManualClock())
        val stage = TeleportStage(helper)
        val destination = stage.netherPlatform()
        val outcome = Outcome()

        helper.assertTrue(lab.runtime.teleports.launch(stage.request(destination, TeleportStage.rules(delay = 0), listener = outcome)) == null, "le lancement n'est pas refusé")

        helper.assertValueEqual(stage.player.level().dimension(), Level.NETHER, "la dimension du joueur à l'arrivée")
        stage.assertPlayerAt(destination, "dans le Nether")
        helper.assertValueEqual(outcome.arrival?.first?.dimension ?: "", "minecraft:overworld", "la dimension quittée, que la famille reçoit")
        helper.succeed()
    }

    /**
     * Le joueur est téléporté seul : il quitte sa monture, et l'animal qu'il tient en laisse reste sur place, sa laisse tombée à ses pieds,
     * alors même que l'arrivée est à portée de laisse. Le passager ne s'éprouve pas : le jeu refuse à toute entité de monter sur un joueur.
     */
    @GameTest
    fun playerTravelsAlone(helper: GameTestHelper) {
        val lab = Lab.open("player-travels-alone", clock = ManualClock())
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)
        val mount = stage.spawn("minecraft:pig", 1, TeleportStage.FLOOR + 1, 1)
        val leashed = stage.spawn("minecraft:cow", 2, TeleportStage.FLOOR + 1, 2)
        helper.assertTrue(stage.player.startRiding(mount, true, false), "le joueur monte le cochon")
        val leash = leashed as? Leashable ?: throw helper.assertionException("la vache ne se tient pas en laisse")
        leash.setLeashedTo(stage.player, true)
        helper.assertTrue(leash.leashHolder === stage.player, "le joueur tient la vache en laisse")

        helper.assertTrue(lab.runtime.teleports.launch(stage.request(destination, TeleportStage.rules(delay = 0))) == null, "le lancement n'est pas refusé")

        val departure = Vec3(stage.departure.x, stage.departure.y, stage.departure.z)
        stage.assertPlayerAt(destination, "à cinq blocs de son départ, à portée de laisse")
        helper.assertTrue(stage.player.vehicle == null, "le joueur a quitté sa monture")
        helper.assertTrue(leash.leashHolder == null, "le joueur ne tient plus la vache en laisse")
        val dropped = helper.level.getEntitiesOfClass(ItemEntity::class.java, leashed.boundingBox.inflate(2.0)).filter { it.item.`is`(Items.LEAD) }
        helper.assertValueEqual(dropped.size, 1, "la laisse tombée aux pieds de la vache")
        listOf("la monture" to mount, "l'animal tenu en laisse" to leashed).forEach { (what, entity) ->
            helper.assertTrue(entity.isAlive && entity.vehicle == null, "$what reste dans le monde, sans monture")
            helper.assertTrue(entity.position().distanceTo(departure) < 3.0, "$what reste au départ")
        }
        helper.succeed()
    }

    /**
     * Les refus des étapes 1 à 4, dans leur ordre, chacun répondu au joueur en refus : le refus propre à la famille, la téléportation déjà en
     * attente quelle que soit la famille, le cooldown de la famille, qui ne retarde pas une autre famille, puis l'arrivée dangereuse.
     */
    @GameTest
    fun refusalsFollowTheOrderOfTheSteps(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("refusals-follow-the-order-of-the-steps", clock = clock)
        val engine = lab.runtime.teleports
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)
        val overTheVoid = stage.standing(5, 7, 1)
        // Le refus d'une famille, quel qu'il soit : aucune n'a encore les siens.
        val ofFamily = ModMessages.STARTUP_INVALID_SETTINGS.with()

        fun refused(expected: TeleportRefusal, what: String, launch: () -> TeleportRefusal?) {
            val replies = Harness.record(lab.runtime) { helper.assertValueEqual(launch() ?: throw helper.assertionException("le lancement devait être refusé : $what"), expected, what) }
            helper.assertValueEqual(replies, listOf(Delivery(stage.player, expected.message)), "le refus répondu au joueur : $what")
            helper.assertValueEqual(expected.message.type, MessageType.REFUSAL, "le type du message : $what")
        }

        // Étape 2 : une téléportation est en attente, celle d'un home.
        helper.assertTrue(engine.launch(stage.request(destination)) == null, "le premier lancement n'est pas refusé")
        refused(TeleportRefusal.OfFamily(ofFamily), "étape 1, le refus de la famille passe avant la téléportation en attente") { engine.launch(stage.request(destination, familyRefusal = { ofFamily })) }
        refused(TeleportRefusal.AlreadyPending, "étape 2, une téléportation est déjà en attente") { engine.launch(stage.request(destination)) }
        refused(TeleportRefusal.AlreadyPending, "étape 2, quelle que soit la famille") { engine.launch(stage.request(destination, TeleportStage.rules(WarpsRules), table = WarpsRules)) }

        // Étape 3 : le joueur arrive, le cooldown des homes court.
        clock.advance(3)
        engine.tick()
        stage.assertPlayerAt(destination, "le délai écoulé")
        stage.place(stage.player, stage.departure)
        clock.advance(4)
        refused(TeleportRefusal.OnCooldown(6), "étape 3, le cooldown en cours, avec le temps restant") { engine.launch(stage.request(destination)) }
        refused(TeleportRefusal.OnCooldown(6), "étape 3, le cooldown passe avant l'arrivée dangereuse") { engine.launch(stage.request(overTheVoid)) }

        // Étape 4, et le cooldown de chaque famille : celui des homes ne retarde pas un warp.
        refused(TeleportRefusal.DangerousArrival(ArrivalDanger.VOID), "étape 4, l'arrivée dangereuse") { engine.launch(stage.request(overTheVoid, TeleportStage.rules(WarpsRules), table = WarpsRules)) }
        helper.assertTrue(engine.launch(stage.request(destination, TeleportStage.rules(WarpsRules), table = WarpsRules)) == null, "le cooldown des homes ne retarde pas un warp")

        // Le cooldown écoulé, le home se rejoint de nouveau, une fois le warp en attente annulé.
        clock.advance(6)
        refused(TeleportRefusal.AlreadyPending, "le warp est en attente") { engine.launch(stage.request(destination)) }
        helper.succeed()
    }

    /**
     * Le quota se compte à l'arrivée, pour le joueur que la demande désigne : une téléportation annulée ne consomme rien, le quota atteint dit
     * dans combien de temps le prochain usage sera possible, et la plage finie en rouvre une. Le maître du jeu en est dispensé par défaut.
     */
    @GameTest
    fun quotaIsCountedAtTheArrival(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("quota-is-counted-at-the-arrival", clock = clock)
        val engine = lab.runtime.teleports
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)
        val rules = TeleportStage.rules(cooldown = 0, quota = WrittenQuota(uses = 1, period = 60, clock = null))
        val source = stage.player.createCommandSourceStack()
        val charge = engine.quotaCharge(source, stage.player, HomesRules, rules) ?: throw helper.assertionException("le jeu de règles a un quota, que le joueur doit compter")

        helper.assertTrue(engine.quotaCharge(Harness.asGameMaster(stage.player), stage.player, HomesRules, rules) == null, "le maître du jeu est dispensé des quotas")
        helper.assertTrue(engine.quotaCharge(source, stage.player, HomesRules, TeleportStage.rules()) == null, "un jeu sans quota ne compte rien")

        // Une téléportation annulée ne consomme rien.
        helper.assertTrue(engine.launch(stage.request(destination, rules, quotaCharge = charge)) == null, "le premier lancement n'est pas refusé")
        stage.place(stage.player, stage.standing(1, TeleportStage.FLOOR + 1, 3))
        engine.tick()
        helper.assertFalse(engine.hasPending(stage.player), "la téléportation est annulée")
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 0L, "l'attente après une téléportation annulée")

        // L'usage se compte à l'arrivée, et ouvre la plage.
        stage.place(stage.player, stage.departure)
        helper.assertTrue(engine.launch(stage.request(destination, rules, quotaCharge = charge)) == null, "le second lancement n'est pas refusé")
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 0L, "l'attente pendant le délai, avant l'arrivée")
        clock.advance(3)
        engine.tick()
        stage.assertPlayerAt(destination, "le délai écoulé")
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 60L, "l'attente une fois le quota atteint")
        clock.advance(45)
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 15L, "l'attente, 45 secondes plus tard")
        clock.advance(15)
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 0L, "l'attente une fois la plage finie")
        helper.succeed()
    }

    /** Sur le temps de jeu, la plage d'un quota se mesure à la statistique que le jeu tient pour le joueur, et non à l'heure. */
    @GameTest
    fun playtimeQuotaFollowsThePlayTimeOfThePlayer(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("playtime-quota-follows-the-play-time-of-the-player", clock = clock)
        val engine = lab.runtime.teleports
        val stage = TeleportStage(helper)
        val rules = TeleportStage.rules(delay = 0, cooldown = 0, quota = WrittenQuota(uses = 1, period = 600, clock = "playtime"))
        val charge = engine.quotaCharge(stage.player.createCommandSourceStack(), stage.player, HomesRules, rules) ?: throw helper.assertionException("le jeu de règles a un quota")
        val playTime = Stats.CUSTOM.get(Stats.PLAY_TIME)
        fun played(seconds: Int) = stage.player.stats.setValue(stage.player, playTime, seconds * 20)

        played(seconds = 7_200)
        helper.assertTrue(engine.launch(stage.request(stage.platform(5, 5), rules, quotaCharge = charge)) == null, "le lancement n'est pas refusé")
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 600L, "l'attente une fois le quota atteint, en temps de jeu")

        clock.advance(86_400)
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 600L, "l'heure qui passe sans que le joueur joue ne compte pas")
        played(seconds = 7_200 + 240)
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 360L, "l'attente après 4 minutes de jeu")
        played(seconds = 7_200 + 600)
        helper.assertValueEqual(engine.quotaWaitSeconds(charge), 0L, "l'attente après 10 minutes de jeu")
        helper.succeed()
    }

    /**
     * La recherche d'un point d'arrivée par une famille tient lieu de contrôle de l'arrivée : le point qu'elle rend n'est pas contrôlé, son
     * refus est répondu au joueur sans démarrer le cooldown, et elle ne se lance pas pour une commande que le cooldown refuse.
     */
    @GameTest
    fun familySearchStandsForTheArrivalCheck(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("family-search-stands-for-the-arrival-check", clock = clock)
        val engine = lab.runtime.teleports
        val stage = TeleportStage(helper)
        val overTheVoid = stage.standing(5, 7, 1)
        val rules = TeleportStage.rules(delay = 0)
        val noPoint = ModMessages.STARTUP_INVALID_SETTINGS.with()
        var searches = 0
        fun searching(outcome: SearchOutcome) = stage.request(overTheVoid, rules, arrival = Arrival.Searched { outcome.also { searches++ } })

        val replies = Harness.record(lab.runtime) {
            stage.assertRefused(engine.launch(searching(SearchOutcome.Refused(noPoint))), TeleportRefusal.OfFamily(noPoint), "le refus de la recherche")
        }
        helper.assertValueEqual(replies, listOf(Delivery(stage.player, noPoint)), "le refus de la recherche, répondu au joueur")
        stage.assertPlayerAt(stage.departure, "la recherche refusée")

        helper.assertTrue(engine.launch(searching(SearchOutcome.Found(overTheVoid))) == null, "un refus ne démarre pas le cooldown, et le point trouvé n'est pas contrôlé")
        stage.assertPlayerAt(overTheVoid, "au point trouvé")
        helper.assertValueEqual(searches, 2, "les recherches lancées")

        stage.place(stage.player, stage.departure)
        stage.assertRefused(engine.launch(searching(SearchOutcome.Found(overTheVoid))), TeleportRefusal.OnCooldown(10), "le cooldown court depuis l'arrivée")
        helper.assertValueEqual(searches, 2, "aucune recherche pour une commande que le cooldown refuse")
        helper.succeed()
    }

    /**
     * Une téléportation en attente va à son terme avec le délai de son lancement, que le rechargement ne change pas. Le cooldown, lui, se mesure
     * aux règles en vigueur.
     */
    @GameTest
    fun pendingTeleportKeepsTheDelayOfItsLaunch(helper: GameTestHelper) {
        val clock = ManualClock()
        val lab = Lab.open("pending-teleport-keeps-the-delay-of-its-launch", clock = clock)
        val engine = lab.runtime.teleports
        val stage = TeleportStage(helper)
        val destination = stage.platform(5, 5)
        val source = stage.player.createCommandSourceStack()
        fun homes() = lab.runtime.ruleSet(source, HomesRules)

        helper.assertTrue(engine.launch(stage.request(destination, homes())) == null, "le lancement n'est pas refusé")
        lab.writeRules(HomesRules, """{ "rules": { "default": { "delay": 30, "cooldown": 60 } } }""")
        lab.dispatcher.execute("manymanycommands reload", Harness.console(helper))
        helper.assertValueEqual(homes()[HomesRules.delay], 30L, "le délai rechargé")

        clock.advance(3)
        engine.tick()
        stage.assertPlayerAt(destination, "après le délai du lancement, 3 secondes")

        stage.place(stage.player, stage.departure)
        clock.advance(20)
        stage.assertRefused(engine.launch(stage.request(destination, homes())), TeleportRefusal.OnCooldown(40), "le cooldown en cours, mesuré aux règles rechargées")
        helper.succeed()
    }
}
