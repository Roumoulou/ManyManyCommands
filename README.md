# ManyManyCommands

Mod Fabric pour Minecraft 26.3, 26.2 et 26.1.2, un jar par version : des commandes de homes, de warps et
de téléportation pour un serveur. Il se charge sur un serveur dédié et en solo, et un joueur n'a rien à
installer pour rejoindre un serveur qui le porte.

| | |
|---|---|
| État | 0.1.0 : le socle (les messages en français et en anglais, `settings.json`, les droits par défaut), `/manymanycommands`, les fichiers de règles des cinq familles qui téléportent, le déroulé d'une téléportation, ses règles et le moteur qui le joue, et quatre familles jouables : les homes du joueur, `/homes` et ses sept sous-commandes, ses trois alias, et `homes.json` dans le dossier du monde ; les warps, `/warps` et ses huit sous-commandes, l'envoi par l'administrateur, ses trois alias, et `warps.json` ; le retour, `/back` et la position de retour qu'enregistrent les homes, les warps, l'envoi, le retour lui-même, la téléportation aléatoire et la mort ; la téléportation aléatoire, `/wild`, `/wild timed` et `/rtp`, et `wild.json` ; les demandes de téléportation, leurs six commandes, l'expiration, la chute à la déconnexion, le voyage par le déroulé commun et `tpa.json` ; le registre des joueurs, `data/players.json`, écrit à chaque connexion ; les groupes (`groups.json`), les inscriptions (`data/memberships.json`), le système de droits du mod, qui donne à chaque joueur son jeu de règles et ses nœuds, et les neuf commandes `group` et `permission` de `/manymanycommands` ; la branche `/homes player` n'a pas ses commandes, et la source `external` n'est pas branchée |
| Minecraft | 26.3, 26.2 et 26.1.2, un jar par version : `manymanycommands-<version>+<version du jeu>.jar` |
| Fabric Loader | 0.19.5 ou plus récent, **calculé** : voir Build |
| Dépendances | Fabric API, celle contre laquelle chaque jar est compilé ou plus récente (0.162.0 en 26.3, 0.161.0 en 26.2, 0.155.3 en 26.1.2), **calculée** : voir Build ; Fabric Language Kotlin 1.14.2 ou plus récent |
| Embarqué | Storify 0.6.0-SNAPSHOT, la bibliothèque des fichiers JSON du mod, avec tomlkt et json5, en jar-in-jar |
| Côté | serveur dédié et solo |
| Langage | Kotlin 2.4.21, Java 25 |
| Licence | LGPL-3.0-only, voir La licence |

