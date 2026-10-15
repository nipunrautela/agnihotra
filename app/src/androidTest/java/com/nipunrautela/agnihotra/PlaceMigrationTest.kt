package com.nipunrautela.agnihotra

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nipunrautela.agnihotra.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaceMigrationTest {
    @Test fun versionOnePlacesSurviveTheAreaMetadataMigration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE places (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, zoneId TEXT NOT NULL)")
            db.execSQL("INSERT INTO places VALUES ('home', 'Current location', 28.6139, 77.209, 'Asia/Kolkata')")
            db.execSQL("INSERT INTO places VALUES ('custom', 'My home', 19.076, 72.8777, 'Asia/Kolkata')")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            val places = database.places().all()
            assertEquals(2, places.size)
            val current = places.single { it.id == "home" }
            assertEquals(28.6139, current.latitude, 0.0)
            assertEquals("Current location", current.name)
            assertTrue(current.fromCurrentLocation)
            assertNull(current.areaName)
            assertFalse(places.single { it.id == "custom" }.fromCurrentLocation)
            database.places().updateArea("home", 0.0, 0.0, "Wrong area")
            assertNull(database.places().all().single { it.id == "home" }.areaName)
            database.places().updateArea("home", current.latitude, current.longitude, "New Delhi")
            assertEquals("New Delhi", database.places().all().single { it.id == "home" }.areaName)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
