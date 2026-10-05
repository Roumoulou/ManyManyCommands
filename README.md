# ManyManyCommands

Mod Fabric pour Minecraft 26.3, 26.2 et 26.1.2, un jar par version : des commandes de homes, de warps et
de téléportation pour un serveur. Il se charge sur un serveur dédié et en solo, et un joueur n'a rien à
installer pour rejoindre un serveur qui le porte.

| | |
|---|---|
| État | 0.1.0 : le socle (les messages en français et en anglais, `settings.json`, les droits par défaut) et sa première commande, `/manymanycommands` |
| Minecraft | 26.3, 26.2 et 26.1.2, un jar par version : `manymanycommands-<version>+<version du jeu>.jar` |
| Fabric Loader | 0.19.5 ou plus récent, **calculé** : voir Build |
| Dépendances | Fabric API, celle contre laquelle chaque jar est compilé ou plus récente (0.161.0 en 26.3 et 26.2, 0.155.3 en 26.1.2), **calculée** : voir Build ; Fabric Language Kotlin 1.14.1 ou plus récent |
| Embarqué | Storify 0.4.0-SNAPSHOT, la bibliothèque des fichiers JSON du mod, avec tomlkt et json5, en jar-in-jar |
| Côté | serveur dédié et solo |
| Langage | Kotlin 2.4.20, Java 25 |
| Licence | LGPL-3.0-only, voir La licence |

Les spécifications du mod vivent dans `01-docs\specs\` du classeur, hors du dépôt.

---

## Le mod

| Commande | Ce qu'elle fait |
|---|---|
| `/manymanycommands` | affiche la version du mod, la source des droits et l'état de chaque fichier, valide ou refusé |
| `/manymanycommands reload` | relit les fichiers du mod : une ligne par fichier, appliqué, ou refusé avec ses constats |
| `/mmc` | alias de `/manymanycommands` et de ses sous-commandes |

Les trois sont réservées aux maîtres du jeu (le niveau de `/tp`) et s'exécutent depuis la console. Sans ce
niveau, elles n'existent pas pour le joueur, ni à la complétion ni à l'exécution.

Le mod crée `config/manymanycommands/settings.json` à son premier lancement, et ne le réécrit jamais :

| Clé | Défaut | Valeurs admises | Prend effet |
|---|---|---|---|
| `schema-version` | `1` | un fichier plus récent que le mod est refusé | |
| `language` | `auto` | `auto`, `fr`, `en` | au rechargement |
| `permission-source` | `internal` | `internal`, `external` | au redémarrage du serveur |
| `mmc-alias` | `true` | `true`, `false` | au redémarrage du serveur |

Le JSON est strict, et une clé inconnue refuse le fichier. Un fichier refusé au démarrage laisse la place
aux réglages livrés, ce que le journal dit en erreur ; refusé au rechargement, il laisse les réglages en
vigueur. `permission-source` se lit et se valide sans effet : seuls les droits par défaut existent.

Chaque message part comme une clé de traduction avec son texte de repli, en français ou en anglais selon
`language` : un client vanilla affiche le repli, un resourcepack du serveur peut redéfinir la clé.

---

## Architecture

Le code partagé par toutes les versions du jeu vit dans le module `common`, compilé une fois contre la
dernière release servie. Chaque module `mc-<lignée>` ne porte que ce que sa version ne partage pas, et
assemble le jar de cette version. Le découpage des modules et leur build sont décrits en tête de
`settings.gradle.kts`.

```
common/src/main/kotlin/fr/roumoulou/manymanycommands/
├── ManyManyCommands.kt             point d'entrée : il branche les événements du jeu sur le ModRuntime
├── ModIdentity.kt                  MOD_ID, sans un type du jeu
├── ModRuntime.kt                   l'état du mod pour un serveur, ouvert sur un dossier
├── rules/                          les règles des specs : ni le jeu, ni Fabric, ni Storify, ni kotlinx
├── files/                          la lecture des fichiers du mod : Storify et kotlinx-serialization
├── game/                           ce qui nomme un type du jeu : les composants de texte, l'envoi, les droits
└── commands/                       les arbres de commandes

