package com.nearnet.sessionlayer.logic

import android.widget.Toast
import com.google.gson.Gson
import com.nearnet.sessionlayer.data.PackageCommand
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.RoomListResponse
import com.nearnet.sessionlayer.network.SocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
//import kotlinx.coroutines.flow.internal.NoOpContinuation.context

class RoomUtils {

    fun getRoomList(): List<RoomData> {
        val packageCommand = PackageCommand(
            roomID = "",
            command = "getRoomList",
            data = ""
        )

        val response = SocketClient.sendAndReceive(packageCommand)
        println("Odp: $response")

        return try {
            val gson = Gson()
            val responseObj = gson.fromJson(response, RoomListResponse::class.java)
            responseObj.data
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getUserRoomList(userId: String): List<RoomData> {
        val packageCommand = PackageCommand(
            roomID = "",
            command = "getUserRoomList",
            data = userId
        )

        val response = SocketClient.sendAndReceive(packageCommand)
        println("Odp: $response")

        return try {
            val gson = Gson()
            val responseObj = gson.fromJson(response, RoomListResponse::class.java)
            responseObj.data
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }










}