package id.kenang.core.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android database. AndroidSqliteDriver already delivers query notifications
 * and runs create/migrate from the generated schema, so none of the desktop
 * JDBC workarounds are needed here — only foreign keys have to be switched on
 * per connection so deleting a project still cascades.
 */
object DatabaseFactory {

    fun create(context: Context, name: String = "kenang.db"): KenangDb =
        KenangDb(openDriver(context, name))

    private fun openDriver(context: Context, name: String): SqlDriver =
        AndroidSqliteDriver(
            schema = KenangDb.Schema,
            context = context,
            name = name,
            callback = object : AndroidSqliteDriver.Callback(KenangDb.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
}
