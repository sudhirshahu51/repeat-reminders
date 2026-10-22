package com.sudhirshahu.loopalarm.data

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

/**
 * Updating the app must keep people's reminders. For each earlier database version this rebuilds that version from
 * the schema Room saved in app/schemas (so it is exactly what an old install has), adds a reminder, then opens it with
 * the current code: Room runs the migrations and checks the result against the current schema, and fails otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE) // real SQLite; the fake one reports "database is locked" when a file is reopened
class MigrationTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private val enumDefaults = mapOf(
        "endMode" to "END_OF_DAY", "intervalUnit" to "MINUTES", "scheduleMode" to "DAILY", "soundType" to "BEEP",
    )

    @Test
    fun everyEarlierVersionOpensWithItsReminderIntact() {
        for (version in 1 until CURRENT_VERSION) {
            createOldDatabase(version)
            val db = AppDatabase.create(context, dbName(version))
            try {
                try {
                    db.openHelper.writableDatabase // runs the migrations and validates the schema
                } catch (e: Exception) {
                    throw AssertionError("starting from version $version: ${e.message}", e)
                }
                val alarms = runBlocking { db.alarmDao().getAll() }
                assertEquals("version $version: the reminder is kept", listOf("Old reminder"), alarms.map { it.name })
                val a = alarms.single()
                assertEquals("version $version: new settings start off", 0, a.peopleCount)
                assertEquals("version $version", "", a.peopleList)
                assertEquals("version $version", 0, a.peopleFrom)
            } finally {
                db.close()
            }
        }
    }

    private fun createOldDatabase(version: Int) {
        val schema = JSONObject(File("schemas/com.sudhirshahu.loopalarm.data.AppDatabase/$version.json").readText()).getJSONObject("database")
        // A separate file per round: Windows will not delete a database file that is still open.
        val file = context.getDatabasePath(dbName(version))
        file.parentFile?.mkdirs()
        context.deleteDatabase(dbName(version))
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i)) // room_master_table and this version's hash

            val alarms = (0 until entities.length()).map { entities.getJSONObject(it) }.first { it.getString("tableName") == "alarms" }
            val values = ContentValues()
            val fields = alarms.getJSONArray("fields")
            for (i in 0 until fields.length()) {
                val f = fields.getJSONObject(i)
                val column = f.getString("columnName")
                when {
                    column == "id" -> Unit
                    column == "name" -> values.put(column, "Old reminder")
                    column in enumDefaults -> values.put(column, enumDefaults.getValue(column))
                    f.getString("affinity") == "INTEGER" -> values.put(column, 0L)
                    else -> values.put(column, "")
                }
            }
            db.insertOrThrow("alarms", null, values)
            db.version = version
        } finally {
            db.close()
        }
    }

    private fun dbName(version: Int) = "migration-test-$version.db"

    private companion object {
        const val CURRENT_VERSION = 6
    }
}
