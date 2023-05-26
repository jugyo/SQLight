package org.jugyo.litesqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

data class User(val id: Int, val name: String, val gender: String)

fun map(cursor: Cursor) = User(
    id = cursor["id"] ?: 0,
    name = cursor["name"] ?: "",
    gender = cursor["gender"] ?: "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LiteSQLiteTest {
    private lateinit var liteSQLite: LiteSQLite

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("TestDatabase.db")

        liteSQLite = LiteSQLite.Builder(
            context,
            "TestDatabase.db",
            1
        )
            .setLogger {
                Log.d("LiteSQLiteTest", it)
            }
            .onCreate {
                it.execSQL(
                    """
                    CREATE TABLE user (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        gender TEXT NOT NULL
                    )            
                    """,
                )
            }
            .build()

        liteSQLite.execSQL(
            """DELETE FROM user;""",
            """INSERT INTO user (id, name, gender) VALUES (0, 'John Doe', 'Male');""",
            """INSERT INTO user (id, name, gender) VALUES (1, 'Sam Smith', 'Male');""",
            """INSERT INTO user (id, name, gender) VALUES (2, 'Jane Doe', 'Female');""",
            """INSERT INTO user (id, name, gender) VALUES (3, 'Emma Brown', 'Female');"""
        )
    }

    @Test
    fun rawQuery() = runTest {
        var records = liteSQLite.rawQuery("SELECT * FROM user ORDER BY id", ::map)
        assertEquals(4, records.size)
        assertEquals(User(id = 0, name = "John Doe", gender = "Male"), records[0])
        assertEquals(User(id = 1, name = "Sam Smith", gender = "Male"), records[1])
        assertEquals(User(id = 2, name = "Jane Doe", gender = "Female"), records[2])
        assertEquals(User(id = 3, name = "Emma Brown", gender = "Female"), records[3])

        records = liteSQLite.rawQuery("SELECT * FROM user WHERE id = ?", listOf("1"), ::map)
        assertEquals(1, records.size)
        assertEquals(User(id = 1, name = "Sam Smith", gender = "Male"), records[0])
    }

    @Test
    fun rawQueryWithSelectionArgs() = runTest {
        val records = liteSQLite.rawQuery("SELECT * FROM user WHERE id = ?", listOf("0"), ::map)
        assertEquals(1, records.size)
        assertEquals(User(id = 0, name = "John Doe", gender = "Male"), records[0])
    }

    @Test
    fun rawQueryThrowsExceptionOnSqlSyntaxError() = runTest {
        assertThrows(SQLiteException::class.java) {
            runBlocking {
                liteSQLite.rawQuery("SELECT", ::map)
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                liteSQLite.rawQuery("SELECT * FROM user", listOf("1"), ::map)
            }
        }
    }

    @Test
    fun execSQL() = runTest {
        liteSQLite.execSQL(
            """INSERT INTO user (id, name, gender) VALUES (4, 'user1', 'Male');""",
            """INSERT INTO user (id, name, gender) VALUES (5, 'user2', 'Female');""",
        )
        val count = liteSQLite.rawQuery("SELECT count(*) FROM user") {
            it.getInt(0)
        }.first()

        assertEquals(6, count)
    }

    @Test
    fun execSQLWithBindArgs() = runTest {
        liteSQLite.execSQL(
            """INSERT INTO user (id, name, gender) VALUES (?, ?, ?);""",
            listOf("4", "user1", "Male")
        )
        val record = liteSQLite.rawQuery("SELECT * FROM user WHERE id = 4", ::map).first()

        assertEquals(User(id = 4, name = "user1", gender = "Male"), record)
    }

    @Test
    fun execSQLThrowsExceptionOnSqlSyntaxError() = runTest {
        assertThrows(SQLiteException::class.java) {
            runBlocking {
                liteSQLite.execSQL("""INSERT INTO user (id, name""")
            }
        }
    }

    @Test
    fun migration() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("TestDatabase-2.db")

        fun create(version: Int): LiteSQLite {
            return LiteSQLite.Builder(
                context,
                "TestDatabase-2.db",
                version
            )
                .setLogger {
                    Log.d("LiteSQLiteTest", it)
                }
                .onCreate { db ->
                    db.execSQL("CREATE TABLE table1( id integer PRIMARY KEY AUTOINCREMENT, column1 text NOT NULL);")
                }
                .onMigrate { db, version ->
                    when (version) {
                        2 -> db.execSQL("ALTER TABLE table1 ADD COLUMN column2 text;")
                        3 -> db.execSQL("ALTER TABLE table1 ADD COLUMN column3 text;")
                        4 -> db.execSQL("ALTER TABLE table1 ADD COLUMN column4 text;")
                        5 -> db.execSQL("ALTER TABLE table1 ADD COLUMN column5 text;")
                    }
                }
                .build()
        }

        data class Column(
            val name: String,
            val type: String,
            val notnull: Boolean,
            val dflt_value: String?,
            val pk: Boolean
        )

        fun map(cursor: Cursor): Column {
            return Column(
                name = cursor["name"] ?: "",
                type = cursor["type"] ?: "",
                notnull = cursor["notnull"] ?: false,
                dflt_value = cursor["dflt_value"],
                pk = cursor["pk"] ?: false
            )
        }

        var liteSQLite = create(version = 1)
        assertEquals(
            listOf("column1"),
            liteSQLite.rawQuery("PRAGMA table_info(table1)", ::map).map { it.name }
        )

        liteSQLite = create(version = 2)
        assertEquals(
            listOf("column1", "column2"),
            liteSQLite.rawQuery("PRAGMA table_info(table1)", ::map).map { it.name }
        )

        liteSQLite = create(version = 3)
        assertEquals(
            listOf("column1", "column2", "column3"),
            liteSQLite.rawQuery("PRAGMA table_info(table1)", ::map).map { it.name }
        )

        liteSQLite = create(version = 5)
        assertEquals(
            listOf("column1", "column2", "column3", "column4", "column5"),
            liteSQLite.rawQuery("PRAGMA table_info(table1)", ::map).map { it.name }
        )
    }
}
