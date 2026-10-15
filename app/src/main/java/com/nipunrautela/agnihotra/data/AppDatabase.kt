package com.nipunrautela.agnihotra.data

import androidx.room.*
import com.nipunrautela.agnihotra.core.Coordinates
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import java.util.UUID
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "places")
data class SavedPlace(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String = "Asia/Kolkata",
    val areaName: String? = null,
    @ColumnInfo(defaultValue = "0") val fromCurrentLocation: Boolean = false,
) {
    @get:Ignore val coordinates: Coordinates get() = Coordinates(latitude, longitude)
    @get:Ignore val zone: ZoneId get() = ZoneId.of(zoneId)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<SavedPlace>>
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<SavedPlace>
    @Upsert suspend fun save(place: SavedPlace)
    @Query("DELETE FROM places WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE places SET areaName = :areaName WHERE id = :id AND latitude = :latitude AND longitude = :longitude AND fromCurrentLocation = 1")
    suspend fun updateArea(id: String, latitude: Double, longitude: Double, areaName: String)
}

@Database(entities = [SavedPlace::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN areaName TEXT")
                db.execSQL("ALTER TABLE places ADD COLUMN fromCurrentLocation INTEGER NOT NULL DEFAULT 0")
                // Recognize the former automatic labels without renaming existing places.
                db.execSQL("UPDATE places SET fromCurrentLocation = 1 WHERE name IN ('Current location', 'Current Location', 'वर्तमान स्थान')")
            }
        }
    }
}
