package com.nearnet.sessionlayer.logic

import PublicKeyManager
import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlin.io.encoding.Base64

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

data class AskForAccessRequest(
    val idRoom: String
)
data class RequestAction(
    val action: String, // "accept" lub "reject"
    val encryptedRoomKey: String = "" // zaszyfrowany klucz AES (tylko przy accept)
)

data class RoomRequestsResponse(
    val requests: List<Map<String, Any>>
)

data class RemoveUserRequest(
    val userId: String
)

data class SimpleResponse(
    val success: Boolean,
    val error: String? = null
)

data class EncryptedRoomKeyResponse(
    val encryptedRoomKey: String
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

    @POST("/api/rooms/askForAccess")
    suspend fun askForAccess(
        @Header("Authorization") token: String,
        @Body request: AskForAccessRequest
    ): Response<Unit>


    @POST("/api/rooms/{roomId}/requests/{userId}/respond")
    suspend fun respondToJoinRequest(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("userId") userId: String,
        @Body actionRequest: RequestAction
    ): Response<Unit>

    @GET("/api/rooms/{id}/requests")
    suspend fun getRoomRequests(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<RoomRequestsResponse>

    @POST("/api/rooms/{id}/remove-user")
    suspend fun removeUserFromRoom(
        @Header("Authorization") token: String,
        @Path("id") roomId: String,
        @Body body: RemoveUserRequest
    ): Response<SimpleResponse>

    @POST("/api/rooms/{id}/leave")
    suspend fun leaveRoom(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<SimpleResponse>

    //Pobieranie zaszyfrowanego klucza pokoju
    @GET("/api/rooms/{id}/encrypted-key")
    suspend fun getEncryptedRoomKey(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<EncryptedRoomKeyResponse>

    @POST("/api/rooms/{id}/set-admin")
    suspend fun updateRoomAdmin(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<SimpleResponse>

    @POST("/api/rooms/{id}/request-key-again")
    suspend fun requestKeyAgain(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<SimpleResponse>

    @POST("/api/rooms/{id}/unset-admin")
    suspend fun dropAdmin(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<SimpleResponse>


}

class RoomRepository(private val context: Context) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://$SERVER_ADDRESS:$SERVER_PORT")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(RoomApiService::class.java)

    private fun getToken(): String? = UserRepository.getTokenFromPreferences(context)?.let { "Bearer $it" }

    //wszystkie pokoje na serwerze
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

    //pokoje uzytkownika
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

    //tworzenie pokoju na serwerze
    suspend fun addRoom(
        name: String,
        description: String,
        avatar: String,
        password: String,
        isPrivate: Boolean,
        isVisible: Boolean,
        additionalSettings: String = ""
    ): RoomData? = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "Token jest null! Nie można dodać pokoju")
            return@withContext null
        }

        try {

            //generowanie klucza AES dla pokojow prywatnych
            var roomAESKeyBase64: String? = null

            if (isPrivate) {
                val roomAESKey = CryptoUtils.generateAESKey()
                roomAESKeyBase64 = CryptoUtils.aesKeyToString(roomAESKey)
            }

            //tworzenie pokoju na serwerze
            val requestBody = AddRoomRequest(
                name = name,
                description = description,
                avatar = avatar,
                password = password,
                isPrivate = isPrivate,
                isVisible = isVisible,
                additionalSettings = additionalSettings
            )

            val response = api.addRoom(token, requestBody)
            Log.d("ROOM", "HTTP response code: ${response.code()}")

            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e("ROOM", "Tworzenie pokoju nieudane!")
                Log.e("ROOM", "HTTP Code: ${response.code()}")
                Log.e("ROOM", "Error: $errorMsg")
                return@withContext null
            }

            val roomData = response.body()
            if (roomData == null) {
                Log.e("ROOM", "Brak danych pokoju w odpowiedzi")
                return@withContext null
            }

            Log.d("ROOM", "✓ Pokój utworzony pomyślnie na serwerze!")
            Log.d("ROOM", "  ID pokoju: ${roomData.idRoom}")
            Log.d("ROOM", "  Nazwa: ${roomData.name}")


            //zapisanie klucza AES lokalnie dla pokoi prywatnych
            if (isPrivate && roomAESKeyBase64 != null) {

                saveRoomAESKey(roomData.idRoom, roomAESKeyBase64)

                // weryfikacja zapisu
                val savedKey = getRoomAESKey(roomData.idRoom)
                if (savedKey != null) {
                    if (savedKey == roomAESKeyBase64) {
                        Log.d("ROOM", "Klucz identyczny z zapisanym")
                    } else {
                        Log.e("ROOM", "Klucz rozni się od zapisanego!")
                    }
                } else {
                    Log.e("ROOM", "Nie mozna odczytac klucza")
                }
            } else {
                Log.d("ROOM", "Pomijam zapisywanie klucza (pokoj publiczny)")
            }

            Log.d("ROOM", "Typ pokoju: ${if (isPrivate) "PRYWATNY (zaszyfrowany)" else "PUBLICZNY (nieszyfrowany)"}")
            return@withContext roomData

        } catch (e: Exception) {
            Log.e("ROOM", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

     //zapisanie kluczu AES pokoju w SharedPreferences
    private fun saveRoomAESKey(roomId: String, aesKeyBase64: String) {
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        prefs.edit().putString("aes_key_$roomId", aesKeyBase64).apply()
        Log.d("ROOM", "Klucz zapisany w RoomKeys: aes_key_$roomId")
    }

    //pobieranie kluczu AES pokoju z SharedPreferences
    fun getRoomAESKey(roomId: String): String? {
        Log.d("ROOM", "Pobieranie klucza AES dla pokoju: $roomId")
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        val key = prefs.getString("aes_key_$roomId", null)
        if (key != null) {
            Log.d("ROOM", "Klucz znaleziony")
        } else {
            Log.w("ROOM", "Klucz nie znaleziony")
        }
        return key
    }

    //usuwanie klucza
    private fun removeRoomAESKey(roomId: String) {
        Log.d("ROOM", "Usuwanie klucza AES dla pokoju: $roomId")
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        prefs.edit().remove("aes_key_$roomId").apply()
        Log.d("ROOM", "✓ Klucz usunięty")
    }


    //sprawdzenie czy pokoj ma zapisany klucz AES
    fun hasRoomAESKey(roomId: String): Boolean {
        val key = getRoomAESKey(roomId)
        val hasKey = key != null
        Log.d("ROOM", "Pokój $roomId ma klucz AES: $hasKey")
        return hasKey
    }

    //aktualizacja pokoju
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


    //usun pokoj
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
    //pokoj + uzytkownicy
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

    //dodanie sie do pokoju publicznego + dodanie sie do pokoju prywatnego haslem
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
            ?: identifier.takeIf { it.isNotBlank() } // jeśli uzytkownik podał ID bezpośrednio

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

    //zwraca liste uzytkownikow czekajacych na akceptacje - wariant z request zamiast hasla dla pokojow prywatnych
    suspend fun getPendingRequests(roomId: String): List<UserData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        try {
            val response = api.getRoomRequests(token, roomId)
            if (response.isSuccessful) {
                val body = response.body()
                Log.d("ROOM", "Pending requests raw: $body")
                body?.requests?.mapNotNull { req ->
                    val idUser = req["userId"]?.toString() ?: return@mapNotNull null
                    UserData(
                        id = idUser,
                        login = req["login"]?.toString() ?: "",
                        name = req["name"]?.toString() ?: "",
                        avatar = req["avatar"]?.toString() ?: "",
                        publicKey = "",
                        additionalSettings = ""
                    )
                } ?: emptyList()
            } else {
                Log.e("ROOM", "getPendingRequests failed: ${response.code()} ${response.errorBody()?.string()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in getPendingRequests", e)
            emptyList()
        }
    }

    //prosba o dolaczenie do pokoju prywatnego
    suspend fun sendJoinRequest(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "No token available")
            return@withContext false
        }

        return@withContext try {
            val response = api.askForAccess(token, AskForAccessRequest(roomId))
            Log.d("ROOM", "joinRoom response: ${response.code()} ${response.errorBody()?.string()}")
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    //odpowiedz na prosbe o dolaczenie do pokoju
    suspend fun respondToJoinRequest(
        roomId: String,
        userId: String,
        accept: Boolean
    ): Boolean = withContext(Dispatchers.IO) {

        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokena")
            return@withContext false
        }

        val action = if (accept) "accept" else "reject"

        try {
            var encryptedRoomKey = ""

            // KROK 1: Jeśli akceptujemy, sprawdź czy pokój jest prywatny
            if (accept) {
                Log.d("ROOM", "--- Krok 1: Sprawdzanie typu pokoju ---")

                // Pobierz dane pokoju
                val roomData = getRoomById(roomId)

                if (roomData != null && roomData.isPrivate) {
                    // pobranie lub wygenerowanie klucza AES pokoju
                    val roomAESKeyBase64 = getRoomAESKey(roomId)

                    if (roomAESKeyBase64 == null) {
                        val newAESKey = CryptoUtils.generateAESKey()
                        val newKeyBase64 = CryptoUtils.aesKeyToString(newAESKey)
                        saveRoomAESKey(roomId, newKeyBase64)
                    } else {
                        Log.d("ROOM", "Pokoj ma juz klucz AES")
                    }

                    // pobranie klucza publicznego uzytkownika
                    val userPublicKey = PublicKeyManager(context).getPublicKeyForUser(userId)

                    if (userPublicKey == null) {
                        Log.e("ROOM", "Nie mozna pobrac klucza publicznego uzytkownika $userId")
                        // kontynuuj bez szyfrowania - uzytkownik nie bedzie mogł czytac wiadomosci
                    } else {
                        Log.d("ROOM", "Klucz publiczny użytkownika pobrany")


                        // szyfrowanie klucza AES pokoju kluczem publicznym uzytkownika
                        val currentRoomKey = getRoomAESKey(roomId)!!
                        val roomAESKey = CryptoUtils.stringToAESKey(currentRoomKey)

                        encryptedRoomKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, userPublicKey)
                    }

                } else if (roomData != null) {
                    Log.d("ROOM", "Pokoj jest publiczny")
                } else {
                    Log.e("ROOM", "Nie mozna pobrac danych pokoju")
                }
            }

            //wyslanie odpowiedzi na serwer
            if (accept && encryptedRoomKey.isNotEmpty()) {
                Log.d("ROOM", "Wysyłam zaszyfrowany klucz: ${encryptedRoomKey.take(50)}...")
            }

            val response = api.respondToJoinRequest(
                token,
                roomId,
                userId,
                RequestAction(action, encryptedRoomKey)
            )

            Log.d("ROOM", "Response code: ${response.code()}")
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                Log.e("ROOM", "Response error: $errorBody")
            }

            val success = response.isSuccessful

            if (success) {
                Log.d("ROOM", "Odpowiedź wysłana pomyślnie")
                if (accept && encryptedRoomKey.isNotEmpty()) {
                    Log.d("ROOM", "✓ Użytkownik $userId otrzyma zaszyfrowany klucz pokoju")
                }
            } else {
                Log.e("ROOM", "Bład wysyłania odpowiedzi")
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e("ROOM", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    //pobieranie i odszyfrowanie klucza
    suspend fun fetchAndDecryptRoomKey(roomId: String): Boolean = withContext(Dispatchers.IO) {

        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "✗ Brak tokena")
            return@withContext false
        }

        try {


            //pobieranie zaszyfrowanego klucza AES z serwera
            val response = api.getEncryptedRoomKey(token, roomId)

            Log.d("ROOM", "Response code: ${response.code()}")

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "✗ Bład pobierania klucza")
                Log.e("ROOM", "  HTTP Code: ${response.code()}")
                Log.e("ROOM", "  Error: $errorBody")

                // 404 = brak klucza
                if (response.code() == 404) {
                    Log.d("ROOM", "Pokoj prawdopodobnie publiczny - brak zaszyfrowanego klucza")
                }

                return@withContext false
            }

            val encryptedRoomKey = response.body()?.encryptedRoomKey

            if (encryptedRoomKey.isNullOrEmpty()) {
                Log.e("ROOM", "Pusty zaszyfrowany klucz")
                return@withContext false
            }



            //pobieranie wlasnego klucza prywatnego
            val myLogin = UserRepository.getLoginFromPreferences(context)

            if (myLogin.isNullOrEmpty()) {
                Log.e("ROOM", "✗ Nie można pobrać własnego loginu")
                Log.e("ROOM", "  Sprawdź czy login jest zapisywany przy logowaniu")
                return@withContext false
            }

            val myPrivateKey = CryptoUtils.getPrivateKey(context, myLogin)

            if (myPrivateKey == null) {
                Log.e("ROOM", "Nie można pobrać klucza prywatnego!")
                return@withContext false
            }

            // odszyfrowywanie klucza AES pokoju
            val roomAESKey = CryptoUtils.decryptAESKeyWithRSA(encryptedRoomKey, myPrivateKey)
            val roomAESKeyBase64 = CryptoUtils.aesKeyToString(roomAESKey)

            //zapisanie klucza AES lokalnie
            saveRoomAESKey(roomId, roomAESKeyBase64)

            //weryfikacja zapisu
            val savedKey = getRoomAESKey(roomId)
            if (savedKey != null && savedKey == roomAESKeyBase64) {
                Log.d("ROOM", "klucz zapisany poprawnie")
            } else {
                Log.e("ROOM", "problem z zapisem klucza!")
            }

            return@withContext true

        } catch (e: Exception) {
            Log.e("ROOM", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    //pobieranie pokoju po ID
    private suspend fun getRoomById(roomId: String): RoomData? = withContext(Dispatchers.IO) {
        Log.d("ROOM", "Pobieranie danych pokoju: $roomId")

        try {
            val token = getToken() ?: return@withContext null

            // przeszukanie swoich pokoi
            val myRooms = getMyRooms()
            val room = myRooms.find { it.idRoom == roomId }

            if (room != null) {
                Log.d("ROOM", "✓ Znaleziono pokój w 'moich pokojach'")
                Log.d("ROOM", "  Nazwa: ${room.name}")
                Log.d("ROOM", "  isPrivate: ${room.isPrivate}")
                return@withContext room
            }

            // jesli nie przeszulanie wszystkich pokoi
            val allRooms = getAllRooms()
            val room2 = allRooms.find { it.idRoom == roomId }

            if (room2 != null) {
                Log.d("ROOM", "Znaleziono pokój w 'wszystkich pokojach'")
                return@withContext room2
            }

            Log.w("ROOM", "Nie znaleziono pokoju o ID: $roomId")
            return@withContext null

        } catch (e: Exception) {
            Log.e("ROOM", "Bład pobierania pokoju: ${e.message}", e)
            return@withContext null
        }
    }

    //zwraca id pokoju po nazwie
    suspend fun getRoomIdByName(name: String): String? = withContext(Dispatchers.IO) {
        val token = getToken() ?: run {
            Log.e("ROOM", "No token available")
            return@withContext null
        }
        try {
            //pobranie wszystkich widocznych pokoi
            val allRoomsResponse = api.getAllRooms(token)
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

    suspend fun removeUserFromRoom(roomId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false
        try {
            val response = api.removeUserFromRoom(token, roomId, RemoveUserRequest(userId))
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("ROOM", "User $userId removed from room $roomId")
                true
            } else {
                Log.e("ROOM", "removeUserFromRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in removeUserFromRoom", e)
            false
        }
    }

    suspend fun leaveRoom(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false
        try {
            val response = api.leaveRoom(token, roomId)
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("ROOM", "Successfully left room $roomId")
                true
            } else {
                Log.e("ROOM", "leaveRoom failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Exception in leaveRoom", e)
            false
        }
    }

    suspend fun updateRoomAdmin(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            return@withContext false
        }

        try {
            Log.d("ROOM", "Attempting to claim room: $roomId")

            val response = api.updateRoomAdmin(token, roomId)

            Log.d("ROOM", "updateRoomAdmin response code: ${response.code()}")

            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("ROOM", "Successfully claimed room $roomId")
                } else {
                    Log.e("ROOM", "UpdateRoomAdmin failed")
                }
                return@withContext success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "updateRoomAdmin failed: ${response.code()} $errorBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Exception in updateRoomAdmin", e)
            return@withContext false
        }
    }

    suspend fun dropAdmin(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "No token available for dropAdmin")
            return@withContext false
        }

        try {
            Log.d("ROOM", "Dropping admin status for room: $roomId")

            val response = api.dropAdmin(token, roomId)

            Log.d("ROOM", "dropAdmin response code: ${response.code()}")

            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("ROOM", "Admin status dropped successfully")
                } else {
                    Log.e("ROOM", "Server returned success=false")
                }
                return@withContext success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "dropAdmin failed: ${response.code()} $errorBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Exception in dropAdmin", e)
            return@withContext false
        }
    }

    suspend fun requestKeyAgain(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "No token available for requestKeyAgain")
            return@withContext false
        }

        try {
            Log.d("ROOM", "Requesting key again for room: $roomId")

            val response = api.requestKeyAgain(token, roomId)

            Log.d("ROOM", "requestKeyAgain response code: ${response.code()}")

            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("ROOM", "Key request sent successfully")
                    Log.d("ROOM", "Status changed to 'waitingForKey'")
                    Log.d("ROOM", "Other users will be notified to send the key")
                } else {
                    Log.e("ROOM", "Server returned success=false")
                }
                return@withContext success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "requestKeyAgain failed: ${response.code()} $errorBody")

                // Parsuj błędy:
                when (response.code()) {
                    403 -> Log.e("ROOM", " Not in room")
                    404 -> Log.e("ROOM", " No access request found")
                    400 -> Log.e("ROOM", " Can only re-request if status is 'accepted'")
                }

                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "✗ Exception in requestKeyAgain", e)
            return@withContext false
        }
    }

    suspend fun verifyRoomKeyExists(roomId: String, isPrivate: Boolean): Boolean = withContext(Dispatchers.IO) {
        Log.d("ROOM", "Verifying room key for: $roomId")


        if (!isPrivate) {
            Log.d("ROOM", "Public room - no key needed")
            return@withContext true
        }

        val hasKey = hasRoomAESKey(roomId)

        if (hasKey) {
            Log.d("ROOM", "User has room key")
            return@withContext true
        } else {
            Log.w("ROOM", "User does NOT have room key")
            return@withContext false
        }
    }



}



