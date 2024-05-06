package androidovshchik.gpstest

import android.content.Context
import androidovshchik.gpstest.data.CustomDatabase
import androidx.room.Room

lateinit var roomDatabase: CustomDatabase

fun initCustomModule(context: Context) {
    roomDatabase = Room.databaseBuilder(
        context,
        CustomDatabase::class.java,
        "custom.db"
    ).build()
}