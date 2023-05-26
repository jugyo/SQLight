package org.jugyo.litesqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * - This database helper class can be used to complement another OR mapper library.
 * - It does not take care of database migration.
 * - Don't forget to specify the database version you are using with your OR mapper.
 */
class LiteSQLite private constructor(
    context: Context,
    databaseName: String,
    databaseVersion: Int
) {
    private val sqlite = SQLite(
        context,
        databaseName,
        databaseVersion
    )

    private var onCreateHandler: (db: SQLiteDatabase) -> Unit = {}
    private var onMigrateHandler: (db: SQLiteDatabase, version: Int) -> Unit = { _, _ -> }
    private var logger: (message: String) -> Unit = {}

    /**
     * Executes a raw SQL query and returns a list of entity.
     *
     * @param sql the SQL query to execute
     * @param selectionArgs the arguments to replace placeholders in the SQL query
     * @param mapper the function that maps raw data to an entity class instance
     * @return a List<T>
     */
    suspend fun <T> rawQuery(
        sql: String,
        selectionArgs: List<String>? = null,
        mapper: (Cursor) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        logger.invoke("Executing query: '$sql' with parameters: $selectionArgs")
        val cursor = sqlite.readableDatabase.rawQuery(sql, selectionArgs?.toTypedArray())

        val records = mutableListOf<T>()

        if (cursor.moveToFirst()) {
            while (cursor.moveToNext()) {
                records.add(mapper(cursor))
            }
            cursor.close()
        }

        records
    }

    /**
     * Executes a raw SQL query and returns a list of entity.
     *
     * @param sql the SQL query to execute
     * @param mapper the function that maps raw data to an entity class instance
     * @return a List<T>
     */
    suspend fun <T> rawQuery(
        sql: String,
        mapper: (Cursor) -> T
    ) = rawQuery(sql, null, mapper)

    suspend fun execSQL(
        vararg sql: String
    ) = withContext(Dispatchers.IO) {
        sql.forEach {
            logger.invoke("Executing query: '$it'")
            sqlite.writableDatabase.execSQL(it)
        }
    }

    suspend fun execSQL(
        sql: String,
        bindArgs: List<String>
    ) = withContext(Dispatchers.IO) {
        logger.invoke("Executing query: '$sql' with parameters: $bindArgs")
        sqlite.writableDatabase.execSQL(sql, bindArgs.toTypedArray())
    }

    fun close() {
        sqlite.close()
    }

    private fun onCreate(db: SQLiteDatabase) {
        logger.invoke("onCreate called")
        onCreateHandler.invoke(db)
    }

    private fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logger.invoke("onUpgrade called with parameters: oldVersion=$oldVersion, newVersion=$newVersion")
        ((oldVersion + 1)..newVersion).forEach {
            onMigrateHandler.invoke(db, it)
        }
    }

    private inner class SQLite(
        context: Context,
        databaseName: String,
        databaseVersion: Int
    ) : SQLiteOpenHelper(
        context, databaseName,
        null,
        databaseVersion
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            this@LiteSQLite.onCreate(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            this@LiteSQLite.onUpgrade(db, oldVersion, newVersion)
        }
    }

    class Builder(
        private val context: Context,
        private val databaseName: String,
        private val databaseVersion: Int
    ) {
        private var onCreateHandler: (db: SQLiteDatabase) -> Unit = {}
        private var onMigrateHandler: (db: SQLiteDatabase, version: Int) -> Unit = { _, _ -> }
        private var logger: (message: String) -> Unit = {}

        fun onCreate(handler: (db: SQLiteDatabase) -> Unit): Builder {
            onCreateHandler = handler
            return this
        }

        fun onMigrate(handler: (db: SQLiteDatabase, version: Int) -> Unit): Builder {
            onMigrateHandler = handler
            return this
        }

        fun setLogger(logger: (message: String) -> Unit): Builder {
            this.logger = logger
            return this
        }

        fun build(): LiteSQLite {
            return LiteSQLite(
                context = context,
                databaseName = databaseName,
                databaseVersion = databaseVersion
            ).also { target ->
                target.onCreateHandler = onCreateHandler
                target.onMigrateHandler = onMigrateHandler
                target.logger = logger
            }
        }
    }
}

inline operator fun <reified T> Cursor.get(column: String): T? {
    val columnIndex = getColumnIndex(column)
    if (columnIndex == -1) {
        // Column not found
        return null
    }

    return when (T::class) {
        Int::class -> getInt(columnIndex)
        String::class -> getString(columnIndex)
        Double::class -> getDouble(columnIndex)
        Float::class -> getFloat(columnIndex)
        Short::class -> getShort(columnIndex)
        ByteArray::class -> getBlob(columnIndex)
        Boolean::class -> getInt(columnIndex) == 1
        else -> null
    } as T?
}

fun Cursor.toMap(): Map<String, String?> {
    return columnNames.associate { column ->
        val columnIndex = getColumnIndex(column)
        if (columnIndex == -1) {
            column to null
        } else {
            column to getString(columnIndex)
        }
    }
}
