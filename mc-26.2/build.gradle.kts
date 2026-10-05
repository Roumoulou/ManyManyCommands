/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  MODULE mc-26.2 — le mod pour Minecraft 26.2
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Ce que le plugin manymanycommands.game-version fait de ce module (le jar
 *  manymanycommands-0.1.0+26.2.jar, le mod en développement, les étages 1 et 2
 *  rejoués contre 26.2, la vérification de compatibilité) est décrit dans son
 *  en-tête, dans build-logic. Ici ne vit que ce qui tient à 26.2 :
 *
 *  ┌───────────────────────────────────────────────────────────────────────────┐
 *  │  1  les dépendances de son catalogue, mc262                               │
 *  │  2  Outfitter : ses environnements, son serveur GameTest, ses cibles      │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 *  Le module n'a pas de sources : rien ne diffère d'une version du jeu à l'autre.
 *  Ce que 26.2 ne partage pas avec les autres versions vit ici, sous src.
 *
 *  Plugins, sans version : la racine les a chargés (build.gradle.kts de la racine).
 *    - manymanycommands.game-version   le module de version (build-logic)
 *    - fr.moulou.outfitter             les environnements de développement et les
 *                                      déploiements (S:\16\_V\Outfitter, consommé
 *                                      en build composite)
 * ════════════════════════════════════════════════════════════════════════════════
 */

plugins {
    id("manymanycommands.game-version")
    id("fr.moulou.outfitter")
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 1 — LES DÉPENDANCES DE 26.2
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  - minecraft                  : le jeu, en 26.2.
 *  - fabric-api                 : la Fabric API publiée pour lui.
 *  - fabric-gametest-api        : le module GameTest de cette Fabric API, pour le
 *                                 serveur de l'étage 2.
 *  - modmenu, cloth-config      : ceux de 26.2, jamais embarqués : la vérification
 *                                 de compatibilité s'en sert pour éprouver un écran
 *                                 de configuration de common contre eux (section 8
 *                                 du plugin de version).
 *
 *  Le reste (chargeur, Kotlin, Storify, le code de common) vient du plugin de
 *  version.
 * ════════════════════════════════════════════════════════════════════════════════
 */
dependencies {
    minecraft(mc262.minecraft)
    implementation(mc262.fabric.api)
    "gametestRuntimeOnly"(mc262.fabric.gametest.api)
    "clientCompileOnly"(mc262.modmenu)
    "clientCompileOnly"(mc262.cloth.config)
}

/**
 * ════════════════════════════════════════════════════════════════════════════════
 *  SECTION 2 — LES ENVIRONNEMENTS DE DÉVELOPPEMENT : OUTFITTER
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  Le plugin Outfitter (S:\16\_V\Outfitter, consommé en build composite : voir
 *  settings.gradle.kts) prépare chaque environnement avant son run, serveur
 *  GameTest compris, et déploie le jar. Ce bloc ne déclare que ce qui est propre à
 *  ce module : la version de Minecraft, qui choisit le dossier de l'entrepôt S:\18,
 *  les deux environnements avec leur profil, le serveur GameTest, les deux cibles
 *  de déploiement et le panier du serveur dédié. Tout le reste vient des clés
 *  `outfitter.*` : gradle.properties de la racine pour ce qui est propre au projet
 *  (maps, monde du serveur, logs), machine.properties pour ce qui est propre au
 *  poste ou à la personne (l'entrepôt S:\18, le joueur). Outfitter lit le
 *  machine.properties de la racine, puis celui de ce module, qui le surcharge clé
 *  par clé et désigne l'instance Prism de 26.2.
 *
 *  ── DEUX ENVIRONNEMENTS, VANILLA PURS ───────────────────────────────────────
 *  `client` et `server` : aucun mod tiers, Loom charge le mod depuis le classpath.
 *  Ils disent ce que voit un joueur n'ayant QUE ce mod, en solo pour le premier,
 *  sur un serveur dédié pour le second. Le dossier est run\<nom> DANS ce module
 *  (mc-26.2\run\client) : les mondes d'une version ne se mélangent pas à ceux d'une
 *  autre.
 *
 *  ── LE SERVEUR GAMETEST : CRÉÉ PAR LE PLUGIN DE VERSION, DÉCLARÉ ICI ────────
 *  `gameTest`, le serveur de l'étage 2, est le run que le plugin de version crée (sa
 *  section 7), et qu'il refuse de laisser sans cette déclaration. Outfitter le fait
 *  tourner dans mc-26.2\run\game-test, hors de build\, le remet à neuf avant chaque
 *  run (`freshGameTest` : world\ et config\ retirés) et range le rapport de ses
 *  tests dans build\test-results\gameTest\TEST-gameTest.xml. Il ne reçoit rien de
 *  l'entrepôt ni de l'instance : un clone sans S:\18 le rejoue tel quel. Pour ne
 *  jouer qu'une partie des tests, un motif à jokers sur leur identifiant,
 *  <mod>:<classe>_<méthode> en snake_case, l'argument entre guillemets : sans eux,
 *  PowerShell le coupe au point.
 *
 *      gradlew :mc-26.2:runGameTest "-Poutfitter.gametest_filter=manymanycommands-gametest:mod_loading_*"
 *
 *  ── DEUX CIBLES, et elles ne reçoivent PAS la même chose ────────────────────
 *  `serverPur`, le serveur dédié 26.2 du classeur (05-instances\server-pur-26.2),
 *  n'a pas de modpack : il reçoit le jar ET le panier `serverPurBundle`, Fabric API
 *  et FLK aux versions de ce module, sans leurs dépendances. Son chemin part de la
 *  racine du build, pas de ce module. `prism`, l'instance PrismLauncher MDTK de 26.2,
 *  porte son propre modpack : elle ne reçoit QUE le jar, et la tâche avertit si
 *  Fabric API ou FLK semblent absents de ses mods ; Outfitter refuse de déployer dans
 *  une instance d'une autre lignée.
 *
 *  Les tâches, groupe `outfitter` : sync<Env>Profile, sync<Env>Worlds, fresh<Env>,
 *  prepare<Env>, deployTo<Cible>, setup<Cible>, resetEnvironments, resetWorlds,
 *  outfitterLog4jConfigs. Le détail, condition, geste et marqueur de chacune : la
 *  doc du plugin.
 * ════════════════════════════════════════════════════════════════════════════════
 */
outfitter {
    minecraftVersion = mc262.versions.minecraft
    environments {
        register("client") { client(); profile = "vanilla" }
        register("server") { server(); profile = "dev" }
    }
    gameTests {
        register("gameTest")
    }
    deployTargets {
        register("serverPur") {
            directory = layout.settingsDirectory.dir("../../05-instances/server-pur-26.2/server")
            profile = "dev"
        }
        register("prism") {
            directory = referenceInstance
            expectedMods.set(listOf("fabric-api", "fabric-language-kotlin"))
        }
    }
}

/* Le panier du serveur dédié : ces deux jars et rien d'autre (la configuration n'est pas transitive), aux versions que le mod a compilées. */
dependencies {
    "serverPurBundle"(mc262.fabric.api)
    "serverPurBundle"(mc.fabric.language.kotlin)
}
