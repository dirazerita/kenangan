package id.kenang.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/**
 * Creates/opens the SQLite database. Foreign keys ON so project deletion
 * cascades (photos/scenes/jobs/outputs go with it).
 */
object DatabaseFactory {

    fun create(dbFile: File): KenangDb {
        dbFile.parentFile?.mkdirs()
        val driver = openDriver(dbFile)
        return KenangDb(driver)
    }

    /** In-memory database for tests. */
    fun createInMemory(): KenangDb {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        KenangDb.Schema.create(driver)
        return KenangDb(driver)
    }

    private fun openDriver(dbFile: File): SqlDriver {
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        val props = Properties().apply { put("foreign_keys", "true") }
        val driver = JdbcSqliteDriver(url, props)
        migrate(driver)
        return driver
    }

    private fun migrate(driver: SqlDriver) {
        val currentVersion = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version;",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0,
        ).value

        val schemaVersion = KenangDb.Schema.version
        if (currentVersion == 0L) {
            KenangDb.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = $schemaVersion;", 0)
        } else if (currentVersion < schemaVersion) {
            KenangDb.Schema.migrate(driver, currentVersion, schemaVersion)
            driver.execute(null, "PRAGMA user_version = $schemaVersion;", 0)
        }
    }
}
