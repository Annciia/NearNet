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
    val success: Boolean,
    val error: String? = null
)


data class AddRoomRequest(
    val name: String,
    val description: String = "",
    val avatar: String = "",
    val password: String = "",
    val isPrivate: Boolean = false,
    val isVisible: Boolean = true,
    val additionalSettings: String = ""
)

data class UpdateRoomRequest(
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val password: String? = null,
    val isPrivate: Boolean? = null,
    val isVisible: Boolean? = null,
    val additionalSettings: String? = null
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
    val success: Boolean, // poprawna nazwa
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
            Log.e("ROOM", "getAllRooms failed: ${response.code()} ${response.errorBody()?.string()}")
            emptyList()
        }
    }


    suspend fun getMyRooms(): List<RoomData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val response = api.getMyRooms(token)
        if (response.isSuccessful) {
            response.body()?.rooms ?: emptyList()
        } else {
            Log.e("ROOM", "getMyRooms failed: ${response.code()} ${response.errorBody()?.string()}")
            emptyList()
        }
    }


    suspend fun addRoom(name: String,
                        description: String,
                        avatar: String,
                        password: String,
                        isPrivate: Boolean,
                        isVisible: Boolean,
                        additionalSettings: String = ""): RoomData? = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "Token jest null! Nie można dodać pokoju")
            return@withContext null
        }

        val request = AddRoomRequest(
            name = name.trim(),
            description = description.trim(),
            avatar = avatar.trim(),
            password = password.trim(),
            isPrivate = isPrivate,
            isVisible = isVisible,
            additionalSettings = additionalSettings
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
                Log.e("ROOM", "addRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Exception in addRoom", e)
            null
        }
    }


    suspend fun updateRoom(room: RoomData): RoomData? = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext null

        //val idRoom = getRoomIdByName(room.name) ?: return@withContext null

        val idRoom = room.idRoom
        val body = UpdateRoomRequest(
            name = room.name,
            description = room.description,
            avatar = room.avatar.ifEmpty { "" },
            password = room.password,
            isPrivate = room.isPrivate,
            isVisible = room.isVisible
        )

        try {
            val response = api.updateRoom(token, idRoom, body)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e("ROOM", "updateRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in updateRoom", e)
            null
        }
    }



    suspend fun deleteRoom(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "Token jest null! Nie można usunąć pokoju")
            return@withContext false
        }

        try {
            val response = api.deleteRoom(token, roomId)

            Log.d("ROOM", "Sending deleteRoom request for id: $roomId")
            Log.d("ROOM", "Response code: ${response.code()}")
            Log.d("ROOM", "Response body: ${response.body()}")
            Log.d("ROOM", "Response error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.success == true
            } else {
                Log.e("ROOM", "deleteRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Exception in deleteRoom", e)
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
                    Log.e("ROOM", "Empty body in getRoomAndUsers")
                    return@withContext null
                }
            } else {
                Log.e("ROOM", "getRoomAndUsers failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in getRoomAndUsers", e)
            null
        }
    }

    //na razie nie uzywane
    suspend fun addUserToRoom(roomName: String, login: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val idRoom = getRoomIdByName(roomName) ?: return@withContext false
        try {
            val response = api.addUserToRoom(token, idRoom, AddUserToRoomRequest(login))

            Log.d("ROOM", "Sending addUserToRoom request: roomId=$idRoom, login=$login")
            Log.d("ROOM", "Response code: ${response.code()}")
            Log.d("ROOM", "Response body: ${response.body()}")
            Log.d("ROOM", "Response error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.success == true
            } else {
                Log.e("ROOM", "addUserToRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in addUserToRoom", e)
            false
        }
    }

    suspend fun addMyselfToRoom(identifier: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: run {
            Log.e("ROOM", "No token available")
            return@withContext false
        }

        // pobranie wszystkich widocznych pokoi tylko jeśli identifier wygląda jak nazwa
        val allRooms = try {
            val response = api.getAllRooms(token)
            if (response.isSuccessful) response.body()?.rooms ?: emptyList()
            else emptyList()
        } catch (e: Exception) {
            Log.e("ROOM", "Exception fetching rooms", e)
            emptyList()
        }

        // sprawdzenie, czy id jest juz ID pokoju lub szukanie po nazwie
        val idRoom = allRooms.find { it.idRoom == identifier || it.name.equals(identifier, ignoreCase = true) }?.idRoom
            ?: identifier.takeIf { it.isNotBlank() } // jeśli użytkownik podał ID bezpośrednio

        if (idRoom.isNullOrBlank()) {
            Log.e("ROOM", "No roomId found for identifier=$identifier")
            return@withContext false
        }

        try {
            val roomPassword = password.ifEmpty { "" }
            Log.d("ROOM", "Sending addMyselfToRoom request: idRoom=$idRoom, password='${if (roomPassword.isEmpty()) "" else roomPassword}'")

            val response = api.addMyselfToRoom(token, AddMyselfToRoomRequest(idRoom, roomPassword))

            Log.d("ROOM", "Response code: ${response.code()}")
            Log.d("ROOM", "Response body: ${response.body()}")
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) Log.e("ROOM", "⬅Response error body: $errorBody")

            val success = response.isSuccessful && response.body()?.success == true
            Log.d("ROOM", "Join room success = $success")
            return@withContext success

        } catch (e: Exception) {
            Log.e("ROOM", "Exception in addMyselfToRoom", e)
            return@withContext false
        }
    }




    suspend fun getRoomIdByName(name: String): String? = withContext(Dispatchers.IO) {
        val token = getToken() ?: run {
            Log.e("ROOM", "No token available")
            return@withContext null
        }
        try {
            //pobranie wszystkich widocznych pokoi (publicznych)
            val allRoomsResponse = api.getAllRooms(token) // /api/rooms
            Log.d("ROOM", "getAllRooms request sent")
            Log.d("ROOM", "Response code: ${allRoomsResponse.code()}")
            Log.d("ROOM", "Response body: ${allRoomsResponse.body()}")
            val errorBody = allRoomsResponse.errorBody()?.string()
            if (errorBody != null) Log.d("ROOM", "Response error body: $errorBody")

            if (allRoomsResponse.isSuccessful) {
                val rooms = allRoomsResponse.body()?.rooms ?: emptyList()
                Log.d("ROOM", "All visible rooms: ${rooms.map { it.name to it.idRoom }}")
                val room = rooms.find { it.name.equals(name, ignoreCase = true) }
                Log.d("ROOM", "Searching for roomName='$name', found=${room?.idRoom}")
                room?.idRoom
            } else {
                Log.e("ROOM", "getRoomIdByName failed: ${allRoomsResponse.code()} $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in getRoomIdByName", e)
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

//    suspend fun addUserToRoomByName(name: String, login: String): Boolean {
//        val idRoom = getRoomIdByName(name) ?: return false
//        return addUserToRoom(idRoom, login)
//    }









}



