package androidovshchik.gpstest.data

import android.annotation.SuppressLint
import androidx.annotation.NonNull
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@SuppressLint("KotlinNullnessAnnotation")
@Entity(tableName = "custom_files")
data class CustomFile(
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "last_modified")
    val lastModified: Long,
    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
) {

    constructor(file: File) : this(file.path, file.lastModified())
}