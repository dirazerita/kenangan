package id.kenang.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
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

    /**
     * Phase-04 runs parallel scene writers + poll readers, so the driver must
     * survive concurrent write transactions (DbConcurrencyTest):
     * - WAL lets readers coexist with a writer.
     * - Transactions must open as IMMEDIATE: SQLDelight's stock JdbcSqliteDriver
     *   issues a deferred `BEGIN`, and a read→write upgrade inside it fails
     *   instantly with SQLITE_BUSY — busy_timeout never applies to upgrades.
     *   The DataSource route uses autoCommit-based transactions, where xerial
     *   honors transaction_mode=IMMEDIATE and busy_timeout queues writers.
     */
    private fun openDriver(dbFile: File): SqlDriver {
        val config = org.sqlite.SQLiteConfig().apply {
            enforceForeignKeys(true)
            setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL)
            busyTimeout = 10_000
            transactionMode = org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE
        }
        val dataSource = org.sqlite.SQLiteDataSource(config).apply {
            url = "jdbc:sqlite:${dbFile.absolutePath}"
        }
        val driver = NotifyingDriver(dataSource.asJdbcDriver())
        migrate(driver)
        return driver
    }

    /**
     * The JDBC drivers ship listener hooks that never deliver, so every
     * SQLDelight asFlow() screen freezes on its first snapshot while
     * background work completes invisibly (dogfood 2026-08-26: keyframes
     * finished in 70s, UI showed empty cards for an hour). This wrapper
     * implements the listener registry for real; mutations still notify via
     * the generated code's notifyListeners calls, deferred by the Transacter
     * until commit. Locked by DbFlowNotificationTest.
     */
    private class NotifyingDriver(
        private val delegate: SqlDriver,
    ) : SqlDriver by delegate {
        private val listeners =
            java.util.concurrent.ConcurrentHashMap<String, MutableSet<app.cash.sqldelight.Query.Listener>>()

        override fun addListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {
            queryKeys.forEach { key ->
                listeners.computeIfAbsent(key) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
                    .add(listener)
            }
        }

        override fun removeListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {
            queryKeys.forEach { key -> listeners[key]?.remove(listener) }
        }

        override fun notifyListeners(vararg queryKeys: String) {
            queryKeys.flatMap { listeners[it].orEmpty() }.toSet()
                .forEach { it.queryResultsChanged() }
        }
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
