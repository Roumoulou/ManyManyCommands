// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.files

import fr.roumoulou.manymanycommands.rules.common.CommonMessages
import fr.roumoulou.manymanycommands.rules.file.FileFinding
import fr.roumoulou.manymanycommands.rules.file.FindingPath
import fr.roumoulou.manymanycommands.rules.message.Fragment
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Ce qu'un fichier qui ne se décode pas a de fautif, quand son JSON est bien formé : ses clés inconnues et ses valeurs d'une autre nature que
 * celle attendue (cahier des charges, chapitres 2.7.3 et 2.7.4).
 *
 * Le lecteur strict s'arrête à la première faute et la dit dans un message anglais, sans chemin. Ce diagnostic relit le fichier en arbre et le
 * confronte au schéma de sa classe sérialisée : il rend toutes les fautes, chacune à son chemin, en constats du mod.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object DocumentDiagnosis {

    /** Les constats de [text], sans leur ligne. Vide quand le JSON est mal formé ou que la faute est d'une autre sorte : le message du lecteur reste alors le seul constat. */
    fun findings(text: String, descriptor: SerialDescriptor): List<FileFinding> {
        val root = try {
            Json.parseToJsonElement(text.removePrefix("﻿"))
        } catch (_: SerializationException) {
            return emptyList()
        }
        return buildList { inspect(root, descriptor, path = "", findings = this) }
    }

    private fun inspect(element: JsonElement, descriptor: SerialDescriptor, path: String, findings: MutableList<FileFinding>) {
        if (descriptor.isInline) return inspect(element, descriptor.getElementDescriptor(0), path, findings)
        if (element is JsonNull) {
            if (!descriptor.isNullable) findings += wrongNature(path, descriptor)
            return
        }
        if (!hasNature(element, descriptor)) {
            findings += wrongNature(path, descriptor)
            return
        }
        when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> (element as JsonObject).forEach { (key, value) ->
                val index = descriptor.getElementIndex(key)
                if (index == CompositeDecoder.UNKNOWN_NAME) findings += FileFinding(FindingPath.property(path, key), null, CommonMessages.FINDING_UNKNOWN_KEY.with())
                else inspect(value, descriptor.getElementDescriptor(index), FindingPath.property(path, key), findings)
            }
            StructureKind.MAP -> (element as JsonObject).forEach { (key, value) -> inspect(value, descriptor.getElementDescriptor(1), FindingPath.entry(path, key), findings) }
            StructureKind.LIST -> (element as JsonArray).forEachIndexed { index, value -> inspect(value, descriptor.getElementDescriptor(0), FindingPath.index(path, index), findings) }
            else -> {}
        }
    }

    /** La valeur a-t-elle la nature de son schéma ? Les natures que ce diagnostic ne connaît pas (une énumération) passent : le lecteur les juge. */
    private fun hasNature(element: JsonElement, descriptor: SerialDescriptor): Boolean = when (natureOf(descriptor)) {
        null -> true
        CommonMessages.NATURE_OBJECT -> element is JsonObject
        CommonMessages.NATURE_LIST -> element is JsonArray
        CommonMessages.NATURE_TEXT -> element is JsonPrimitive && element.isString
        CommonMessages.NATURE_BOOLEAN -> element is JsonPrimitive && !element.isString && element.booleanOrNull != null
        CommonMessages.NATURE_INTEGER -> element is JsonPrimitive && !element.isString && element.longOrNull != null
        else -> element is JsonPrimitive && !element.isString && element.doubleOrNull != null
    }

    private fun natureOf(descriptor: SerialDescriptor): Fragment? = when (descriptor.kind) {
        StructureKind.CLASS, StructureKind.OBJECT, StructureKind.MAP -> CommonMessages.NATURE_OBJECT
        StructureKind.LIST -> CommonMessages.NATURE_LIST
        PrimitiveKind.STRING -> CommonMessages.NATURE_TEXT
        PrimitiveKind.BOOLEAN -> CommonMessages.NATURE_BOOLEAN
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> CommonMessages.NATURE_INTEGER
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> CommonMessages.NATURE_NUMBER
        else -> null
    }

    private fun wrongNature(path: String, descriptor: SerialDescriptor): FileFinding {
        val expected = natureOf(descriptor)?.with() ?: descriptor.serialName
        return FileFinding(path.ifEmpty { null }, null, CommonMessages.FINDING_WRONG_NATURE.with(expected))
    }
}
