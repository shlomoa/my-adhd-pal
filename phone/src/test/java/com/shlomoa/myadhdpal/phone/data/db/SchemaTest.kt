package com.shlomoa.myadhdpal.phone.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TC-S-097: the exported schema is committed and the migration policy is enforced for every version step. */
class SchemaTest {
    private val schemaDir = File("schemas/${AppDatabase::class.java.name}")

    @Test
    fun `TC-S-097 the exported schema of every version up to the current one is committed`() {
        assertTrue("run from the module directory: ${File(".").absolutePath}", schemaDir.isDirectory)
        val versions = schemaDir.listFiles { f -> f.extension == "json" }!!.map { it.nameWithoutExtension.toInt() }.sorted()
        assertEquals((1..AppDatabase.VERSION).toList(), versions)
    }

    @Test
    fun `TC-S-097 every version step has a migration and none is destructive`() {
        val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until AppDatabase.VERSION).map { it to it + 1 }, steps)
    }

    @Test
    fun `TC-S-097 the migration policy is documented`() {
        val doc = File("../docs/data-layer.md").readText()
        assertTrue(doc.contains("Migration policy"))
        assertTrue(doc.contains("never destructive"))
    }
}
