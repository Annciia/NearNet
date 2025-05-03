package com.nearnet.sessionlayer.data.db

import androidx.room.*
import com.nearnet.sessionlayer.data.model.UserData

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserData)

    @Query("SELECT * FROM users WHERE name = :userName LIMIT 1")
    fun getUserByName(userName: String): UserData?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserData>

    @Delete
    suspend fun deleteUser(user: UserData)
}