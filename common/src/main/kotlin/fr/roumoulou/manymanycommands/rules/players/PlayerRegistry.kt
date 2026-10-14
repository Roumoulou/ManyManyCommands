// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules.players

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.common.NameRules
import fr.roumoulou.manymanycommands.rules.common.PlayerIds
import fr.roumoulou.manymanycommands.rules.data.DataRules
import fr.roumoulou.manymanycommands.rules.data.WrittenData
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.ruleset.missingKey
import fr.roumoulou.manymanycommands.rules.ruleset.notAllowed
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Un joueur désigné dans une commande (cahier des charges, chapitre 2.7.2) : son UUID, et le nom sous lequel il l'est, celui du moment ou son dernier nom connu. */
data class PlayerRef(val id: UUID, val name: String)

/** Ce que le registre tient d'un joueur : son dernier nom connu, qui fait foi, et la date de sa dernière connexion, à la seconde. */
data class PlayerRecord(val name: String, val lastSeen: Instant)

/**
 * Le registre des joueurs (cahier des charges, chapitre 2.7.2) : chaque joueur qui s'est connecté, par son UUID, dans l'ordre du fichier. Il vit
 * hors du dossier du monde, parce que les inscriptions des groupes s'y réfèrent et survivent à un changement de monde.
 */
data class PlayerRegistry(val players: Map<UUID, PlayerRecord> = emptyMap()) {

    operator fun get(player: UUID): PlayerRecord? = players[player]

    /** Le registre où [player] vient de se connecter sous [name], à [now] tronqué à la seconde : son entrée, à sa place s'il en avait une. */
    fun joined(player: UUID, name: String, now: Instant): PlayerRegistry = PlayerRegistry(players + (player to PlayerRecord(name, now.truncatedTo(ChronoUnit.SECONDS))))

    /** Le joueur dont [name] est le dernier nom connu, sans tenir compte de la casse, sous ce nom ; quand plusieurs le portent, le dernier connecté. `null` quand aucun. */
    fun named(name: String): PlayerRef? = players.entries.filter { it.value.name.equals(name, ignoreCase = true) }.maxByOrNull { it.value.lastSeen }?.let { PlayerRef(it.key, it.value.name) }
}

/** `data\players.json` en règles : ses constats et son contenu, par le mécanisme commun des fichiers de données. */
object PlayerRegistryRules {

    val fileName: String = DataRules.serverFileName("players")

    fun findings(written: WrittenData<WrittenPlayerRecord>): List<FileFinding> = DataRules.findings(written) { path, entry -> entry.findings(path) }

    /** Le registre d'un fichier que [findings] juge valide. */
    fun registry(written: WrittenData<WrittenPlayerRecord>): PlayerRegistry =
        PlayerRegistry(written.players.entries.associate { (id, entry) -> (PlayerIds.parse(id) ?: error("Unvalidated player id '$id'")) to entry.value() })

    fun written(registry: PlayerRegistry): WrittenData<WrittenPlayerRecord> = WrittenData(DataRules.SCHEMA_VERSION, registry.players.entries.associate { (id, record) -> id.toString() to WrittenPlayerRecord.of(record) })

    /** L'instant que [written] écrit, en UTC comme `2026-10-05T12:00:00Z`, ou `null` quand ce n'en est pas un. */
    fun instantOf(written: String): Instant? = try {
        Instant.parse(written)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * L'entrée d'un joueur dans `data\players.json` telle qu'elle s'écrit (cahier des charges, chapitre 2.7.5) : son nom et sa dernière connexion, tous
 * deux obligatoires. Une clé qui manque vaut `null`.
 */
data class WrittenPlayerRecord(val name: String?, val lastSeen: String?) {

    companion object {

        const val LAST_SEEN_KEY = "last-seen"

        fun of(record: PlayerRecord): WrittenPlayerRecord = WrittenPlayerRecord(record.name, record.lastSeen.toString())
    }

    /** Les constats de l'entrée écrite à [path], sans leur ligne : une clé qui manque, un nom hors de la règle des noms, une date qui n'est pas un instant UTC. */
    fun findings(path: String): List<FileFinding> = buildList {
        when {
            name == null -> add(missingKey(path, DataRules.NAME_KEY))
            !NameRules.isName(name) -> add(notAllowed(FindingPath.property(path, DataRules.NAME_KEY), name, CommonMessages.ALLOWED_NAME.with()))
        }
        when {
            lastSeen == null -> add(missingKey(path, LAST_SEEN_KEY))
            PlayerRegistryRules.instantOf(lastSeen) == null -> add(notAllowed(FindingPath.property(path, LAST_SEEN_KEY), lastSeen, CommonMessages.ALLOWED_UTC_INSTANT.with()))
        }
    }

    /** L'entrée d'une écriture que [findings] admet. */
    fun value(): PlayerRecord = PlayerRecord(
        name = name ?: error("Unvalidated player record without '${DataRules.NAME_KEY}'"),
        lastSeen = PlayerRegistryRules.instantOf(lastSeen ?: error("Unvalidated player record without '$LAST_SEEN_KEY'")) ?: error("Unvalidated instant '$lastSeen'"),
    )
}
