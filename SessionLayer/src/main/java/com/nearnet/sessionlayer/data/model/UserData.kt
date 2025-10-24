package com.nearnet.sessionlayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "users")
data class UserData(
    @PrimaryKey
    @SerializedName("idUser") val id: String,
    @SerializedName("login") val login: String,
    val name: String = "",
    val avatar: String = "",
    val publicKey: String = "",
    @SerializedName("password") val passwordHash: String = "",
    var additionalSettings: String = ""
)


