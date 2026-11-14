/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Brings a library written by Exhale master into this build's own schema before Room opens it.
 *
 * Both are the same app — one package, one `song.db` — and an install of this build over master
 * opens master's library. The two share the schema up to version 22 and then number their own
 * changes separately: master's 25 and 27 are not this build's 25 and 27 (master has tags and two
 * playlist columns this build never had; this build has library tokens, lyric offsets and more on
 * the song). Room then ran this build's upgrades from 27 over master's tables and failed on the
 * first column that was not there: `no such column: libraryAddToken`.
 *
 * So, while the file stands at one of the versions where the two differ, every table this build
 * expects at that version is compared with what is on disk and, where they differ, rebuilt in this
 * build's shape with whatever columns the two have in common carried over — songs, artists,
 * albums, playlists, history, lyrics, all kept. Columns this build has and master did not take
 * their defaults. Tables only master has (its tags) are left alone. The views are made again and
 * the identity Room checks is set to this build's, so its own upgrades then run from there as if
 * the file had always been its own. A library that is already this build's is not touched.
 */
internal object ForeignSchemaRepair {
    private const val TAG = "ForeignSchemaRepair"

    /** The versions at which master's schema and this build's differ, with this build's for each. */
    private val DIVERGENT_VERSIONS = 23..27

    fun repairIfNeeded(context: Context, databaseName: String) {
        val file = context.getDatabasePath(databaseName)
        if (!file.exists()) return
        runCatching { repair(context, file) }
            .onFailure { Log.e(TAG, "Could not bring $databaseName into this build's schema", it) }
    }

    private fun repair(context: Context, file: File) {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val version = db.version
            if (version !in DIVERGENT_VERSIONS) return
            val schema = runCatching {
                context.assets.open("db-repair/$version.json").bufferedReader().use { it.readText() }
            }.getOrNull()?.let { JSONObject(it).getJSONObject("database") } ?: return

            val entities = schema.getJSONArray("entities")
            val mismatched = (0 until entities.length())
                .map { entities.getJSONObject(it) }
                .filter { entity -> columnsOf(db, entity.getString("tableName")) != expectedColumns(entity) }
            if (mismatched.isEmpty()) return
            Log.i(TAG, "Library at version $version is not this build's: rebuilding ${mismatched.map { it.getString("tableName") }}")

            db.execSQL("PRAGMA foreign_keys = OFF")
            db.beginTransaction()
            try {
                // Views first: a table renamed under a view that names a column it no longer has
                // is an error, and this build's views are made again at the end anyway.
                db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'view'", null).use { cursor ->
                    val views = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                    views.forEach { db.execSQL("DROP VIEW IF EXISTS `$it`") }
                }

                mismatched.forEach { entity -> rebuild(db, entity) }

                // Every expected index, on the rebuilt tables and the ones left as they were.
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }

                val views = schema.optJSONArray("views")
                if (views != null) {
                    for (i in 0 until views.length()) {
                        val view = views.getJSONObject(i)
                        val name = view.getString("viewName")
                        db.execSQL(view.getString("createSql").replace("\${VIEW_NAME}", name))
                    }
                }

                val setup = schema.optJSONArray("setupQueries")
                if (setup != null) for (i in 0 until setup.length()) db.execSQL(setup.getString(i))

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        } finally {
            db.close()
        }
    }

    /** Remakes [entity]'s table in this build's shape, carrying over the columns the two share. */
    private fun rebuild(db: SQLiteDatabase, entity: JSONObject) {
        val table = entity.getString("tableName")
        val createSql = entity.getString("createSql")
        val existing = columnsOf(db, table)
        if (existing.isEmpty()) {
            db.execSQL(createSql.replace("\${TABLE_NAME}", table))
            return
        }
        val temp = "_repair_$table"
        db.execSQL("DROP TABLE IF EXISTS `$temp`")
        db.execSQL(createSql.replace("\${TABLE_NAME}", temp))

        val fields = entity.getJSONArray("fields")
        val targets = mutableListOf<String>()
        val sources = mutableListOf<String>()
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            val column = field.getString("columnName")
            when {
                column in existing -> {
                    targets += "`$column`"
                    // A NOT NULL column that master left empty takes the column's own zero.
                    sources += if (field.optBoolean("notNull")) {
                        "COALESCE(`$column`, ${fallbackFor(field)})"
                    } else {
                        "`$column`"
                    }
                }
                // Only this build has it: its DEFAULT applies, and one with none gets a zero.
                field.optBoolean("notNull") && !field.has("defaultValue") -> {
                    targets += "`$column`"
                    sources += fallbackFor(field)
                }
            }
        }
        db.execSQL(
            "INSERT OR IGNORE INTO `$temp` (${targets.joinToString()}) " +
                "SELECT ${sources.joinToString()} FROM `$table`",
        )
        db.execSQL("DROP TABLE `$table`")
        db.execSQL("ALTER TABLE `$temp` RENAME TO `$table`")
    }

    private fun fallbackFor(field: JSONObject): String = when (field.optString("affinity")) {
        "TEXT" -> "''"
        "BLOB" -> "x''"
        else -> "0"
    }

    private fun expectedColumns(entity: JSONObject): Set<String> {
        val fields = entity.getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
    }

    private fun columnsOf(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val name = cursor.getColumnIndex("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }
}
