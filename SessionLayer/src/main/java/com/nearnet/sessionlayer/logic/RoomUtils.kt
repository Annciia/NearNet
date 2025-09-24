package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class RoomsResponse(
    val rooms: List<RoomData>
)

data class DeleteRoomResponse(
    val Succes: Boolean? = null,
    val error: String? = null
)

data class AddRoomRequest(
    val name: String,
    val avatar: String,
    val password: String,
    val isPrivate: Boolean,
    val isVisible: Boolean
)

data class UpdateRoomRequest(
    val name: String,
    val avatar: String,
    val password: String = "",
    val isPrivate: Boolean = false,
    val isVisible: Boolean = true
)

data class UserListResponse(
    val rooms: List<UserData>
)

data class GetRoomAndUsersResponse(
    val roomData: RoomData,
    val userList: UserListResponse
)

data class AddUserToRoomRequest(
    val login: String
)

data class AddUserToRoomResponse(
    @SerializedName("Succes") val success: Boolean,
    val error: String? = null
)

data class AddMyselfToRoomRequest(
    val idRoom: String,
    val password: String
)

data class AddMyselfToRoomResponse(
    @SerializedName("Succes") val success: Boolean,
    val error: String? = null
)



interface RoomApiService {

    @GET("/api/rooms")
    suspend fun getAllRooms(@Header("Authorization") token: String): Response<RoomsResponse>

    @GET("/api/rooms/mine")
    suspend fun getMyRooms(@Header("Authorization") token: String): Response<RoomsResponse>

    @POST("/api/rooms")
    suspend fun addRoom(
        @Header("Authorization") token: String,
        @Body body: AddRoomRequest
    ): Response<RoomData>

    @PUT("/api/rooms/{id}")
    suspend fun updateRoom(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateRoomRequest
    ): Response<RoomData>

    @DELETE("/api/rooms/{id}")
    suspend fun deleteRoom(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<DeleteRoomResponse>

    @GET("/api/rooms/{id}/users")
    suspend fun getRoomAndUsers(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<GetRoomAndUsersResponse>

    @POST("/api/rooms/{id}/add-user")
    suspend fun addUserToRoom(
        @Header("Authorization") token: String,
        @Path("id") roomId: String,
        @Body body: AddUserToRoomRequest
    ): Response<AddUserToRoomResponse>

    @POST("/api/rooms/join")
    suspend fun addMyselfToRoom(
        @Header("Authorization") token: String,
        @Body body: AddMyselfToRoomRequest
    ): Response<AddMyselfToRoomResponse>


}

class RoomRepository(private val context: Context) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://95.108.77.201:3001")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(RoomApiService::class.java)

    private fun getToken(): String? = UserRepository.getTokenFromPreferences(context)?.let { "Bearer $it" }

    suspend fun getAllRooms(): List<RoomData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val response = api.getAllRooms(token)
        if (response.isSuccessful) {
            response.body()?.rooms ?: emptyList()
        } else {
            Log.e("ROOM", "❌ getAllRooms failed: ${response.code()} ${response.errorBody()?.string()}")
            emptyList()
        }
    }