Les spécifications du mod vivent dans `01-docs\specs\` du classeur, hors du dépôt.

---

## Le mod

| Commande | Ce qu'elle fait |
|---|---|
| `/manymanycommands` | affiche la version du mod, la source des droits et l'état de chacun de ses fichiers, les sept fichiers de réglages, le registre des joueurs et les inscriptions (`data/players.json`, `data/memberships.json`) puis les données du monde (`world/homes.json`, `world/warps.json`, `world/back.json`, `world/wild.json`, `world/tpa.json`), valide ou refusé, puis ce qui manque aux groupes |
| `/manymanycommands reload` | relit les fichiers du mod, réglages et données : une ligne par fichier, appliqué, ou refusé avec ses constats, puis les réglages qui attendent le redémarrage, puis ce qui manque aux groupes |
| `/manymanycommands group list` | affiche les groupes et leur nombre de membres, `default` comptant les joueurs du registre qui le suivent |
| `/manymanycommands group info <group>` | affiche le jeu de règles de chaque famille, les nœuds accordés, les nœuds retirés et les membres d'un groupe |
| `/manymanycommands group set <player> <group>` | inscrit un joueur dans un groupe, à la place du précédent |
| `/manymanycommands group reset <player>` | renvoie un joueur dans `default` |
| `/manymanycommands group of <player>` | affiche le groupe d'un joueur |
| `/manymanycommands permission allow <player> <node>` | accorde un nœud à un joueur, en propre |
| `/manymanycommands permission deny <player> <node>` | retire un nœud à un joueur, en propre |
| `/manymanycommands permission unset <player> <node>` | efface la permission propre du joueur sur ce nœud, qui revient à son groupe |
| `/manymanycommands permission list <player>` | affiche les permissions propres d'un joueur |
| `/mmc` | alias de `/manymanycommands` et de ses sous-commandes |

Toutes sont réservées aux maîtres du jeu (le niveau de `/tp`) et s'exécutent depuis la console. Sans ce
niveau, elles n'existent pas pour le joueur, ni à la complétion ni à l'exécution. Les branches `group` et
`permission` exigent que le système du mod soit la source des droits (`permission-source` à `internal`), et
des inscriptions valides ; `<player>` y désigne tout joueur du registre, connecté ou non, sans la casse, et
se complète avec les joueurs connectés ; `<node>` accepte les nœuds du mod, le jeu d'une famille quand son
fichier de règles le porte, et la clé d'accès d'un warp pour toute clé bien écrite. `set`, `reset`, `allow`,
`deny` et `unset` se journalisent, comme une commande d'opérateur.

| Commande | Ce qu'elle fait |
|---|---|
| `/homes`, `/homes list` | affiche les homes du joueur : l'en-tête avec la limite, chaque home trié, cliquable pour le rejoindre et survolable pour sa position, le home par défaut marqué |
| `/homes create <name>` | crée un home à la position du joueur, sous la limite et hors des dimensions interdites |
| `/homes teleport [<name>]` | rejoint le home nommé, ou le home par défaut, par le déroulé commun |
| `/homes delete <name>` | supprime le home, en rappelant sa position |
| `/homes rename <name> <newName>` | renomme le home, et rien d'autre |
| `/homes move <name>` | déplace le home à la position du joueur |
| `/homes default <name>` | désigne le home par défaut, celui que `teleport` rejoint sans nom |
| `/home [<name>]`, `/sethome <name>`, `/delhome <name>` | les alias de `teleport`, `create` et `delete`, que le réglage `aliases` de `rules/homes.json` désactive ensemble |
| `/warps`, `/warps list` | affiche les warps auxquels le joueur a accès : l'en-tête avec leur nombre, chaque warp trié, cliquable pour le rejoindre et survolable pour sa position, la clé d'un warp réservé pour qui a le nœud de `restrict` |
| `/warps teleport <name>` | rejoint le warp, par le déroulé commun ; un warp réservé à d'autres n'existe pas pour le joueur |
| `/warps create <name> [<pos> [<rotation> [<dimension>]]]` | crée un warp ouvert à tous, à la position de l'administrateur ou au placement donné, dans les limites du monde |
| `/warps delete <name>` | supprime le warp, en rappelant sa position |
| `/warps rename <name> <newName>` | renomme le warp, et rien d'autre |
| `/warps move <name> [<pos> [<rotation> [<dimension>]]]` | déplace le warp à la position de l'administrateur ou au placement donné |
| `/warps restrict <name> <key>`, `/warps unrestrict <name>` | réserve le warp aux joueurs qui ont le nœud `manymanycommands.warps.access.<key>`, ou le rouvre à tous |
| `/warps teleport <name> <targets>` | envoie les joueurs désignés au warp, tout de suite, sans le déroulé et sans égard pour sa clé |
| `/warp <name> [<targets>]`, `/setwarp <name> [<pos> ...]`, `/delwarp <name>` | les alias de `teleport`, `create` et `delete`, que le réglage `aliases` de `rules/warps.json` désactive ensemble |
| `/back` | ramène le joueur à l'endroit qu'il a quitté en dernier, par le déroulé commun ; deux `/back` de suite font un aller-retour |
| `/wild` | téléporte le joueur vers un point tiré au hasard dans une couronne autour du point d'apparition du monde, à la surface, sur un bloc solide, hors de l'eau et de la lave, par le déroulé commun ; dix tirages au plus, puis un refus qui invite à réessayer |
| `/wild timed` | le tirage commun : le premier joueur ouvre un créneau et tire le point, et ceux qui le lancent pendant le créneau, dans la même dimension, le rejoignent |
| `/rtp [timed]` | l'alias de `/wild`, que le réglage `aliases` de `rules/wild.json` désactive |
| `/tpa <player>`, `/tpahere <player>` | demande à rejoindre un joueur connecté, ou lui demande de venir : le destinataire reçoit la demande, le temps pour répondre et deux mots cliquables, accepter et refuser ; refusée à soi-même, à un joueur non connecté, à qui a fermé sa réception, sous l'intervalle entre deux demandes au même joueur, et sous le quota du demandeur |
| `/tpaaccept [<player>]`, `/tpadeny [<player>]` | accepte ou refuse la demande du joueur nommé, ou sans nom la dernière reçue : la demande est consommée et les deux joueurs prévenus ; acceptée, le voyageur part par le déroulé commun vers la position de l'hôte à l'acceptation, l'arrivée se dit aux deux, et ce qui refuse ou annule le voyage se dit à l'hôte avec sa cause |
| `/tpacancel` | annule la demande envoyée, et son destinataire l'apprend |
| `/tpatoggle` | ferme ou rouvre la réception des demandes, conservée dans `tpa.json` |

Les commandes des homes, `/back`, `/wild`, `/wild timed` et les six commandes des demandes exigent un joueur, et
sont ouvertes à tout joueur par défaut, chacune sous son nœud (`manymanycommands.homes.<commande>`,
`manymanycommands.back`, `manymanycommands.wild`, `manymanycommands.wild.timed`, `manymanycommands.tpa`,
`manymanycommands.tpahere`, `manymanycommands.tpaaccept`, `manymanycommands.tpadeny`, `manymanycommands.tpacancel`,
`manymanycommands.tpatoggle`). Une demande vit `request-lifetime` secondes, une seule par demandeur, la nouvelle
annulant la précédente ; elle tombe quand l'un des deux joueurs se déconnecte, ce que l'autre apprend. Dans `/warps`, `teleport` et `list` le
sont aussi ; `create`, `delete`, `rename`, `move`, `restrict`, `unrestrict` et l'envoi (`teleport.others`) vont
aux maîtres du jeu, qui reçoivent aussi tous les nœuds d'accès, et à la console, qui lance tout sauf ce qui a
besoin d'une position : `create` et `move` sans `<pos>`, `teleport` sans `<targets>`. Une commande qui modifie
un warp et l'envoi se journalisent, comme une commande d'opérateur. La position de retour s'enregistre quand
une source du jeu de règles du joueur l'active (`sources` dans `rules/back.json`) : une téléportation vers un
home ou un warp, la téléportation aléatoire, une demande acceptée pour le voyageur, l'envoi par un
administrateur, `/back` lui-même, et la mort, inactive par défaut. Elle vit en mémoire et s'efface au
redémarrage du serveur.

Les données des familles vivent dans `<monde>/manymanycommands/` : `homes.json`, les homes et la plage de
quota de chaque joueur, `warps.json`, les warps du serveur et la plage de quota de chaque joueur,
`back.json` et `wild.json`, la plage de quota du retour et de la téléportation aléatoire, et `tpa.json`, la
réception fermée des demandes (`requests-closed`) et la plage de quota de chaque joueur. Le mod les écrit à
chaque changement, et une plage de quota survit ainsi au redémarrage du serveur. Un fichier de données
invalide rend sa famille indisponible, sans être réécrit, jusqu'à sa correction et un `reload`.

Le registre des joueurs, `config/manymanycommands/data/players.json`, note à chaque connexion l'UUID, le
dernier nom connu et la date de la dernière connexion de chaque joueur. Il vit hors du monde, parce qu'il
désigne un joueur, connecté ou non, dans une commande d'administration. Invalide, il ne s'écrit pas, et seuls
les joueurs connectés se désignent, jusqu'à sa correction et un `reload`.

Le mod crée ses fichiers de réglages à son premier lancement, sous `config/manymanycommands/`, recrée
celui qui manque, et ne réécrit jamais un fichier qui existe. `settings.json` porte les réglages qui valent
pour tout le mod :

| Clé | Défaut | Valeurs admises | Prend effet |
|---|---|---|---|
| `schema-version` | `1` | un fichier plus récent que le mod est refusé | |
| `language` | `auto` | `auto`, `fr`, `en` | au rechargement |
| `permission-source` | `internal` | `internal`, `external` | au redémarrage du serveur |
| `mmc-alias` | `true` | `true`, `false` | au redémarrage du serveur |

`permission-source` choisit d'où viennent les droits des joueurs : `internal`, le système du mod, décrit
plus bas avec `groups.json`, ou `external`, un mod de permissions par le module de permissions de la
Fabric API, qui n'est pas encore branché : sous `external`, seuls les droits par défaut existent.

`rules/` porte un fichier de règles par famille qui téléporte : ses réglages du serveur, puis ses jeux de
règles sous `rules`.

| Fichier | Réglages du serveur | Règles d'un jeu |
|---|---|---|
| `rules/homes.json` | `enabled`, `aliases` | `max-homes`, `forbidden-dimensions`, `forbidden-departure-dimensions`, `delay`, `cooldown`, `quota` |
| `rules/warps.json` | `enabled`, `aliases` | `forbidden-departure-dimensions`, `delay`, `cooldown`, `quota` |
| `rules/back.json` | `enabled` | `sources`, `forbidden-departure-dimensions`, `delay`, `cooldown`, `quota` |
| `rules/wild.json` | `enabled`, `aliases`, `min-distance`, `max-distance`, `timed-window` | `forbidden-departure-dimensions`, `quota`, `delay`, `cooldown` |
| `rules/tpa.json` | `enabled`, `request-lifetime`, `request-interval` | `forbidden-departure-dimensions`, `delay`, `cooldown`, `quota` |

Le jeu `default` existe toujours et vaut pour tout joueur. Un autre jeu n'écrit que ce qui l'en distingue,
et s'attribue par le nœud `manymanycommands.rules.<famille>.<nom>` : un joueur qui en reçoit plusieurs prend
le premier dans l'ordre du fichier. `enabled` et `aliases` prennent effet au redémarrage du serveur, tout
le reste au rechargement. Les valeurs livrées et les valeurs admises de chaque clé sont dans la fiche de
sa famille, dans les spécifications.

Sous le système du mod, c'est le groupe d'un joueur qui lui donne son jeu et ses nœuds. `groups.json`
définit les groupes : chaque groupe nomme le jeu qu'il prend dans chaque famille (`rules`), et les nœuds
qu'il accorde (`allow`) et retire (`deny`), une commande, une dispense ou la clé d'accès d'un warp, jamais
un jeu de règles. Le groupe `default` existe toujours.

```json
{
  "schema-version": 1,
  "groups": {
    "default": {},
    "vip": { "rules": { "homes": "vip", "wild": "vip" }, "allow": ["manymanycommands.warps.access.vip"], "deny": [] }
  }
}
```

`data/memberships.json` inscrit chaque joueur dans son groupe (`group`) et porte ses permissions propres
(`allow`, `deny`), qui l'emportent sur le groupe : un nœud `manymanycommands.rules.<famille>.<nom>` accordé
en propre donne ce jeu à la place de celui du groupe. Un joueur inscrit nulle part, ou dans un groupe absent
de `groups.json`, suit `default`, ce que `/manymanycommands` et `reload` disent, comme un jeu nommé que le
fichier de règles de sa famille ne porte pas. Des inscriptions invalides font de tout joueur un membre de
`default`, sans permission propre. Les commandes `group` et `permission` écrivent les inscriptions ; un nœud
inconnu du mod rend le fichier invalide.

Les cinq familles lisent leurs règles ; les demandes y prennent la durée de vie et l'intervalle d'une demande,
et le voyageur le délai, le cooldown et les dimensions de départ de son propre jeu, le quota étant celui du
demandeur. Le déroulé d'une téléportation, que `/homes teleport`, `/warps teleport`, `/back`, `/wild` et
l'acceptation d'une demande lancent, applique celles de sa famille : les
refus dans leur ordre, le délai d'immobilité et son compte à rebours dans la barre d'action, l'annulation
quand le joueur bouge, est blessé, meurt, change de dimension ou se déconnecte, le cooldown par famille, le
quota, et le refus d'une arrivée dangereuse.

Dans les sept fichiers de réglages comme dans les fichiers de données, le JSON est strict, et une clé inconnue
refuse le fichier. Un fichier de réglages refusé au démarrage laisse la place à ses réglages livrés, ce que le
journal dit en erreur ; refusé au rechargement, il laisse ses réglages en vigueur. Chaque fichier se juge
seul : un fichier refusé n'empêche pas les autres de s'appliquer.

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
├── files/                          la lecture et l'écriture des fichiers du mod : Storify et kotlinx-serialization
├── game/                           ce qui nomme un type du jeu : les textes, l'envoi d'un message, les droits, le déroulé, le placement, l'envoi de joueurs, le point sûr de /wild
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
| 0, la logique pure | `common/src/test` | les règles de la couche `rules`, sans un type du jeu : un import du jeu n'y compile pas | `:common:test`, une fois, 390 tests |
| 1, le jeu amorcé | `common/src/testMC` | les fichiers du mod sur de vrais fichiers, et ce qui nomme un type du jeu, sans serveur | `:mc-<lignée>:testMC`, par version, 153 tests |
| 2, le serveur GameTest | `common/src/gametest` | les commandes et le déroulé d'une téléportation, joués par des joueurs simulés et par la console sur un vrai serveur sans fenêtre | `:mc-<lignée>:runGameTest`, par version, 129 gametests |

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

L'entrepôt `C:\MC-Kit`, le même sur chaque poste, se déclare dans `gradle.properties` (`outfitter.profiles_dir`,
`outfitter.maps_dir`), et `machine.properties` peut le surcharger par poste. **`machine.properties`, à créer sur
chaque machine**, à la racine : ce fichier n'est pas versionné, et le build s'en passe. Il porte ce qui est
propre à la personne, le joueur des runs client :

```properties
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
