package com.nearnet.sessionlayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserData(
    @PrimaryKey val idUser: String,
    val name: String,
    val avatar: String,
    val publicKey: String,
    val password: String = "",
    //val passwordHash: String,
    val darkLightMode: Boolean
)