common/src/main/resources/          fabric.mod.json, gabarit que chaque module de version remplit,
                                    et assets/manymanycommands/lang/, les deux fichiers de langue
common/src/test/                    l'étage 0 des tests : la logique pure
common/src/testMC/                  l'étage 1 : le jeu amorcé
common/src/gametest/                l'étage 2 : le mod manymanycommands-gametest, jamais publié

mc-26.1/, mc-26.2/, mc-26.3/        un build par lignée du jeu, sans sources tant que rien n'y diffère

build-logic/src/main/kotlin/        les deux plugins de convention, et les tâches de vérification
```

Une couche ne nomme que celles qui la précèdent dans cet arbre, et sous chacune un package porte le nom de
sa famille de commandes. Le découpage, ses règles et la façon d'ajouter une famille sont dans
`01-docs\technical-docs\00-wip\architecture.md` du classeur, hors du dépôt.

`main` et `client` sont deux source sets séparés : une classe du client ne compile pas dans `main`, ce qui
garantit que le jar tourne sur un serveur dédié. Le source set `client` n'a pas de sources.

---

## Build

```powershell
.\gradlew.bat build --console=plain
```

Un jar par version du jeu, dans `mc-<lignée>/build/libs/manymanycommands-<version du mod>+<version compilée>.jar`
(par exemple `mc-26.1/build/libs/manymanycommands-0.1.0+26.1.2.jar`), Storify, tomlkt et json5 embarqués
sous `META-INF/jars/`. `build` joue les **trois étages de test**, où un test descend au plus bas étage où
il peut vivre :

| Étage | Source set | Ce qu'il éprouve | Tâche |
|---|---|---|---|
| 0, la logique pure | `common/src/test` | les règles de la couche `rules`, sans un type du jeu : un import du jeu n'y compile pas | `:common:test`, une fois, 60 tests |
| 1, le jeu amorcé | `common/src/testMC` | les fichiers du mod sur de vrais fichiers, et ce qui nomme un type du jeu, sans serveur | `:mc-<lignée>:testMC`, par version, 46 tests |
| 2, le serveur GameTest | `common/src/gametest` | les commandes, jouées par un joueur simulé et par la console sur un vrai serveur sans fenêtre | `:mc-<lignée>:runGameTest`, par version, 17 gametests |

Ce que chaque test prouve est dans `01-docs\technical-docs\00-wip\strategie-de-test.md` du classeur.
`build` joue aussi, contre chaque version :

- **la vérification de compatibilité** (`:mc-<lignée>:checkCommonCompatibility`) : `common` se compile contre
  la dernière release, et cette tâche cherche dans le classpath de chaque version ce que son bytecode nomme.
  Compte rendu dans `mc-<lignée>/build/reports/common-compatibility.txt` ;
- **l'ouverture du jar** (`:mc-<lignée>:checkReleaseJar`) : les jars embarqués par include et les deux
  licences. Compte rendu dans `mc-<lignée>/build/reports/release-jar.txt`.

**Les planchers ne s'écrivent pas à la main.** `fabric.mod.json` déclare
`"fabricloader": ">=${fabric_loader_version}"` et `"fabric-api": ">=${fabric_api_version}"`, que le
`processResources` de chaque module de version expanse : le chargeur depuis le catalogue `mc`, la Fabric API
depuis celle que le module déclare, sans son suffixe. Monter l'un ou l'autre dans un catalogue durcit donc
l'exigence annoncée aux joueurs.

Les pièges du build sont consignés là où ils mordent : les dépôts de dépendances dans le plugin
`manymanycommands.loom-module`, les plugins chargés une seule fois dans le `build.gradle.kts` de la racine,
le package `buildlogic` dans `build-logic/build.gradle.kts`.

---

## Environnements de développement

Les environnements, le serveur GameTest et les déploiements sont l'affaire du plugin **Outfitter**
(`S:\16\_V\Outfitter`, consommé en build composite : voir `settings.gradle.kts`). Chaque module de version
déclare les siens, dans son propre dossier `run/`.

| Tâche | Effet |
|---|---|
| `:mc-<lignée>:runClient`, `:mc-<lignée>:runServer` | client et serveur de dev, vanilla purs, dans `mc-<lignée>/run/client` et `mc-<lignée>/run/server` |
| `:mc-<lignée>:runGameTest` | l'étage 2 : un serveur GameTest sans fenêtre, dans `mc-<lignée>/run/game-test`, remis à neuf avant chaque run, rapport XML dans `mc-<lignée>/build/test-results/gameTest/` ; `"-Poutfitter.gametest_filter=<motif>"`, entre guillemets, n'en joue qu'une partie |
| `:mc-<lignée>:setupServerPur`, `:mc-<lignée>:deployToServerPur` | prépare le serveur dédié de sa version (`05-instances/server-pur-<lignée>` du classeur), puis y pousse le jar, Fabric API et Fabric Language Kotlin |
| `:mc-<lignée>:deployToPrism` | pousse le jar seul dans l'instance PrismLauncher de sa version, en 26.2 et en 26.3 |
| `:<module>:listRepositories` | les dépôts de dépendances effectifs d'un module, ceux que Loom pose compris |

**`machine.properties`, à créer sur chaque machine**, à la racine. Ce fichier n'est pas versionné, et le
build s'en passe : sans lui, Outfitter dégrade les environnements et le dit. Il porte les chemins propres au
poste, en barres obliques, et le joueur des runs client :

```properties
outfitter.profiles_dir=S:/18/00-my-minecraft-favorites-configs
outfitter.maps_dir=S:/18/05-maps
outfitter.username=<ton pseudo>
```

Un module qui déploie dans une instance a le sien, qui la désigne :

```properties
# mc-26.2/machine.properties, et de même mc-26.3/machine.properties
outfitter.reference_instance_dir=C:/chemin/vers/PrismLauncher/instances/<instance de la version>/minecraft
```

---

## Publication

`publishMods` envoie les trois jars sur Modrinth et CurseForge par mod-publish-plugin, **à blanc par
défaut** : rien ne part, ce qui serait envoyé s'écrit sous `mc-<lignée>/build/publishMods/`. Les deux
projets se nomment dans `gradle.properties` (`modrinth_project_id`, `curseforge_project_id`), vides tant
qu'ils n'existent pas ; `checkPublication` refuse alors tout envoi réel (`-Ppublish_live=true`).
`:mc-<lignée>:publishToMavenLocal` pose le jar dans le dépôt Maven local. Le détail est dans les sections 9
et 10 du plugin `manymanycommands.game-version`.

---

## Le dépôt

**Le périmètre est le projet Gradle seul**, ce dossier et rien d'autre. Le classeur qui l'entoure reste sur
le disque : un renvoi vers `01-docs`, `04-releases` ou `05-instances` pointe hors du dépôt.

### Les messages de commit

```
<zone> : <ce qui change, à l'infinitif, en minuscule, sans point final>

