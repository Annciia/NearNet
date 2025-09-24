package com.nearnet.sessionlayer.data.db

import androidx.room.*
import com.nearnet.sessionlayer.data.model.RoomData

@Dao
interface RoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomData)

    @Query("SELECT * FROM chat_rooms WHERE idRoom = :id")
    suspend fun getRoom(id: Int): RoomData?

    @Query("SELECT * FROM chat_rooms")
    suspend fun getAllRooms(): List<RoomData>

    @Delete
    suspend fun deleteRoom(room: RoomData)
}
