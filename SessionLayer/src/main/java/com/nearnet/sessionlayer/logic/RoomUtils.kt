package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.nearnet.sessionlayer.data.PackageCommand
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.RoomListResponse
import com.nearnet.sessionlayer.network.SocketClient
import io.socket.client.Ack
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlinx.coroutines.CompletableDeferred
import com.nearnet.sessionlayer.logic.UserRepository
import org.json.JSONObject



object RoomUtils {

    //to dziala jak wg ma dzialac UserRoomList - tag "get_rooms"
    //z tego co rozumiem ma zwracac wszystkie pokoje na serwerze
    suspend fun getRoomList(socket: Socket): List<RoomData> = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<List<RoomData>>() //obiekt pozwalajacy na oczekiwanie na wynik w sposob asynchroniczny przez co nie blokuje

        socket.emit("get_rooms", Ack { args ->
            try {
                val jsonArray = args[0] as JSONArray
                val roomList = mutableListOf<RoomData>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    //narazie tylko id i name z serwera odsyla
                    val room = RoomData(
                        idRoom = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        description = "",
                        imagesSettings = "",
                        password = "",
                        isPrivate = false,
                        isVisible = true,
                        idAdmin = "",
                        users = mutableListOf()
                    )

                    roomList.add(room)
                }

                deferred.complete(roomList)

            } catch (e: Exception) {
                e.printStackTrace()
                deferred.complete(emptyList())
            }
        })

        return@withContext deferred.await() //wstrzymuje wykonanie funkcji az wynik bedzie dostepny
    }

    //to powinno zwracac to co wyzej
    suspend fun getUserRoomList(
        userName: String,
        userRepository: UserRepository,
        socket: Socket)
    : List<RoomData> = withContext(Dispatchers.IO) {

        val userId = userRepository.getUserIdByName(userName)

        if (userId == null) {
            return@withContext emptyList<RoomData>()
        }

        val deferred = CompletableDeferred<List<RoomData>>()

        socket.emit("get_user_rooms", userId, Ack { args ->
            try {
                val jsonArray = args[0] as JSONArray
                val roomList = mutableListOf<RoomData>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    val room = RoomData(
                        idRoom = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        imagesSettings = obj.optString("imagesSettings", ""),
                        password = obj.optString("password", ""),
                        isPrivate = obj.optBoolean("isPrivate", false),
                        isVisible = obj.optBoolean("isVisible", true),
                        idAdmin = obj.optString("idAdmin", ""),
                        users = mutableListOf()
                    )

                    roomList.add(room)
                }

                deferred.complete(roomList)

            } catch (e: Exception) {
                e.printStackTrace()
                deferred.complete(emptyList())
            }
        })

        return@withContext deferred.await()
    }

    //tworzy pokoj - uzytkownik musi byc zautoryzowany tokenem
    suspend fun createRoom(
        context: Context,
        socket: Socket,
        roomData: RoomData
    ): String = withContext(Dispatchers.IO) {

        val deferred = CompletableDeferred<String>()
        //val token = UserRepository.getTokenFromPreferences(context)

        val roomJson = JSONObject().apply {
            put("name", roomData.name)
            put("password", roomData.password)
            //put("token", token)
        }

        socket.emit("create_room", roomJson, Ack { args ->
            try {
                val response = args[0] as JSONObject
                Log.d("CreateRoom", "Server response: $response")

                if (response.getBoolean("success")) {
                    socket.emit("join_all_rooms")
                    val roomId = response.optString("roomId", "")
                    Log.d("CreateRoom", "Room created with ID: $roomId")
                    deferred.complete(roomId)
                } else {
                    Log.d("CreateRoom", "Room creation failed")
                    deferred.complete("")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                deferred.complete("")
            }
        })

        return@withContext deferred.await()
    }


}