<Le pourquoi, si ce n'est pas évident. Jamais le comment : le diff le dit déjà.>
```

| Zone | Ce qu'elle couvre |
|---|---|
| `build` | `settings.gradle.kts`, les `build.gradle.kts` de la racine et des modules, `build-logic`, les catalogues, le wrapper, `gradle.properties` |
| `mod` | le code et les ressources du mod : `common/src/main`, `common/src/client` et les `src` des modules de version |
| `test` | `common/src/test`, `common/src/testMC` et `common/src/gametest` |
| `doc` | ce README |
| `dépôt` | `.gitignore`, `.gitattributes`, `.editorconfig`, les licences, la structure du dépôt |

### L'identité

Les commits portent le pseudonyme `roumoulou` et une adresse noreply de GitHub, jamais un nom civil ni une
adresse personnelle. Pas de ligne `Co-Authored-By`. La branche est `master`.

## La licence

ManyManyCommands est distribué sous la GNU Lesser General Public License, version 3 seulement (SPDX
`LGPL-3.0-only`) ; copyright (c) 2026 Roumoulou. Le texte fait foi : `LICENSE`, et `LICENSE.GPL` pour la
GPL v3 qu'il incorpore ; les deux voyagent dans le jar et dans le jar de sources, suffixés du nom de
l'artefact. Storify, embarquée en jar-in-jar, est sous la même licence et porte la sienne dans son propre
jar.
