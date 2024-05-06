package androidovshchik.gpstest.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CustomFileDao {

    @Query("""
        SELECT * FROM custom_files
        WHERE uploaded = 0
    """)
    fun getUploadFiles(): List<CustomFile>

    @Insert
    fun insertFile(file: CustomFile)

    @Update
    fun updateFile(file: CustomFile)
}