    suspend fun getMyRooms(): List<RoomData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val response = api.getMyRooms(token)
        if (response.isSuccessful) {
            response.body()?.rooms ?: emptyList()
        } else {
            Log.e("ROOM", "❌ getMyRooms failed: ${response.code()} ${response.errorBody()?.string()}")
            emptyList()
        }
    }

    suspend fun addRoom(room: RoomData): RoomData? = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "❌ Token jest null! Nie można dodać pokoju")
            return@withContext null
        }

        val avatarUrl = room.avatar.ifEmpty { "https://example.com/default-avatar.png" }

        val request = AddRoomRequest(
            name = room.name,
            avatar = avatarUrl,
            password = room.password,
            isPrivate = room.isPrivate,
            isVisible = room.isVisible
        )

        Log.d("ROOM", "➡️ Sending addRoom request with body: $request")

        try {
            val response = api.addRoom(token, request)

            Log.d("ROOM", "⬅️ Response code: ${response.code()}")
            Log.d("ROOM", "⬅️ Response error body: ${response.errorBody()?.string()}")
            Log.d("ROOM", "⬅️ Response success body: ${response.body()}")

            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e("ROOM", "❌ addRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }

        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in addRoom", e)
            null
        }
    }


    suspend fun updateRoom(room: RoomData): RoomData? = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext null

        val idRoom = getRoomIdByName(room.name) ?: return@withContext null
        val avatarUrl = room.avatar.ifEmpty { "https://example.com/default-avatar.png" }

        val body = UpdateRoomRequest(
            name = room.name,
            avatar = avatarUrl,
            password = room.password,
            isPrivate = room.isPrivate,
            isVisible = room.isVisible
        )

        try {
            val response = api.updateRoom(token, idRoom, body)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e("ROOM", "❌ updateRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in updateRoom", e)
            null
        }
    }



    suspend fun deleteRoom(roomName: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "❌ Token jest null! Nie można usunąć pokoju")
            return@withContext false
        }
        val idRoom = getRoomIdByName(roomName) ?: return@withContext false

        try {
            val response = api.deleteRoom(token, idRoom)

            Log.d("ROOM", "➡️ Sending deleteRoom request for id: $idRoom")
            Log.d("ROOM", "⬅️ Response code: ${response.code()}")
            Log.d("ROOM", "⬅️ Response body: ${response.body()}")
            Log.d("ROOM", "⬅️ Response error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.Succes == true
            } else {
                Log.e("ROOM", "❌ deleteRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in deleteRoom", e)
            false
        }
    }

    suspend fun getRoomAndUsers(roomName: String): Pair<RoomData, List<UserData>>? = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext null
        val idRoom = getRoomIdByName(roomName) ?: return@withContext null

        try {
            val response = api.getRoomAndUsers(token, idRoom)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    return@withContext body.roomData to body.userList.rooms
                } else {
                    Log.e("ROOM", "❌ Empty body in getRoomAndUsers")
                    return@withContext null
                }
            } else {
                Log.e("ROOM", "❌ getRoomAndUsers failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in getRoomAndUsers", e)
            null
        }
    }

    suspend fun addUserToRoom(roomName: String, login: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val idRoom = getRoomIdByName(roomName) ?: return@withContext false
        try {
            val response = api.addUserToRoom(token, idRoom, AddUserToRoomRequest(login))

            Log.d("ROOM", "➡️ Sending addUserToRoom request: roomId=$idRoom, login=$login")
            Log.d("ROOM", "⬅️ Response code: ${response.code()}")
            Log.d("ROOM", "⬅️ Response body: ${response.body()}")
            Log.d("ROOM", "⬅️ Response error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.success == true
            } else {
                Log.e("ROOM", "❌ addUserToRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in addUserToRoom", e)
            false
        }
    }


    suspend fun addMyselfToRoom(roomName: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false

        val idRoom = getRoomIdByName(roomName) ?: return@withContext false

        try {
            val response = api.addMyselfToRoom(token, AddMyselfToRoomRequest(idRoom, password))

            Log.d("ROOM", "➡️ Sending addMyselfToRoom request: idRoom=$idRoom, password=$password")
            Log.d("ROOM", "⬅️ Response code: ${response.code()}")
            Log.d("ROOM", "⬅️ Response body: ${response.body()}")
            Log.d("ROOM", "⬅️ Response error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.success == true
            } else {
                Log.e("ROOM", "❌ addMyselfToRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in addMyselfToRoom", e)
            false
        }
    }

    suspend fun getRoomIdByName(name: String): String? = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext null
        try {
            // Pobranie tylko pokoi, w których jesteś członkiem
            val myRooms = api.getMyRooms(token)
            if (myRooms.isSuccessful) {
                val rooms = myRooms.body()?.rooms ?: emptyList()
                val room = rooms.find { it.name.equals(name, ignoreCase = true) }
                room?.idRoom
            } else {
                Log.e("ROOM", "❌ getRoomIdByName failed: ${myRooms.code()} ${myRooms.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "❌ Exception in getRoomIdByName", e)
            null
        }
    }

    suspend fun addMyselfToRoomByName(name: String, password: String): Boolean {
        val idRoom = getRoomIdByName(name) ?: return false
        return addMyselfToRoom(idRoom, password)
    }

    suspend fun deleteRoomByName(name: String): Boolean {
        val idRoom = getRoomIdByName(name) ?: return false
        return deleteRoom(idRoom)
    }

    suspend fun addUserToRoomByName(name: String, login: String): Boolean {
        val idRoom = getRoomIdByName(name) ?: return false
        return addUserToRoom(idRoom, login)
    }









}




//package com.nearnet.sessionlayer.logic
//
//import android.content.Context
//import android.util.Log
//import com.google.gson.Gson
//import com.nearnet.sessionlayer.data.model.RoomData
//import io.socket.client.Ack
//import io.socket.client.Socket
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.json.JSONArray
//import kotlinx.coroutines.CompletableDeferred
//import org.json.JSONObject



//object RoomUtils {

//    //to dziala jak wg ma dzialac UserRoomList - tag "get_rooms"
//    //z tego co rozumiem ma zwracac wszystkie pokoje na serwerze
//    suspend fun getRoomList(socket: Socket): List<RoomData> = withContext(Dispatchers.IO) {
//        val deferred = CompletableDeferred<List<RoomData>>() //obiekt pozwalajacy na oczekiwanie na wynik w sposob asynchroniczny przez co nie blokuje
//
//        socket.emit("get_rooms", Ack { args ->
//            try {
//                val jsonArray = args[0] as JSONArray
//                val roomList = mutableListOf<RoomData>()
//
//                for (i in 0 until jsonArray.length()) {
//                    val obj = jsonArray.getJSONObject(i)
//                    //narazie tylko id i name z serwera odsyla
//                    val room = RoomData(
//                        idRoom = obj.optLong("id", 0L),
//                        name = obj.getString("name"),
//                        description = "",
//                        imagesSettings = "",
//                        password = "",
//                        isPrivate = false,
//                        isVisible = true,
//                        idAdmin = "",
//                        users = mutableListOf()
//                    )
//
//                    roomList.add(room)
//                }
//
//                deferred.complete(roomList)
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                deferred.complete(emptyList())
//            }
//        })
//
//        return@withContext deferred.await() //wstrzymuje wykonanie funkcji az wynik bedzie dostepny
//    }
//
//    //to powinno zwracac to co wyzej
//    suspend fun getUserRoomList(
//        userName: String,
//        userRepository: UserRepository,
//        socket: Socket)
//    : List<RoomData> = withContext(Dispatchers.IO) {
//
//        val userId = userRepository.getUserIdByName(userName)
//
//        if (userId == null) {
//            return@withContext emptyList<RoomData>()
//        }
//
//        val deferred = CompletableDeferred<List<RoomData>>()
//        //tu nie wysylac userid?
//        socket.emit("get_user_rooms", userId, Ack { args ->
//            try {
//                val jsonArray = args[0] as JSONArray
//                val roomList = mutableListOf<RoomData>()
//
//                for (i in 0 until jsonArray.length()) {
//                    val obj = jsonArray.getJSONObject(i)
//
//                    val room = RoomData(
//                        idRoom = obj.optLong("id", 0L),
//                        name = obj.getString("name"),
//                        description = obj.optString("description", ""),
//                        imagesSettings = obj.optString("imagesSettings", ""),
//                        password = obj.optString("password", ""),
//                        isPrivate = obj.optBoolean("isPrivate", false),
//                        isVisible = obj.optBoolean("isVisible", true),
//                        idAdmin = obj.optString("idAdmin", ""),
//                        users = mutableListOf()
//                    )
//
//                    roomList.add(room)
//                }
//
//                deferred.complete(roomList)
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                deferred.complete(emptyList())
//            }
//        })
//
//        return@withContext deferred.await()
//    }
//
//
//    suspend fun addRoom(
//        socket: Socket,
//        roomData: RoomData
//    ): RoomData? = withContext(Dispatchers.IO) {
//        val deferred = CompletableDeferred<RoomData?>()
//
//        try {
//            val roomJson = JSONObject().apply {
//                put("idRoom", roomData.idRoom ?: JSONObject.NULL)
//                put("name", roomData.name)
//                put("password", roomData.password)
//                put("isPrivate", roomData.isPrivate)
//                put("isVisible", roomData.isVisible)
//                put("idAdmin", roomData.idAdmin)
//            }
//
//            socket.emit("add_room", roomJson, Ack { args ->
//                try {
//                    val response = args[0] as JSONObject
//                    Log.d("AddRoom", "Server response: $response")
//
//                    if (response.getBoolean("success")) {
//                        val roomJsonResp = response.getJSONObject("room")
//                        val createdRoom = Gson().fromJson(roomJsonResp.toString(), RoomData::class.java)
//                        deferred.complete(createdRoom)
//                    } else {
//                        Log.d("AddRoom", "Room creation failed: ${response.optString("error")}")
//                        deferred.complete(null)
//                    }
//
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    deferred.complete(null)
//                }
//            })
//        } catch (e: Exception) {
//            e.printStackTrace()
//            deferred.complete(null)
//        }
//
//        return@withContext deferred.await()
//    }
//
//    suspend fun updateRoom(
//        socket: Socket,
//        roomData: RoomData
//    ): RoomData? = withContext(Dispatchers.IO) {
//        val deferred = CompletableDeferred<RoomData?>()
//
//        try {
//            val roomJson = JSONObject().apply {
//                put("idRoom", roomData.idRoom)
//                put("name", roomData.name)
//                put("description", roomData.description)
//                put("imagesSettings", roomData.imagesSettings)
//                put("password", roomData.password)
//                put("isPrivate", roomData.isPrivate)
//                put("isVisible", roomData.isVisible)
//                put("idAdmin", roomData.idAdmin)
//                put("users", JSONArray(roomData.users))
//            }
//
//            socket.emit("update_room", roomJson, Ack { args ->
//                try {
//                    val response = args[0] as JSONObject
//                    Log.d("UpdateRoom", "Server response: $response")
//
//                    if (response.getBoolean("success")) {
//                        val updatedRoomJson = response.getJSONObject("room")
//                        val updatedRoom = Gson().fromJson(updatedRoomJson.toString(), RoomData::class.java)
//                        deferred.complete(updatedRoom)
//                    } else {
//                        Log.d("UpdateRoom", "Room update failed: ${response.optString("error")}")
//                        deferred.complete(null)
//                    }
//
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    deferred.complete(null)
//                }
//            })
//        } catch (e: Exception) {
//            e.printStackTrace()
//            deferred.complete(null)
//        }
//
//        return@withContext deferred.await()
//    }
//
//
//    suspend fun deleteRoom(
//        socket: Socket,
//        roomData: RoomData
//    ): Boolean = withContext(Dispatchers.IO) {
//        val deferred = CompletableDeferred<Boolean>()
//
//        try {
//            val roomJson = JSONObject().apply {
//                put("idRoom", roomData.idRoom)
//                put("idAdmin", roomData.idAdmin)
//            }
//
//            socket.emit("delete_room", roomJson, Ack { args ->
//                try {
//                    val response = args[0] as JSONObject
//                    Log.d("DeleteRoom", "Server response: $response")
//
//                    if (response.getBoolean("success")) {
//                        deferred.complete(true)
//                    } else {
//                        Log.d("DeleteRoom", "❌ Delete failed: ${response.optString("error")}")
//                        deferred.complete(false)
//                    }
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    deferred.complete(false)
//                }
//            })
//        } catch (e: Exception) {
//            e.printStackTrace()
//            deferred.complete(false)
//        }
//
//        return@withContext deferred.await()
//    }



//}