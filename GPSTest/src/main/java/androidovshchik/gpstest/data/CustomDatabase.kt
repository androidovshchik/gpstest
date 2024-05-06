package androidovshchik.gpstest.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CustomFile::class], version = 1)
abstract class CustomDatabase : RoomDatabase() {

    abstract fun customFileDao(): CustomFileDao
}