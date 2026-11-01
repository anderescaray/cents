package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.local.migration.ALL_MIGRATIONS
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File

/**
 * A database file at a schema version that shipped, for a migration test to start from, and the
 * way to open it with every migration. Shared by the migration tests, so each one starts from the
 * exported schema rather than from DDL typed out again (`CentsDatabaseMigrationTest`).
 */
internal class MigrationTestDatabase {
    private val file: File =
        ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getDatabasePath(NAME)
            .also {
                it.parentFile?.mkdirs()
                it.delete()
            }

    /** Rows written into the database [createVersion] left, before Room opens it. */
    fun writeRaw(vararg statements: String) {
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            statements.forEach(database::execSQL)
        } finally {
            database.close()
        }
    }

    fun openWithMigrations(): CentsDatabase =
        Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CentsDatabase::class.java,
                NAME,
            ).addMigrations(*ALL_MIGRATIONS)
            .build()

    /**
     * A real database at [version], created from the committed schema Room exported for it.
     *
     * Typing the old DDL out here would test this file against itself: the version that
     * shipped is the one Room exported, so that is the one a migration has to be able to
     * start from. The same account and movement go into every version, so what survives is
     * compared against the same numbers whichever hop is under test.
     */
    fun createVersion(version: Int) {
        val schema =
            JSONObject(File(SCHEMA_DIRECTORY, "$version.json").readText()).getJSONObject("database")
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (i in 0 until indices.length()) {
                    database.execSQL(
                        indices.getJSONObject(i).getString("createSql").replace(TABLE_PLACEHOLDER, table),
                    )
                }
            }
            // Room's own bookkeeping: without it the database does not look like one Room
            // wrote, and the migration would be tested against something that never existed.
            database.execSQL(ROOM_MASTER_TABLE)
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
                arrayOf(schema.getString("identityHash")),
            )
            database.execSQL(
                "INSERT INTO accounts " +
                    "(name, type, currency, openingBalanceMinor, colorArgb, archived, sortOrder, createdAt) " +
                    "VALUES ('Checking', 'BANK', 'EUR', 10000, 0, 0, 0, 1755511200000)",
            )
            database.execSQL(
                "INSERT INTO transactions " +
                    "(accountId, categoryId, kind, amountMinor, currency, date, createdAt, updatedAt) " +
                    "VALUES (1, NULL, 'EXPENSE', 2550, 'EUR', 20684, 1755511200000, 1755511200000)",
            )
            database.version = version
        } finally {
            database.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"

        /** The exported DDL names its own table with this, backticks already around it. */
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
        val SCHEMA_DIRECTORY = File("schemas/${CentsDatabase::class.qualifiedName}")

        const val ROOM_MASTER_TABLE =
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
    }
}

/** The amount of the one movement [MigrationTestDatabase.createVersion] writes at every version. */
internal suspend fun moneyIn(database: CentsDatabase): Long =
    database
        .transactionDao()
        .observeAll()
        .first()
        .first { it.ruleId == null }
        .amountMinor
