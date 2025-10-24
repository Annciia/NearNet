package com.nearnet.sessionlayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "users")
data class UserData(
    @PrimaryKey
    @SerializedName("idUser") val id: String,
    @SerializedName("login") val login: String?, // To pole dałeś jako nienull (ja dopisałam znak zapytania, by mogło być null String? bo inaczej mi się wywala),
    // bo jest czasem nullem, czy to ok? - Jak wchodzę do pokoju, to lista użytkowników którą dostaję ma to pole wpisane null - jak ok, to usuń ten komentarz ;)
    val name: String = "",
    val avatar: String = "",
    val publicKey: String = "",
    @SerializedName("password") val passwordHash: String = "",
    var additionalSettings: String = ""
)


