package dev.ambitionsoftware.tymeboxed.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.ambitionsoftware.tymeboxed.data.db.entities.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY registeredAt DESC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE tagUid = :uid)")
    suspend fun exists(uid: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
