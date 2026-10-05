// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.ruleset

import fr.roumoulou.manymanycommands.MOD_ID
import fr.roumoulou.manymanycommands.rules.ruleset.DimensionsRule
import fr.roumoulou.manymanycommands.rules.ruleset.IntegerRule
import fr.roumoulou.manymanycommands.rules.ruleset.QuotaRule
import fr.roumoulou.manymanycommands.rules.ruleset.Rule
import fr.roumoulou.manymanycommands.rules.ruleset.RulesTable
import fr.roumoulou.manymanycommands.rules.ruleset.SwitchRule
import fr.roumoulou.manymanycommands.rules.ruleset.SwitchesRule
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenQuota
import fr.roumoulou.manymanycommands.rules.ruleset.WrittenRules
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Un fichier de règles tel qu'il s'écrit (cahier des charges, chapitre 2.7.1) : ce que le fichier porte, sans ce qu'il ne porte pas.
 *
 * Chaque famille a sa classe, parce que Storify crée le fichier qui manque depuis le constructeur sans argument de la classe qu'il lit : c'est lui
 * qui porte les valeurs livrées de la famille.
 */
abstract class RulesDocument(val written: WrittenRules)

/**
 * La forme sérialisée du fichier de règles d'une famille, déduite de sa table : la version de schéma, ses réglages du serveur, puis ses jeux sous
 * `rules`. Chaque règle s'y lit dans la nature de sa sorte.
 *
 * @param wrap le constructeur de la classe de la famille
 */
abstract class RulesDocumentSerializer<DOCUMENT : RulesDocument>(table: RulesTable, private val wrap: (WrittenRules) -> DOCUMENT) : KSerializer<DOCUMENT> {

    private val sets = MapSerializer(String.serializer(), KnownKeysSerializer("$MOD_ID.rules.${table.family}.set", table.rules.map { it.key to serializerOf(it) }))

    private val document = KnownKeysSerializer(
        "$MOD_ID.rules.${table.family}",
        listOf(RulesTable.SCHEMA_VERSION_KEY to Int.serializer()) + table.settings.map { it.key to serializerOf(it) } + (RulesTable.RULES_KEY to sets),
    )

    override val descriptor: SerialDescriptor get() = document.descriptor

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): DOCUMENT {
        val read = decoder.decodeSerializableValue(document)
        return wrap(
            WrittenRules(
                schemaVersion = read[RulesTable.SCHEMA_VERSION_KEY] as Int? ?: RulesTable.SCHEMA_VERSION,
                settings = read - RulesTable.SCHEMA_VERSION_KEY - RulesTable.RULES_KEY,
                sets = read[RulesTable.RULES_KEY] as Map<String, Map<String, Any?>>? ?: emptyMap(),
            ),
        )
    }

    override fun serialize(encoder: Encoder, value: DOCUMENT) {
        val written = value.written
        encoder.encodeSerializableValue(document, mapOf(RulesTable.SCHEMA_VERSION_KEY to written.schemaVersion) + written.settings + (RulesTable.RULES_KEY to written.sets))
    }

    /** Ce qui lit la valeur d'une règle : sa sorte décide de sa nature dans le fichier. */
    private fun serializerOf(rule: Rule<*, *>): KSerializer<*> = when (rule) {
        is SwitchRule -> Boolean.serializer()
        is IntegerRule -> Long.serializer()
        is DimensionsRule -> ListSerializer(String.serializer())
        is SwitchesRule -> KnownKeysSerializer("$MOD_ID.rules.${rule.key}", rule.switches.map { it to Boolean.serializer() })
        is QuotaRule -> WrittenQuotaSerializer.nullable
    }
}

/** Un quota tel qu'il s'écrit : ses clés présentes. Une clé qui manque y reste `null`, pour que la règle du quota le dise. */
private object WrittenQuotaSerializer : KSerializer<WrittenQuota> {

    private val keys = KnownKeysSerializer(
        "$MOD_ID.rules.quota",
        listOf(WrittenQuota.USES_KEY to Long.serializer(), WrittenQuota.PERIOD_KEY to Long.serializer(), WrittenQuota.CLOCK_KEY to String.serializer()),
    )

    override val descriptor: SerialDescriptor get() = keys.descriptor

    override fun deserialize(decoder: Decoder): WrittenQuota {
        val read = decoder.decodeSerializableValue(keys)
        return WrittenQuota(read[WrittenQuota.USES_KEY] as Long?, read[WrittenQuota.PERIOD_KEY] as Long?, read[WrittenQuota.CLOCK_KEY] as String?)
    }

    override fun serialize(encoder: Encoder, value: WrittenQuota) {
        val written = mapOf(WrittenQuota.USES_KEY to value.uses, WrittenQuota.PERIOD_KEY to value.period, WrittenQuota.CLOCK_KEY to value.clock)
        encoder.encodeSerializableValue(keys, written.filterValues { it != null })
    }
}
