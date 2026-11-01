package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-C02-4 (REQ-COL-09): the Collections copy in EN, ES and DE, and equal entry counts in the three strings files. */
class CollectionManageStringsTest {
    @Test
    fun collectionCopyExistsInEveryLocaleWithTheMockupWording() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"manage_tab_collections\">Collections</string>"))
        assertTrue(en.contains("<string name=\"collections_empty_title\">No collections yet</string>"))
        assertTrue(en.contains("<string name=\"collections_new\">New collection</string>"))
        assertTrue(en.contains("<string name=\"content_edit_collection_members\">Add or remove garments</string>"))
        assertTrue(en.contains("<string name=\"collection_delete_confirm\">Delete collection</string>"))
        assertTrue(en.contains("<item quantity=\"other\">%d garments</item>"))

        assertTrue(es.contains("<string name=\"manage_tab_collections\">Colecciones</string>"))
        assertTrue(es.contains("<string name=\"collections_empty_title\">Aún no hay colecciones</string>"))
        assertTrue(es.contains("<string name=\"collections_new\">Nueva colección</string>"))
        assertTrue(es.contains("<string name=\"content_edit_collection_members\">Añadir o quitar prendas</string>"))
        assertTrue(es.contains("<string name=\"collection_delete_confirm\">Eliminar colección</string>"))
        assertTrue(es.contains("<item quantity=\"other\">%d prendas</item>"))

        assertTrue(de.contains("<string name=\"manage_tab_collections\">Sammlungen</string>"))
        assertTrue(de.contains("<string name=\"collections_empty_title\">Noch keine Sammlungen</string>"))
        assertTrue(de.contains("<string name=\"collections_new\">Neue Sammlung</string>"))
        assertTrue(de.contains("<string name=\"content_edit_collection_members\">Kleidungsstücke hinzufügen oder entfernen</string>"))
        assertTrue(de.contains("<string name=\"collection_delete_confirm\">Sammlung löschen</string>"))
        assertTrue(de.contains("<item quantity=\"other\">%d Kleidungsstücke</item>"))
    }

    @Test
    fun everyLocaleKeepsTheSameNumberOfEntries() {
        val counts = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-es/strings.xml",
            "app/src/main/res/values-de/strings.xml",
        ).map { entryCount(resource(it)) }

        assertEquals(counts[0], counts[1])
        assertEquals(counts[0], counts[2])
    }

    private fun entryCount(xml: String): Int = Regex("<(string|plurals) name=\"").findAll(xml).count()

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
