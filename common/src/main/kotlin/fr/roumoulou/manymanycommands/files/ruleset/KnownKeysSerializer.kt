// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files.ruleset

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/**
 * Un objet JSON aux clés connues, toutes facultatives, qui se décode en ses seules clés écrites, dans l'ordre du fichier.
 *
 * Une classe sérialisée donnerait son défaut à une clé absente, et le fichier ne dirait plus ce qu'il écrit : or une règle absente d'un jeu
 * prend la valeur d'un autre jeu, et un `quota` absent n'est pas un `quota` à `null`. Le schéma reste celui d'une classe : le lecteur strict
 * refuse une clé inconnue, et `DocumentDiagnosis` y lit les clés et leur nature.
 *
 * @param keys chaque clé et ce qui décode sa valeur, dans l'ordre où le mod les écrit
 */
internal class KnownKeysSerializer(serialName: String, private val keys: List<Pair<String, KSerializer<*>>>) : KSerializer<Map<String, Any?>> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(serialName) {
        keys.forEach { (key, serializer) -> element(key, serializer.descriptor, isOptional = true) }
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> = decoder.decodeStructure(descriptor) {
        val written = LinkedHashMap<String, Any?>()
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            written[keys[index].first] = decodeSerializableElement(descriptor, index, keys[index].second)
        }
        written
    }

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) = encoder.encodeStructure(descriptor) {
        keys.forEachIndexed { index, (key, serializer) ->
            @Suppress("UNCHECKED_CAST")
            if (key in value) encodeSerializableElement(descriptor, index, serializer as KSerializer<Any?>, value[key])
        }
    }
}
