// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.groups

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.permission.NodeCatalog
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed

/**
 * Un groupe (`groups.md` des specs, chapitre 2) : le jeu qu'il prend dans chaque famille, par la commande principale de la famille, les nœuds
 * qu'il accorde et ceux qu'il retire. Un jeu s'attribue en le nommant, jamais par un nœud : aucun nœud de jeu de règles ne figure dans ses listes.
 */
data class GroupDefinition(val ruleSets: Map<String, String> = emptyMap(), val allow: Set<String> = emptySet(), val deny: Set<String> = emptySet()) {

    /** Le verdict du groupe sur le nœud [node] : accordé, retiré, ou rien. */
    fun verdict(node: String): Boolean? = when (node) {
        in allow -> true
        in deny -> false
        else -> null
    }
}

/** Les groupes de `groups.json`, par leur nom, dans l'ordre du fichier. Le groupe `default` y est toujours, écrit ou non. */
data class Groups(val groups: Map<String, GroupDefinition>) {

    companion object {

        /** Le groupe qui existe toujours, et accueille tout joueur qui n'est inscrit nulle part. */
        const val DEFAULT_GROUP = "default"

        /** Les groupes livrés, ceux d'un fichier qui n'écrit rien : `default` seul, sans jeu nommé ni nœud. */
        val SHIPPED = Groups(mapOf(DEFAULT_GROUP to GroupDefinition()))
    }

    val names: List<String> get() = groups.keys.toList()

    operator fun get(name: String): GroupDefinition? = groups[name]

    /**
     * Le groupe qui vaut pour un joueur inscrit dans [name] : le sien quand il existe, `default` sinon, pour qui n'est inscrit nulle part comme
     * pour un groupe absent des réglages (chapitre 4).
     */
    fun effective(name: String?): GroupDefinition = name?.let { groups[it] } ?: groups.getValue(DEFAULT_GROUP)
}

/** Un groupe tel que `groups.json` l'écrit : une clé qui manque vaut vide. */
data class WrittenGroup(val ruleSets: Map<String, String> = emptyMap(), val allow: List<String> = emptyList(), val deny: List<String> = emptyList()) {

    /**
     * Les constats du groupe écrit à [path], sans leur ligne : une famille que le mod ne connaît pas sous `rules`, un nom de jeu invalide, un
     * nœud que le mod ne connaît pas, un nœud de jeu de règles, un nœud à la fois accordé et retiré.
     */
    fun findings(path: String): List<FileFinding> = buildList {
        val rulesPath = FindingPath.property(path, GroupsRules.RULES_KEY)
        ruleSets.forEach { (family, set) ->
            val familyPath = FindingPath.property(rulesPath, family)
            when {
                family !in NodeCatalog.FAMILIES -> add(FileFinding(familyPath, null, CommonMessages.FINDING_UNKNOWN_KEY.with()))
                !NameRules.isLowercaseName(set) -> add(notAllowed(familyPath, set, CommonMessages.ALLOWED_LOWERCASE_NAME.with()))
            }
        }
        addAll(NodeLists.findings(FindingPath.property(path, GroupsRules.ALLOW_KEY), allow, ruleSetsAllowed = false, alsoIn = emptyList()))
        addAll(NodeLists.findings(FindingPath.property(path, GroupsRules.DENY_KEY), deny, ruleSetsAllowed = false, alsoIn = allow))
    }

    /** Le groupe d'une écriture que [findings] admet. */
    fun value(): GroupDefinition = GroupDefinition(ruleSets, allow.toSet(), deny.toSet())
}

/**
 * `groups.json` tel qu'il s'écrit : sa version de schéma, et chaque groupe sous son nom, dans l'ordre du fichier.
 *
 * @property schemaVersion la version de schéma, celle du mod quand le fichier ne l'écrit pas
 */
data class WrittenGroups(val schemaVersion: Int = GroupsRules.SCHEMA_VERSION, val groups: Map<String, WrittenGroup> = emptyMap())

/** `groups.json` en règles (`groups.md` des specs, chapitres 2 et 4) : ses constats et son contenu. */
object GroupsRules {

    const val FILE_NAME = "groups.json"

    /** La version de schéma que ce mod écrit et sait lire : un fichier qui en annonce une plus récente est invalide. */
    const val SCHEMA_VERSION = 1

    const val SCHEMA_VERSION_KEY = "schema-version"
    const val GROUPS_KEY = "groups"
    const val RULES_KEY = "rules"
    const val ALLOW_KEY = "allow"
    const val DENY_KEY = "deny"

    /** Le fichier que le mod écrit quand il manque : le groupe `default`, vide. */
    fun shippedFile(): WrittenGroups = WrittenGroups(SCHEMA_VERSION, mapOf(Groups.DEFAULT_GROUP to WrittenGroup()))

    /** Les constats d'un fichier lu, sans leur ligne : la version de schéma, puis chaque groupe, son nom d'abord. Vide quand le fichier est valide. */
    fun findings(written: WrittenGroups): List<FileFinding> = buildList {
        if (written.schemaVersion > SCHEMA_VERSION) add(FileFinding(SCHEMA_VERSION_KEY, null, CommonMessages.FINDING_SCHEMA_TOO_RECENT.with(written.schemaVersion, SCHEMA_VERSION)))
        written.groups.forEach { (name, group) ->
            val path = FindingPath.entry(GROUPS_KEY, name)
            if (!NameRules.isLowercaseName(name)) add(notAllowed(path, name, CommonMessages.ALLOWED_LOWERCASE_NAME.with()))
            addAll(group.findings(path))
        }
    }

    /**
     * Les groupes d'un fichier que [findings] juge valide, dans l'ordre du fichier. Le groupe `default` existe même quand le fichier ne l'écrit
     * pas : il vient alors après les groupes écrits.
     */
    fun groups(written: WrittenGroups): Groups {
        val groups = LinkedHashMap<String, GroupDefinition>()
        written.groups.forEach { (name, group) -> groups[name] = group.value() }
        groups.putIfAbsent(Groups.DEFAULT_GROUP, GroupDefinition())
        return Groups(groups)
    }
}

/** Une liste de nœuds écrite dans un fichier, `allow` ou `deny` : chaque nœud doit être connu du mod, et absent de l'autre liste. */
internal object NodeLists {

    /**
     * Les constats de la liste [nodes] écrite à [path], sans leur ligne, un par nœud fautif.
     *
     * @param ruleSetsAllowed un nœud de jeu de règles est admis : oui pour un joueur, non pour un groupe, qui attribue un jeu en le nommant
     * @param alsoIn la liste où un nœud de celle-ci ne doit pas figurer aussi
     */
    fun findings(path: String, nodes: List<String>, ruleSetsAllowed: Boolean, alsoIn: List<String>): List<FileFinding> = nodes.mapIndexedNotNull { index, name ->
        val nodePath = FindingPath.index(path, index)
        val node = NodeCatalog.parse(name)
        when {
            node == null -> notAllowed(nodePath, name, (if (ruleSetsAllowed) CommonMessages.ALLOWED_NODE else CommonMessages.ALLOWED_COMMAND_NODE).with())
            !ruleSetsAllowed && NodeCatalog.ruleSetOf(node) != null -> notAllowed(nodePath, name, CommonMessages.ALLOWED_COMMAND_NODE.with())
            name in alsoIn -> notAllowed(nodePath, name, CommonMessages.ALLOWED_NODE_ABSENT_FROM_ALLOW.with())
            else -> null
        }
    }
}
