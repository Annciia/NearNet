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

//    @POST("api/rooms/{roomId}/requests/{userId}/respond")
//    suspend fun respondToJoinRequest(
//        @Header("Authorization") token: String,
//        @Path("roomId") roomId: String,
//        @Path("userId") userId: String,
//        @Body actionRequest: RequestAction
//    ): Response<Unit>

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






}

class RoomRepository(private val context: Context) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://95.108.77.201:3002")
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


//    suspend fun addRoom(name: String,
//                        description: String,
//                        avatar: String,
//                        password: String,
//                        isPrivate: Boolean,
//                        isVisible: Boolean,
//                        additionalSettings: String = ""): RoomData? = withContext(Dispatchers.IO) {
//        val token = getToken()
//        if (token == null) {
//            Log.e("ROOM", "Token jest null! Nie można dodać pokoju")
//            return@withContext null
//        }
//
//        val request = AddRoomRequest(
//            name = name.trim(),
//            description = description.trim(),
//            avatar = avatar.trim(),
//            password = password.trim(),
//            isPrivate = isPrivate,
//            isVisible = isVisible,
//            additionalSettings = additionalSettings
//        )
//
//        Log.d("ROOM", "➡️ Sending addRoom request with body: $request")
//
//        try {
//            val response = api.addRoom(token, request)
//
//            Log.d("ROOM", "⬅️ Response code: ${response.code()}")
//            Log.d("ROOM", "⬅️ Response error body: ${response.errorBody()?.string()}")
//            Log.d("ROOM", "⬅️ Response success body: ${response.body()}")
//
//            if (response.isSuccessful) {
//                response.body()
//            } else {
//                Log.e("ROOM", "addRoom failed: ${response.code()} ${response.errorBody()?.string()}")
//                null
//            }
//
//        } catch (e: Exception) {
//            Log.e("ROOM", "Exception in addRoom", e)
//            null
//        }
//    }

    suspend fun addRoom(
        name: String,
        description: String,
        avatar: String,
        password: String,
        isPrivate: Boolean,
        isVisible: Boolean,
        additionalSettings: String = ""
    ): RoomData? = withContext(Dispatchers.IO) {
        Log.d("ROOM", "====== TWORZENIE POKOJU ======")
        Log.d("ROOM", "Nazwa pokoju: $name")
        Log.d("ROOM", "Prywatny: $isPrivate")
        Log.d("ROOM", "Widoczny: $isVisible")

        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "✗ Token jest null! Nie można dodać pokoju")
            return@withContext null
        }

        try {
            // ============================================
            // KROK 1: GENEROWANIE KLUCZA AES (TYLKO DLA PRYWATNYCH POKOI)
            // ============================================
            var roomAESKeyBase64: String? = null

            if (isPrivate) {
                Log.d("ROOM", "--- Krok 1: Generowanie klucza AES (pokój prywatny) ---")
                val roomAESKey = CryptoUtils.generateAESKey()
                roomAESKeyBase64 = CryptoUtils.aesKeyToString(roomAESKey)
                Log.d("ROOM", "✓ Klucz AES pokoju wygenerowany")
                Log.d("ROOM", "  Długość Base64: ${roomAESKeyBase64.length} znaków")
                Log.d("ROOM", "  Pierwsze 30 znaków: ${roomAESKeyBase64.take(30)}...")
                Log.d("ROOM", "  Ostatnie 30 znaków: ...${roomAESKeyBase64.takeLast(30)}")
            } else {
                Log.d("ROOM", "--- Krok 1: Pomijam generowanie klucza (pokój publiczny) ---")
                Log.d("ROOM", "  Pokój publiczny - wiadomości NIE będą szyfrowane")
            }

            // ============================================
            // KROK 2: TWORZENIE POKOJU NA SERWERZE
            // ============================================
            Log.d("ROOM", "--- Krok 2: Tworzenie pokoju na serwerze ---")
            val requestBody = AddRoomRequest(
                name = name,
                description = description,
                avatar = avatar,
                password = password,
                isPrivate = isPrivate,
                isVisible = isVisible,
                additionalSettings = additionalSettings
            )

            Log.d("ROOM", "Wysyłanie żądania...")
            val response = api.addRoom(token, requestBody)
            Log.d("ROOM", "HTTP response code: ${response.code()}")

            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e("ROOM", "✗ Tworzenie pokoju nieudane!")
                Log.e("ROOM", "  HTTP Code: ${response.code()}")
                Log.e("ROOM", "  Error: $errorMsg")
                return@withContext null
            }

            val roomData = response.body()
            if (roomData == null) {
                Log.e("ROOM", "✗ Brak danych pokoju w odpowiedzi")
                return@withContext null
            }

            Log.d("ROOM", "✓ Pokój utworzony pomyślnie na serwerze!")
            Log.d("ROOM", "  ID pokoju: ${roomData.idRoom}")
            Log.d("ROOM", "  Nazwa: ${roomData.name}")

            // ============================================
            // KROK 3: ZAPISANIE KLUCZA AES LOKALNIE (TYLKO DLA PRYWATNYCH)
            // ============================================
            if (isPrivate && roomAESKeyBase64 != null) {
                Log.d("ROOM", "--- Krok 3: Zapisanie klucza AES lokalnie ---")

                saveRoomAESKey(roomData.idRoom, roomAESKeyBase64)

                Log.d("ROOM", "✓ Klucz AES zapisany w SharedPreferences")
                Log.d("ROOM", "  Klucz dla pokoju: ${roomData.idRoom}")

                // Weryfikacja zapisu
                Log.d("ROOM", "--- Weryfikacja zapisu ---")
                val savedKey = getRoomAESKey(roomData.idRoom)
                if (savedKey != null) {
                    Log.d("ROOM", "✓ Weryfikacja pozytywna - klucz można odczytać")
                    if (savedKey == roomAESKeyBase64) {
                        Log.d("ROOM", "✓✓ Klucz identyczny z zapisanym")
                    } else {
                        Log.e("ROOM", "✗✗ Klucz różni się od zapisanego!")
                    }
                } else {
                    Log.e("ROOM", "✗ Weryfikacja negatywna - nie można odczytać klucza!")
                }
            } else {
                Log.d("ROOM", "--- Krok 3: Pomijam zapisywanie klucza (pokój publiczny) ---")
            }

            Log.d("ROOM", "====== TWORZENIE POKOJU ZAKOŃCZONE SUKCESEM ======")
            Log.d("ROOM", "Typ pokoju: ${if (isPrivate) "PRYWATNY (zaszyfrowany)" else "PUBLICZNY (nieszyfrowany)"}")
            return@withContext roomData

        } catch (e: Exception) {
            Log.e("ROOM", "✗✗ WYJĄTEK podczas tworzenia pokoju!", e)
            Log.e("ROOM", "  Typ: ${e.javaClass.simpleName}")
            Log.e("ROOM", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            Log.d("ROOM", "====== TWORZENIE POKOJU ZAKOŃCZONE BŁĘDEM ======")
            return@withContext null
        }
    }

    /**
     * Zapisuje klucz AES pokoju w SharedPreferences
     */
    private fun saveRoomAESKey(roomId: String, aesKeyBase64: String) {
        Log.d("ROOM", "Zapisywanie klucza AES...")
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        prefs.edit().putString("aes_key_$roomId", aesKeyBase64).apply()
        Log.d("ROOM", "✓ Klucz zapisany w RoomKeys: aes_key_$roomId")
    }

    /**
     * Pobiera klucz AES pokoju z SharedPreferences
     */
    fun getRoomAESKey(roomId: String): String? {
        Log.d("ROOM", "Pobieranie klucza AES dla pokoju: $roomId")
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        val key = prefs.getString("aes_key_$roomId", null)
        if (key != null) {
            Log.d("ROOM", "✓ Klucz znaleziony (długość: ${key.length})")
        } else {
            Log.w("ROOM", "⚠ Klucz nie znaleziony")
        }
        return key
    }

    /**
     * Usuwa klucz AES pokoju z SharedPreferences
     */
    private fun removeRoomAESKey(roomId: String) {
        Log.d("ROOM", "Usuwanie klucza AES dla pokoju: $roomId")
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        prefs.edit().remove("aes_key_$roomId").apply()
        Log.d("ROOM", "✓ Klucz usunięty")
    }

    /**
     * Sprawdza czy pokój ma zapisany klucz AES
     */
    fun hasRoomAESKey(roomId: String): Boolean {
        val key = getRoomAESKey(roomId)
        val hasKey = key != null
        Log.d("ROOM", "Pokój $roomId ma klucz AES: $hasKey")
        return hasKey
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

    //prosba o dolaczenie
    suspend fun sendJoinRequest(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "No token available")
            return@withContext false
        }
        Log.d("ROOM", "Sending join request with token: $token")

        return@withContext try {
            val response = api.askForAccess(token, AskForAccessRequest(roomId))
            Log.d("ROOM", "joinRoom response: ${response.code()} ${response.errorBody()?.string()}")
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

//    suspend fun respondToJoinRequest(roomId: String, userId: String, accept: Boolean): Boolean =
//        withContext(Dispatchers.IO) {
//            val token = getToken()
//            if (token.isNullOrBlank()) {
//                Log.e("ROOM", "No token available")
//                return@withContext false
//            }
//
//            val action = if (accept) "accept" else "reject"
//
//            Log.d("ROOM", "Sending respondToJoinRequest: roomId=$roomId, userId=$userId, action=$action")
//
//
//            return@withContext try {
//                val response = api.respondToJoinRequest(token, roomId, userId, RequestAction(action))
//                Log.d("ROOM", "Respond request: ${response.code()} ${response.errorBody()?.string()}")
//                response.isSuccessful
//            } catch (e: Exception) {
//                Log.e("ROOM", "Error in respondToJoinRequest", e)
//                false
//            }
//        }

    suspend fun respondToJoinRequest(
        roomId: String,
        userId: String,
        accept: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("ROOM", "====== ODPOWIEDŹ NA PROŚBĘ O DOŁĄCZENIE ======")
        Log.d("ROOM", "RoomId: $roomId")
        Log.d("ROOM", "UserId: $userId")
        Log.d("ROOM", "Accept: $accept")

        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "✗ Brak tokena")
            return@withContext false
        }

        val action = if (accept) "accept" else "reject"

        try {
            var encryptedRoomKey = ""

            // ============================================
            // KROK 1: Jeśli akceptujemy, sprawdź czy pokój jest prywatny
            // ============================================
            if (accept) {
                Log.d("ROOM", "--- Krok 1: Sprawdzanie typu pokoju ---")

                // Pobierz dane pokoju
                val roomData = getRoomById(roomId)

                if (roomData != null && roomData.isPrivate) {
                    Log.d("ROOM", "🔒 Pokój jest PRYWATNY - generuję zaszyfrowany klucz")

                    // ============================================
                    // KROK 2: Pobierz lub wygeneruj klucz AES pokoju
                    // ============================================
                    Log.d("ROOM", "--- Krok 2: Pobieranie klucza AES pokoju ---")

                    val roomAESKeyBase64 = getRoomAESKey(roomId)

                    if (roomAESKeyBase64 == null) {
                        // Pokój nie ma jeszcze klucza - wygeneruj nowy
                        Log.d("ROOM", "⚠ Pokój nie ma klucza - generuję nowy...")
                        val newAESKey = CryptoUtils.generateAESKey()
                        val newKeyBase64 = CryptoUtils.aesKeyToString(newAESKey)
                        saveRoomAESKey(roomId, newKeyBase64)
                        Log.d("ROOM", "✓ Nowy klucz AES wygenerowany i zapisany")
                    } else {
                        Log.d("ROOM", "✓ Pokój ma już klucz AES")
                    }

                    // ============================================
                    // KROK 3: Pobierz klucz publiczny użytkownika
                    // ============================================
                    Log.d("ROOM", "--- Krok 3: Pobieranie klucza publicznego użytkownika ---")

                    val userPublicKey = PublicKeyManager(context).getPublicKeyForUser(userId)

                    if (userPublicKey == null) {
                        Log.e("ROOM", "✗ Nie można pobrać klucza publicznego użytkownika $userId")
                        Log.e("ROOM", "  Użytkownik może nie mieć klucza RSA!")
                        // Kontynuuj bez szyfrowania - użytkownik nie będzie mógł czytać wiadomości
                    } else {
                        Log.d("ROOM", "✓ Klucz publiczny użytkownika pobrany")

                        // ============================================
                        // KROK 4: Zaszyfruj klucz AES pokoju kluczem publicznym użytkownika
                        // ============================================
                        Log.d("ROOM", "--- Krok 4: Szyfrowanie klucza AES ---")

                        val currentRoomKey = getRoomAESKey(roomId)!!
                        val roomAESKey = CryptoUtils.stringToAESKey(currentRoomKey)

                        encryptedRoomKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, userPublicKey)

                        Log.d("ROOM", "✓ Klucz AES zaszyfrowany kluczem publicznym użytkownika")
                        Log.d("ROOM", "  Długość zaszyfrowanego klucza: ${encryptedRoomKey.length} znaków")
                    }

                } else if (roomData != null) {
                    Log.d("ROOM", "🌐 Pokój jest PUBLICZNY - bez szyfrowania klucza")
                } else {
                    Log.e("ROOM", "✗ Nie można pobrać danych pokoju")
                }
            }

            // ============================================
            // KROK 5: Wyślij odpowiedź na serwer
            // ============================================
            Log.d("ROOM", "--- Krok ${if (accept) "5" else "2"}: Wysyłanie odpowiedzi na serwer ---")
            Log.d("ROOM", "Action: $action")
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
                Log.d("ROOM", "✓✓ Odpowiedź wysłana pomyślnie")
                if (accept && encryptedRoomKey.isNotEmpty()) {
                    Log.d("ROOM", "✓ Użytkownik $userId otrzyma zaszyfrowany klucz pokoju")
                }
            } else {
                Log.e("ROOM", "✗ Błąd wysyłania odpowiedzi")
            }

            Log.d("ROOM", "====== KONIEC ODPOWIEDZI ======")
            return@withContext success

        } catch (e: Exception) {
            Log.e("ROOM", "✗✗ WYJĄTEK w respondToJoinRequest", e)
            Log.e("ROOM", "  Typ: ${e.javaClass.simpleName}")
            Log.e("ROOM", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun fetchAndDecryptRoomKey(roomId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("ROOM", "====== POBIERANIE KLUCZA POKOJU ======")
        Log.d("ROOM", "RoomId: $roomId")

        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "✗ Brak tokena")
            return@withContext false
        }

        try {
            // ============================================
            // KROK 1: Pobierz zaszyfrowany klucz z serwera
            // ============================================
            Log.d("ROOM", "--- Krok 1: Pobieranie zaszyfrowanego klucza ---")

            val response = api.getEncryptedRoomKey(token, roomId)

            Log.d("ROOM", "Response code: ${response.code()}")

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "✗ Błąd pobierania klucza")
                Log.e("ROOM", "  HTTP Code: ${response.code()}")
                Log.e("ROOM", "  Error: $errorBody")

                // 404 = brak klucza (prawdopodobnie pokój publiczny)
                if (response.code() == 404) {
                    Log.d("ROOM", "⚠ Pokój prawdopodobnie publiczny - brak zaszyfrowanego klucza")
                }

                return@withContext false
            }

            val encryptedRoomKey = response.body()?.encryptedRoomKey

            if (encryptedRoomKey.isNullOrEmpty()) {
                Log.e("ROOM", "✗ Pusty zaszyfrowany klucz")
                return@withContext false
            }

            Log.d("ROOM", "✓ Zaszyfrowany klucz pobrany")
            Log.d("ROOM", "  Długość: ${encryptedRoomKey.length} znaków")
            Log.d("ROOM", "  Pierwsze 50 znaków: ${encryptedRoomKey.take(50)}...")

            // ============================================
            // KROK 2: Pobierz własny klucz prywatny RSA
            // ============================================
            Log.d("ROOM", "--- Krok 2: Pobieranie klucza prywatnego ---")

            // Pobierz login użytkownika
            val myLogin = UserRepository.getLoginFromPreferences(context)

            if (myLogin.isNullOrEmpty()) {
                Log.e("ROOM", "✗ Nie można pobrać własnego loginu")
                Log.e("ROOM", "  Sprawdź czy login jest zapisywany przy logowaniu")
                return@withContext false
            }

            Log.d("ROOM", "Mój login: $myLogin")

            val myPrivateKey = CryptoUtils.getPrivateKey(context, myLogin)

            if (myPrivateKey == null) {
                Log.e("ROOM", "✗ Nie można pobrać klucza prywatnego!")
                Log.e("ROOM", "  Czy masz wygenerowane klucze RSA?")
                Log.e("ROOM", "  Login używany jako identyfikator: $myLogin")
                return@withContext false
            }

            Log.d("ROOM", "✓ Klucz prywatny RSA pobrany")

            // ============================================
            // KROK 3: Odszyfruj klucz AES pokoju
            // ============================================
            Log.d("ROOM", "--- Krok 3: Deszyfrowanie klucza AES ---")

            val roomAESKey = CryptoUtils.decryptAESKeyWithRSA(encryptedRoomKey, myPrivateKey)
            val roomAESKeyBase64 = CryptoUtils.aesKeyToString(roomAESKey)

            Log.d("ROOM", "✓ Klucz AES odszyfrowany pomyślnie!")
            Log.d("ROOM", "  Długość klucza AES: ${roomAESKeyBase64.length} znaków")

            // ============================================
            // KROK 4: Zapisz klucz AES lokalnie
            // ============================================
            Log.d("ROOM", "--- Krok 4: Zapisanie klucza lokalnie ---")

            saveRoomAESKey(roomId, roomAESKeyBase64)

            Log.d("ROOM", "✓ Klucz AES zapisany w SharedPreferences")

            // Weryfikacja
            Log.d("ROOM", "--- Weryfikacja zapisu ---")
            val savedKey = getRoomAESKey(roomId)
            if (savedKey != null && savedKey == roomAESKeyBase64) {
                Log.d("ROOM", "✓✓ Weryfikacja pozytywna - klucz zapisany poprawnie")
            } else {
                Log.e("ROOM", "✗✗ Weryfikacja negatywna - problem z zapisem!")
            }

            Log.d("ROOM", "====== POBIERANIE KLUCZA ZAKOŃCZONE SUKCESEM ======")
            return@withContext true

        } catch (e: Exception) {
            Log.e("ROOM", "✗✗ WYJĄTEK podczas pobierania klucza!", e)
            Log.e("ROOM", "  Typ: ${e.javaClass.simpleName}")
            Log.e("ROOM", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    private suspend fun getRoomById(roomId: String): RoomData? = withContext(Dispatchers.IO) {
        Log.d("ROOM", "Pobieranie danych pokoju: $roomId")

        try {
            val token = getToken() ?: return@withContext null

            // Spróbuj pobrać z moich pokoi
            val myRooms = getMyRooms()
            val room = myRooms.find { it.idRoom == roomId }

            if (room != null) {
                Log.d("ROOM", "✓ Znaleziono pokój w 'moich pokojach'")
                Log.d("ROOM", "  Nazwa: ${room.name}")
                Log.d("ROOM", "  isPrivate: ${room.isPrivate}")
                return@withContext room
            }

            // Jeśli nie znaleziono, spróbuj z wszystkich pokoi
            val allRooms = getAllRooms()
            val room2 = allRooms.find { it.idRoom == roomId }

            if (room2 != null) {
                Log.d("ROOM", "✓ Znaleziono pokój w 'wszystkich pokojach'")
                return@withContext room2
            }

            Log.w("ROOM", "⚠ Nie znaleziono pokoju o ID: $roomId")
            return@withContext null

        } catch (e: Exception) {
            Log.e("ROOM", "Błąd pobierania pokoju: ${e.message}", e)
            return@withContext null
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

}



