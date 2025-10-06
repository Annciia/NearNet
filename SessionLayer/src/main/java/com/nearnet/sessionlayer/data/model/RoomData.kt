package com.nearnet.sessionlayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.nearnet.sessionlayer.data.db.Converters


@Entity(tableName = "chat_rooms")
@TypeConverters(Converters::class)
data class RoomData(
    @PrimaryKey val idRoom: String = "",
    val name: String = "",
    val description: String = "",
    val avatar: String = "",
    val password: String = "",
    val isPrivate: Boolean = false,
    val isVisible: Boolean = true,
    val idAdmin: String = "",
    val additionalSettings: String = ""
    //val users: List<String> = emptyList()
)

