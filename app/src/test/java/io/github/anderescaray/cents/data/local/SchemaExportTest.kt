package io.github.anderescaray.cents.data.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the migration story. Room only exports a schema while `exportSchema` is on and
 * `room.schemaLocation` is set; if either is lost, migrations can no longer be written or
 * tested against the previous schema, and nothing else in the build would notice.
 */
class SchemaExportTest {
    @Test
    fun `the current schema version is exported and committed`() {
        val schema = File("schemas/${CentsDatabase::class.qualifiedName}/${CentsDatabase.VERSION}.json")

        assertTrue(
            "Missing ${schema.path}. Room must export every schema version so migrations can be tested.",
            schema.isFile,
        )
    }
}
