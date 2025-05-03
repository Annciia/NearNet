package com.nearnet.sessionlayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.nearnet.sessionlayer.data.db.Converters

@Entity(tableName = "chat_rooms")
@TypeConverters(Converters::class)
data class RoomData(
    @PrimaryKey(autoGenerate = true) val idRoom: Long = 0L,  // Zmieniamy typ na Long
    var name: String,
    var description: String,
    var imagesSettings: String,
    var password: String,
    var isPrivate: Boolean,
    var isVisible: Boolean,
    val idAdmin: String,
    val users: List<String>
)

