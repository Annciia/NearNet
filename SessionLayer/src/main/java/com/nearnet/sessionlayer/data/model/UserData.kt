package com.nearnet.sessionlayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserData(
    @PrimaryKey val id: String,
    val login: String,
    val name: String,
    val avatar: String,
    val publicKey: String,
    val passwordHash: String = "",
    var additionalSettings: String,
)


