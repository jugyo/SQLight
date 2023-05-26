# SQLight

A thin wrapper library for android.database.sqlite.SQLiteOpenHelper, providing enhanced database operation capabilities through the use of Kotlin Coroutines.

## Getting Started

### Installing SQLight

Ensure you have the following libraries as 

Just copy `sqlight/src/main/kotlin/org/jugyo/sqlight/SQLight.kt` into your project.

## Using SQLight

Create an instance of the database client as follows:

```kotlin
sqlight = SQLight.Builder(
    context,
    "Database.db",
    1
)
```

Here's an example of setting up a logger:

```kotlin
sqlight = SQLight.Builder(/* database config */)
    .setLogger {
        Log.d("SQLightTest", it)
    }
```

If you want to add a process for when the database is created:

```kotlin
sqlight = SQLight.Builder(/* database config */)
    .onCreate {
        it.execSQL(
            """
            CREATE TABLE user (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL
            )            
            """,
        )
    }
```

If you want to define migrations:

```kotlin
sqlight = SQLight.Builder(/* database config */)
    .onMigrate { db, version ->
        when (version) {
            2 -> db.execSQL("ALTER TABLE user ADD COLUMN column2 text")
            3 -> db.execSQL("ALTER TABLE user ADD COLUMN column3 text")
            4 -> db.execSQL("ALTER TABLE user ADD COLUMN column4 text")
            5 -> db.execSQL("ALTER TABLE user ADD COLUMN column5 text")
        }
    }
```
