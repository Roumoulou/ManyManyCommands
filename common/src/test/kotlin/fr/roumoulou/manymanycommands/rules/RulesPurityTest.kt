// SPDX-FileCopyrightText: 2026 Roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.roumoulou.manymanycommands.rules

import fr.roumoulou.manymanycommands.rules.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readBytes

/**
 * Le garde de la couche `rules` : aucune de ses classes ne nomme le jeu, Fabric, Storify, kotlinx-serialization ni SLF4J.
 *
 * Le classpath de cet étage ne les porte pas, mais une classe qu'aucun test ne charge passerait quand même : ce test lit le bytecode de tout le
 * package, où chaque type nommé laisse son nom.
 */
class RulesPurityTest {

    private companion object {
        val FORBIDDEN = listOf("net/minecraft/", "net/fabricmc/", "com/mojang/", "fr/moulou/storify/", "kotlinx/serialization/", "org/slf4j/")
    }

    @Test
    fun `aucune classe de rules ne nomme le jeu, Fabric, Storify, kotlinx-serialization ni SLF4J`() {
        val classesRoot = Path.of(MessageType::class.java.protectionDomain.codeSource.location.toURI())
        val rulesDirectory = classesRoot.resolve("fr/roumoulou/manymanycommands/rules")
        val classFiles = Files.walk(rulesDirectory).use { paths -> paths.filter { it.extension == "class" }.toList() }
        assertTrue(classFiles.isNotEmpty(), "No compiled class found under $rulesDirectory")

        val offenders = classFiles.flatMap { classFile ->
            val bytecode = String(classFile.readBytes(), Charsets.ISO_8859_1)
            FORBIDDEN.filter { it in bytecode }.map { "${rulesDirectory.relativize(classFile)} names $it" }
        }
        assertEquals(emptyList<String>(), offenders, "The rules layer must stay free of the game and of the file libraries")
    }
}
