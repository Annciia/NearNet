package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
// ============================================================================
// MODELE DANYCH - Struktury żądań i odpowiedzi API
// ============================================================================

//Odpowiedź zawierająca listę pokoi
data class RoomsResponse(
    val rooms: List<RoomData>
)
//Odpowiedź potwierdzająca usunięcie pokoju
data class DeleteRoomResponse(
    val success: Boolean,
    val error: String? = null
)

//Żądanie utworzenia nowego pokoju
data class AddRoomRequest(
    val name: String,
    val description: String = "",
    val avatar: String = "",
    val password: String = "",
    val isPrivate: Boolean = false,
    val isVisible: Boolean = true,
    val additionalSettings: String = ""
)

//Żądanie aktualizacji danych pokoju
data class UpdateRoomRequest(
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val password: String? = null,
    val isPrivate: Boolean? = null,
    val isVisible: Boolean? = null,
    val additionalSettings: String? = null
)

//Odpowiedź zawierająca listę użytkowników
data class UserListResponse(
    val rooms: List<UserData>
)

//Odpowiedź zawierająca dane pokoju i listę użytkowników
data class GetRoomAndUsersResponse(
    val roomData: RoomData,
    val userList: UserListResponse
)


//Żądanie dodania użytkownika do pokoju (przez admina)
data class AddUserToRoomRequest(
    val login: String
)

//Odpowiedź na dodanie użytkownika do pokoju
data class AddUserToRoomResponse(
    @SerializedName("Succes") val success: Boolean,
    val error: String? = null
)

//Żądanie dołączenia do pokoju (przez samego użytkownika)
data class AddMyselfToRoomRequest(
    val idRoom: String,
    val password: String
)

//Odpowiedź na dołączenie do pokoju
data class AddMyselfToRoomResponse(
    val success: Boolean,
    val error: String? = null
)

//Żądanie dostępu do pokoju prywatnego
data class AskForAccessRequest(
    val idRoom: String
)

//Akcja odpowiedzi na żądanie dostępu (akceptacja/odrzucenie)
data class RequestAction(
    val action: String, // "accept" lub "reject"
    val encryptedRoomKey: String = "" // JSON string zawierający {encryptedAESKey, encryptedPassword}
)

//Odpowiedź zawierająca żądania dostępu do pokoju
data class RoomRequestsResponse(
    val requests: List<Map<String, Any>>
)

//Żądanie usunięcia użytkownika z pokoju
data class RemoveUserRequest(
    val userId: String
)

//Prosta odpowiedź sukces/błąd
data class SimpleResponse(
    val success: Boolean,
    val error: String? = null
)

//Odpowiedź zawierająca zaszyfrowany klucz pokoju
data class EncryptedRoomKeyResponse(
    val encryptedRoomKey: String
)

//Status żądania dołączenia do pokoju
data class JoinRequestStatus(
    val status: String,
    val encryptedRoomKey: String? = null,
    val requestedAt: String? = null
)

//Żądanie deklaracji sprawdzenia hasła użytkownika
data class DeclarePasswordCheckRequest(
    val targetUserId: String
)

//Żądanie wysłania zaszyfrowanego hasła
data class SendEncryptedPasswordRequest(
    val encryptedPassword: String
)

//Odpowiedź ze statusami użytkowników w pokoju
data class RoomUsersStatusResponse(
    val roomId: String,
    val statuses: List<UserStatus>
)

//Status pojedynczego użytkownika w pokoju
data class UserStatus(
    val userId: String,
    val status: String,
    val encryptedRoomKey: String? = null,
    val requestedAt: String? = null,
    val publicKey: String? = null
)

//Żądanie wysłania klucza pokoju do użytkownika
data class SendRoomKeyRequest(
    val targetUserId: String,
    val encryptedRoomKey: String
)


// ============================================================================
// RETROFIT API SERVICE - Definicje endpointów
// ============================================================================

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

    @GET("/api/rooms/{id}/my-request")
    suspend fun getMyRequest(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<Map<String, Any>>

    @POST("/api/rooms/{id}/request-join-by-password")
    suspend fun requestJoinByPassword(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<SimpleResponse>

    @POST("/api/rooms/{id}/declare-password-check")
    suspend fun declarePasswordCheck(
        @Header("Authorization") token: String,
        @Path("id") roomId: String,
        @Body body: DeclarePasswordCheckRequest
    ): Response<SimpleResponse>

    @POST("/api/rooms/{id}/send-encrypted-password")
    suspend fun sendEncryptedPassword(
        @Header("Authorization") token: String,
        @Path("id") roomId: String,
        @Body body: SendEncryptedPasswordRequest
    ): Response<SimpleResponse>

    @GET("/api/rooms/{id}/room_users_status")
    suspend fun getRoomUsersStatus(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<RoomUsersStatusResponse>

    @POST("/api/rooms/{id}/send-room-key")
    suspend fun sendRoomKey(
        @Header("Authorization") token: String,
        @Path("id") roomId: String,
        @Body body: SendRoomKeyRequest
    ): Response<SimpleResponse>


}
// ============================================================================
// ROOM REPOSITORY - Główna klasa zarządzania pokojami
// ============================================================================

class RoomRepository(private val context: Context) {

//    private val retrofit = Retrofit.Builder()
//        .baseUrl("https://$SERVER_ADDRESS:$SERVER_PORT")
//        .addConverterFactory(GsonConverterFactory.create())
//        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.getBaseUrl(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api = retrofit.create(RoomApiService::class.java)

    /**
    * Pobiera token autoryzacyjny z UserRepository
    *
    * @return Token w formacie "Bearer {token}" lub null jeśli brak tokenu
    */
    private fun getToken(): String? = UserRepository.getTokenFromPreferences(context)?.let { "Bearer $it" }

    // ============================================================================
    // FUNKCJE ZARZĄDZANIA KLUCZAMI AES POKOJÓW
    // ============================================================================

    /**
     * Zapisuje klucz AES pokoju w SharedPreferences
     *
     * @param roomId ID pokoju
     * @param aesKeyBase64 Klucz AES w formacie Base64
     */
    private fun saveRoomAESKey(roomId: String, aesKeyBase64: String) {
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        prefs.edit().putString("aes_key_$roomId", aesKeyBase64).apply()
        Log.d("ROOM", "Klucz zapisany w RoomKeys: aes_key_$roomId")
    }

    /**
     * Pobiera klucz AES pokoju z SharedPreferences
     *
     * @param roomId ID pokoju
     * @return Klucz AES w formacie Base64 lub null jeśli nie istnieje
     */
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

    /**
     * Usuwa klucz AES pokoju z SharedPreferences
     *
     * @param roomId ID pokoju
     */
    private fun removeRoomAESKey(roomId: String) {
        Log.d("ROOM", "Usuwanie klucza AES dla pokoju: $roomId")
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        prefs.edit().remove("aes_key_$roomId").apply()
        Log.d("ROOM", "Klucz usunięty")
    }

    /**
     * Sprawdza czy pokój ma zapisany klucz AES
     *
     * @param roomId ID pokoju
     * @return true jeśli klucz istnieje, false w przeciwnym razie
     */
    fun hasRoomAESKey(roomId: String): Boolean {
        val key = getRoomAESKey(roomId)
        val hasKey = key != null
        Log.d("ROOM", "Pokój $roomId ma klucz AES: $hasKey")
        return hasKey
    }

    // ============================================================================
    // FUNKCJE ZARZĄDZANIA HASŁAMI POKOJÓW
    // ============================================================================

    /**
     * Zapisuje hasło pokoju lokalnie
     *
     * @param roomId ID pokoju
     * @param password Hasło do pokoju
     */
    private fun saveRoomPassword(roomId: String, password: String) {
        val prefs = context.getSharedPreferences("RoomPasswords", Context.MODE_PRIVATE)
        prefs.edit().putString("password_$roomId", password).apply()
        Log.d("ROOM", "Hasło zapisane dla pokoju: $roomId")
    }

    /**
     * Pobiera hasło pokoju z lokalnego storage
     *
     * @param roomId ID pokoju
     * @return Hasło pokoju lub null jeśli nie istnieje
     */
    fun getRoomPassword(roomId: String): String? {
        val prefs = context.getSharedPreferences("RoomPasswords", Context.MODE_PRIVATE)
        val password = prefs.getString("password_$roomId", null)
        if (password != null) {
            Log.d("ROOM", "Znaleziono hasło dla pokoju: $roomId")
        } else {
            Log.w("ROOM", "Brak hasła dla pokoju: $roomId")
        }
        return password
    }

    /**
     * Usuwa hasło pokoju z lokalnego storage
     *
     * @param roomId ID pokoju
     */
    private fun removeRoomPassword(roomId: String) {
        val prefs = context.getSharedPreferences("RoomPasswords", Context.MODE_PRIVATE)
        prefs.edit().remove("password_$roomId").apply()
        Log.d("ROOM", "Hasło usunięte dla pokoju: $roomId")
    }

    // ============================================================================
    // FUNKCJE POBIERANIA LISTY POKOJÓW
    // ============================================================================

    /**
     * Pobiera listę wszystkich widocznych pokojów
     *
     * @return Lista wszystkich pokojów lub pusta lista w przypadku błędu
     */
    suspend fun getAllRooms(): List<RoomData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val response = api.getAllRooms(token)
        if (response.isSuccessful) {
            response.body()?.rooms ?: emptyList()
        } else {
            Log.e("ROOM", "Błąd pobierania pokojów:: ${response.code()} ${response.errorBody()?.string()}")
            emptyList()
        }
    }

    /**
     * Pobiera listę pokojów, do których należy użytkownik
     *
     * @return Lista pokojów użytkownika lub pusta lista w przypadku błędu
     */
    suspend fun getMyRooms(): List<RoomData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val response = api.getMyRooms(token)
        if (response.isSuccessful) {
            response.body()?.rooms ?: emptyList()
        } else {
            Log.e("ROOM", "Błąd pobierania moich pokojów: ${response.code()} ${response.errorBody()?.string()}")
            emptyList()
        }
    }

    // ============================================================================
    // FUNKCJE TWORZENIA I ZARZĄDZANIA POKOJEM
    // ============================================================================

    /**
     * Tworzy nowy pokój
     *
     * Dla pokojów prywatnych:
     * - Generuje klucz AES do szyfrowania wiadomości
     * - Zapisuje klucz AES lokalnie
     * - Zapisuje hasło pokoju lokalnie
     *
     * @param name Nazwa pokoju
     * @param description Opis pokoju
     * @param avatar Avatar pokoju
     * @param password Hasło do pokoju (opcjonalne)
     * @param isPrivate Czy pokój jest prywatny (wymaga zatwierdzenia przez admina)
     * @param isVisible Czy pokój jest widoczny na liście publicznych pokojów
     * @param additionalSettings Dodatkowe ustawienia (opcjonalne)
     * @return Dane utworzonego pokoju lub null w przypadku błędu
     */
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
            Log.e("ROOM", "Brak tokenu dla addRoom")
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
                //zapis hasla przy tworzeniu pokoju
                if (password.isNotBlank()) {
                    saveRoomPassword(roomData.idRoom, password)
                    Log.d("ROOM", "Hasło pokoju zapisane lokalnie")
                }

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


    /**
     * Aktualizuje dane pokoju
     *
     * @param room Dane pokoju do aktualizacji
     * @return Zaktualizowane dane pokoju lub null w przypadku błędu
     */
    suspend fun updateRoom(room: RoomData): RoomData? = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext null

        val idRoom = room.idRoom
        val body = UpdateRoomRequest(
            name = room.name,
            description = room.description,
            avatar = room.avatar.ifEmpty { "" },
            password = room.password,
            isPrivate = room.isPrivate,
            isVisible = room.isVisible,
            additionalSettings = room.additionalSettings
        )

        try {
            val response = api.updateRoom(token, idRoom, body)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e("ROOM", "Błąd aktualizacji: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w updateRoom", e)
            null
        }
    }

    /**
     * Usuwa pokój
     *
     * @param roomId ID pokoju do usunięcia
     * @return true jeśli usunięto pomyślnie, false w przeciwnym razie
     */
    suspend fun deleteRoom(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token == null) {
            Log.e("ROOM", "Token jest null! Nie można usunąć pokoju")
            return@withContext false
        }

        try {
            val response = api.deleteRoom(token, roomId)

            Log.d("ROOM", "Usuwanie pokoju: $roomId")
            Log.d("ROOM", "Response code: ${response.code()}")
            Log.d("ROOM", "Response body: ${response.body()}")
            Log.d("ROOM", "Response error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.success == true
            } else {
                Log.e("ROOM", "Błąd usuwania pokoju: ${response.code()} ${response.errorBody()?.string()}")
                false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w deleteRoom", e)
            false
        }
    }


    // ============================================================================
    // FUNKCJE POMOCNICZE - Wyszukiwanie pokojów
    // ============================================================================

    /**
     * Pobiera ID pokoju na podstawie jego nazwy
     *
     * @param name Nazwa pokoju
     * @return ID pokoju lub null jeśli nie znaleziono
     */
    suspend fun getRoomIdByName(name: String): String? = withContext(Dispatchers.IO) {
        val token = getToken() ?: run {
            Log.e("ROOM", "Brak tokenu dla getRoomIdByName")
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
                Log.d("ROOM", "Wszystkie widoczne pokoje:: ${rooms.map { it.name to it.idRoom }}")
                val room = rooms.find { it.name.equals(name, ignoreCase = true) }
                Log.d("ROOM", "Znaleziono pokój ='$name', z ID=${room?.idRoom}")
                room?.idRoom
            } else {
                Log.e("ROOM", "Błąd szukania pokoju: ${allRoomsResponse.code()} $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w getRoomIdByName", e)
            null
        }
    }

    /**
     * Pobiera dane pokoju po jego ID (prywatna funkcja pomocnicza)
     *
     * @param roomId ID pokoju
     * @return Dane pokoju lub null jeśli nie znaleziono
     */
    private suspend fun getRoomById(roomId: String): RoomData? = withContext(Dispatchers.IO) {
        Log.d("ROOM", "Pobieranie danych pokoju: $roomId")

        try {
            val token = getToken() ?: return@withContext null

            // przeszukanie swoich pokoi
            val myRooms = getMyRooms()
            val room = myRooms.find { it.idRoom == roomId }

            if (room != null) {
                Log.d("ROOM", "Znaleziono pokój w 'moich pokojach'")
                Log.d("ROOM", "Nazwa: ${room.name}")
                Log.d("ROOM", "isPrivate: ${room.isPrivate}")
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


    /**
     * Pobiera dane pokoju i listę użytkowników
     *
     * @param roomName Nazwa pokoju
     * @return Para (dane pokoju, lista użytkowników) lub null w przypadku błędu
     */
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
                    Log.e("ROOM", "Pusta odpowiedź z serwera")
                    return@withContext null
                }
            } else {
                Log.e("ROOM", "Błąd pobierania: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w getRoomAndUsers", e)
            null
        }
    }

    // ============================================================================
    // FUNKCJE DOŁĄCZANIA DO POKOJU
    // ============================================================================

    /**
     * Dołącza do pokoju publicznego z hasłem lub prywatnego po akceptacji
     *
     * Obsługuje dołączanie przez:
     * - ID pokoju
     * - Nazwę pokoju
     *
     * @param identifier ID lub nazwa pokoju
     * @param password Hasło do pokoju (puste jeśli brak hasła)
     * @return true jeśli dołączono pomyślnie, false w przeciwnym razie
     */
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
            Log.e("ROOM", "Wyjątek pobierania pokoi", e)
            emptyList()
        }

        // sprawdzenie, czy id jest juz ID pokoju lub szukanie po nazwie
        val idRoom = allRooms.find { it.idRoom == identifier || it.name.equals(identifier, ignoreCase = true) }?.idRoom
            ?: identifier.takeIf { it.isNotBlank() }

        if (idRoom.isNullOrBlank()) {
            Log.e("ROOM", "Nie znaleziono pokoju dla identyfikatora: $identifier")
            return@withContext false
        }

        try {
            val roomPassword = password.ifEmpty { "" }
            Log.d("ROOM", "Dołączanie do pokoju:")
            Log.d("ROOM", "  ID pokoju: $idRoom")
            Log.d("ROOM", "  Hasło: ${if (roomPassword.isEmpty()) "BRAK" else "PODANO"}")

            val response = api.addMyselfToRoom(token, AddMyselfToRoomRequest(idRoom, roomPassword))

            Log.d("ROOM", "HTTP response code: ${response.code()}")
            Log.d("ROOM", "Response body: ${response.body()}")
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) Log.e("ROOM", "Response error body: $errorBody")

            val success = response.isSuccessful && response.body()?.success == true
            Log.d("ROOM", "Pomyślnie dołączono do pokoju = $success")
            return@withContext success

        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w addMyselfToRoom", e)
            return@withContext false
        }
    }

    /**
     * Prosi o dostęp do pokoju prywatnego (wymaga zatwierdzenia przez admina)
     *
     * @param roomId ID pokoju
     * @return true jeśli żądanie zostało wysłane, false w przeciwnym razie
     */
    suspend fun sendJoinRequest(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla sendJoinRequest")
            return@withContext false
        }

        return@withContext try {
            val response = api.askForAccess(token, AskForAccessRequest(roomId))
            Log.d("ROOM", "Błąd wysyłania prośby: ${response.code()} ${response.errorBody()?.string()}")
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    // ============================================================================
    // FUNKCJE OBSŁUGI ŻĄDAŃ DOSTĘPU (DLA ADMINÓW)
    // ============================================================================

    /**
     * Pobiera listę oczekujących żądań dostępu do pokoju (dla admina)
     *
     * @param roomId ID pokoju
     * @return Lista użytkowników oczekujących na dostęp lub pusta lista w przypadku błędu
     */
    suspend fun getPendingRequests(roomId: String): List<UserData> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        try {
            val response = api.getRoomRequests(token, roomId)
            if (response.isSuccessful) {
                val body = response.body()
                Log.d("ROOM", "Dane żądań pending: $body")
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
                Log.e("ROOM", "Błąd pobierania żądań: ${response.code()} ${response.errorBody()?.string()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w getPendingRequests", e)
            emptyList()
        }
    }


    /**
     * Odpowiada na żądanie dostępu użytkownika (rozszerzona wersja z szyfrowaniem)
     *
     * Dla akceptacji w pokoju prywatnym:
     * - Pobiera klucz AES pokoju i hasło
     * - Szyfruje je kluczem publicznym użytkownika
     * - Wysyła zaszyfrowane dane w formacie JSON
     *
     * @param roomId ID pokoju
     * @param userId ID użytkownika
     * @param accept true = akceptuj, false = odrzuć
     * @return true jeśli odpowiedź została wysłana, false w przeciwnym razie
     */
    suspend fun respondToJoinRequest(
        roomId: String,
        userId: String,
        accept: Boolean
    ): Boolean = withContext(Dispatchers.IO) {

        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla respondToJoinRequest")
            return@withContext false
        }

        val action = if (accept) "accept" else "reject"

        try {
            var encryptedData = ""

            if (accept) {
                Log.d("ROOM", "--- Przygotowywanie danych do wysłania ---")

                val roomData = getRoomById(roomId)

                if (roomData != null && roomData.isPrivate) {
                    // Pobierz klucz AES pokoju
                    val roomAESKeyBase64 = getRoomAESKey(roomId)

                    if (roomAESKeyBase64 == null) {
                        Log.e("ROOM", "Nie mam klucza AES pokoju!")
                        return@withContext false
                    }

                    // Pobierz hasło pokoju
                    val roomPassword = getRoomPassword(roomId)

                    if (roomPassword == null) {
                        Log.w("ROOM", "⚠Nie mam hasła pokoju")
                        return@withContext false
                    }

                    // Pobierz klucz publiczny użytkownika
                    val userPublicKey = PublicKeyManager(context).getPublicKeyForUser(userId)

                    if (userPublicKey == null) {
                        Log.e("ROOM", "Nie można pobrać klucza publicznego użytkownika $userId")
                        return@withContext false
                    }

                    Log.d("ROOM", "Klucz publiczny użytkownika pobrany")

                    // Zaszyfruj klucz AES
                    val roomAESKey = CryptoUtils.stringToAESKey(roomAESKeyBase64)
                    val encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, userPublicKey)

                    // Zaszyfruj hasło pokoju
                    val encryptedPassword = CryptoUtils.encryptStringWithRSA(roomPassword, userPublicKey)

                    // stworz JSOIN z kluczem AES i hasłem
                    val jsonData = JSONObject().apply {
                        put("encryptedAESKey", encryptedAESKey)
                        put("encryptedPassword", encryptedPassword)
                    }

                    encryptedData = jsonData.toString()

                    Log.d("ROOM", "JSON utworzony:")
                    Log.d("ROOM", "  - encryptedAESKey: ${encryptedAESKey.take(50)}...")
                    Log.d("ROOM", "  - encryptedPassword: ${encryptedPassword.take(50)}...")

                } else if (roomData != null) {
                    Log.d("ROOM", "Pokój jest publiczny")
                } else {
                    Log.e("ROOM", "Nie można pobrać danych pokoju")
                }
            }

            // Wyślij odpowiedź na serwer (JSON jako string w polu encryptedRoomKey)
            val response = api.respondToJoinRequest(
                token,
                roomId,
                userId,
                RequestAction(action, encryptedData)
            )

            Log.d("ROOM", "Response code: ${response.code()}")

            val success = response.isSuccessful

            if (success) {
                Log.d("ROOM", "JSON wysłany pomyślnie")
            } else {
                Log.e("ROOM", "Błąd wysyłania")
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e("ROOM", "Błąd: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Sprawdza status własnej prośby o dołączenie do pokoju
     *
     * @param roomId ID pokoju
     * @return Status żądania lub null w przypadku błędu
     */
    suspend fun checkMyJoinRequest(roomId: String): JoinRequestStatus? = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla checkMyJoinRequest")
            return@withContext null
        }

        try {
            val response = api.getMyRequest(token, roomId)

            if (!response.isSuccessful) {
                Log.e("ROOM", "Błąd sprawdzania statusu: ${response.code()}")
                return@withContext null
            }

            val body = response.body() ?: return@withContext null

            val status = body["status"] as? String ?: return@withContext null
            val encryptedKey = body["encryptedRoomKey"] as? String
            val requestedAt = body["requestedAt"] as? String

            Log.d("ROOM", "Status mojej prośby: $status")

            return@withContext JoinRequestStatus(status, encryptedKey, requestedAt)

        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w checkMyJoinRequest", e)
            return@withContext null
        }
    }

    // ============================================================================
    // FUNKCJE ZARZĄDZANIA KLUCZAMI POKOJU
    // ============================================================================


    /**
     * Pobiera i odszyfrowuje klucz pokoju oraz hasło
     *
     * Obsługuje dwa formaty:
     * 1. Nowy format JSON: {"encryptedAESKey": "...", "encryptedPassword": "..."}
     * 2. Stary format: samo encryptedRoomKey //używane przed zmianami przesyłania hasła
     *
     * Proces:
     * - Pobiera zaszyfrowane dane z serwera lub używa dostarczonych
     * - Rozpoznaje format (JSON vs stary format)
     * - Odszyfrowuje klucz AES kluczem prywatnym użytkownika
     * - Zapisuje klucz AES lokalnie
     * - Odszyfrowuje i zapisuje hasło (jeśli istnieje)
     *
     * @param roomId ID pokoju
     * @param providedEncryptedData Opcjonalne zaszyfrowane dane (jeśli już pobrane)
     * @return true jeśli klucz został poprawnie odszyfrowany i zapisany, false w przeciwnym razie
     */
    suspend fun fetchAndDecryptRoomKey(
        roomId: String,
        providedEncryptedData: String? = null
    ): Boolean = withContext(Dispatchers.IO) {

        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla fetchAndDecryptRoomKey")
            return@withContext false
        }

        try {
            // Użyj dostarczonych danych lub pobierz z serwera
            val encryptedDataJson = if (providedEncryptedData != null && providedEncryptedData.isNotEmpty()) {
                Log.d("ROOM", "Używam dostarczonego JSON")
                providedEncryptedData
            } else {
                Log.d("ROOM", "Pobieram dane z serwera...")

                val response = api.getEncryptedRoomKey(token, roomId)

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ROOM", "Błąd pobierania: ${response.code()} $errorBody")
                    return@withContext false
                }

                val data = response.body()?.encryptedRoomKey

                if (data.isNullOrEmpty()) {
                    Log.e("ROOM", "Puste dane z serwera")
                    return@withContext false
                }
                Log.d("ROOM", "Dane pobrane z serwera")
                data
            }

            // sprawdza czy to JSON czy stary format z samym kluczem AES
            val isJson = encryptedDataJson.trim().startsWith("{")

            val encryptedAESKey: String
            val encryptedPassword: String?

            if (isJson) {
                // Nowy format - parsuj JSON
                val jsonObject = try {
                    JSONObject(encryptedDataJson)
                } catch (e: Exception) {
                    Log.e("ROOM", "Błąd parsowania JSON", e)
                    return@withContext false
                }

                encryptedAESKey = jsonObject.optString("encryptedAESKey", "")
                encryptedPassword = jsonObject.optString("encryptedPassword", null)

                if (encryptedAESKey.isEmpty()) {
                    Log.e("ROOM", "Brak encryptedAESKey w JSON")
                    return@withContext false
                }

                Log.d("ROOM", "JSON sparsowany:")
                Log.d("ROOM", "  - encryptedAESKey: ${encryptedAESKey.take(50)}...")
                Log.d("ROOM", "  - encryptedPassword: ${if (encryptedPassword.isNullOrEmpty()) "BRAK" else "EXISTS"}")
            } else {
                // Stary format - samo encryptedRoomKey
                Log.d("ROOM", "Stary format - samo encryptedRoomKey")
                encryptedAESKey = encryptedDataJson
                encryptedPassword = null
            }

            // Pobierz login użytkownika
            val myLogin = UserRepository.getLoginFromPreferences(context)

            if (myLogin.isNullOrEmpty()) {
                Log.e("ROOM", "Nie można pobrać loginu")
                return@withContext false
            }

            // Pobierz klucz prywatny
            val myPrivateKey = CryptoUtils.getPrivateKey(context, myLogin)

            if (myPrivateKey == null) {
                Log.e("ROOM", "Nie można pobrać klucza prywatnego!")
                return@withContext false
            }
            Log.d("ROOM", "✓ Klucz prywatny pobrany")

            // Odszyfruj klucz AES
            val roomAESKey = CryptoUtils.decryptAESKeyWithRSA(encryptedAESKey, myPrivateKey)
            val roomAESKeyBase64 = CryptoUtils.aesKeyToString(roomAESKey)

            // Zapisz klucz AES lokalnie
            saveRoomAESKey(roomId, roomAESKeyBase64)
            Log.d("ROOM", "Klucz AES zapisany")

            // Odszyfruj hasło
            if (encryptedPassword != null && encryptedPassword.isNotEmpty()) {
                try {
                    val decryptedPassword = CryptoUtils.decryptStringWithRSA(encryptedPassword, myPrivateKey)
                    saveRoomPassword(roomId, decryptedPassword)
                    Log.d("ROOM", "Hasło pokoju odszyfrowane i zapisane")
                } catch (e: Exception) {
                    Log.e("ROOM", "Błąd deszyfrowania hasła", e)

                }
            }

            // Weryfikacja zapisu
            val savedKey = getRoomAESKey(roomId)
            if (savedKey != null && savedKey == roomAESKeyBase64) {
                Log.d("ROOM", "Wszystko zapisane poprawnie!")
            } else {
                Log.e("ROOM", "Problem z weryfikacją zapisanego klucza")
                return@withContext false
            }

            return@withContext true

        } catch (e: Exception) {
            Log.e("ROOM", "Błąd: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }


    /**
     * Weryfikuje czy użytkownik posiada klucz pokoju
     *
     * @param roomId ID pokoju
     * @param isPrivate Czy pokój jest prywatny
     * @return true jeśli użytkownik ma klucz (lub pokój jest publiczny), false w przeciwnym razie
     */
    suspend fun verifyRoomKeyExists(roomId: String, isPrivate: Boolean): Boolean = withContext(Dispatchers.IO) {
        Log.d("ROOM", "Weryfikacja klucza pokoju: $roomId")


        if (!isPrivate) {
            Log.d("ROOM", "Pokój publiczny - klucz nie jest wymagany")
            return@withContext true
        }

        val hasKey = hasRoomAESKey(roomId)

        if (hasKey) {
            Log.d("ROOM", "Użytkownik posiada klucz pokoju")
            return@withContext true
        } else {
            Log.w("ROOM", "Użytkownik NIE posiada klucza pokoju")
            return@withContext false
        }
    }


    // ============================================================================
    // FUNKCJE ZARZĄDZANIA UŻYTKOWNIKAMI W POKOJU
    // ============================================================================

    /**
     * Usuwa użytkownika z pokoju (funkcja dla admina)
     *
     * @param roomId ID pokoju
     * @param userId ID użytkownika do usunięcia
     * @return true jeśli usunięto pomyślnie, false w przeciwnym razie
     */
    suspend fun removeUserFromRoom(roomId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false
        try {
            val response = api.removeUserFromRoom(token, roomId, RemoveUserRequest(userId))
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("ROOM", "Użytkownik $userId usunięty z pokoju $roomId")
                true
            } else {
                Log.e("ROOM", "Błąd usuwania użytkownika: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w removeUserFromRoom", e)
            false
        }
    }


    /**
     * Opuszcza pokój (funkcja dla zwykłego użytkownika)
     *
     * @param roomId ID pokoju do opuszczenia
     * @return true jeśli opuszczono pomyślnie, false w przeciwnym razie
     */
    suspend fun leaveRoom(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false
        try {
            val response = api.leaveRoom(token, roomId)
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("ROOM", "Pomyślnie opuszczono pokój $roomId")
                true
            } else {
                Log.e("ROOM", "Błąd opuszczania pokoju: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w leaveRoom", e)
            false
        }
    }

    // ============================================================================
    // FUNKCJE ZARZĄDZANIA ROLAMI
    // ============================================================================

    /**
     * Ustawia użytkownika jako admina pokoju
     *
     * @param roomId ID pokoju
     * @return true jeśli operacja się powiodła, false w przeciwnym razie
     */
    suspend fun updateRoomAdmin(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            return@withContext false
        }

        try {
            Log.d("ROOM", "Ustawianie roli admina dla pokoju: $roomId")

            val response = api.updateRoomAdmin(token, roomId)

            Log.d("ROOM", "updateRoomAdmin response code: ${response.code()}")

            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("ROOM", "Rola admina ustawiona pomyślnie $roomId")
                } else {
                    Log.e("ROOM", "Niepowodzenie przy ustalaniu roli admina")
                }
                return@withContext success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "Błąd ustawiania roli admina: ${response.code()} $errorBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w updateRoomAdmin", e)
            return@withContext false
        }
    }

    /**
     * Usuwa rolę admina użytkownika w pokoju
     *
     * @param roomId ID pokoju
     * @return true jeśli operacja się powiodła, false w przeciwnym razie
     */
    suspend fun dropAdmin(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla dropAdmin")
            return@withContext false
        }

        try {
            Log.d("ROOM", "Usuwanie roli admina dla pokoju: $roomId")

            val response = api.dropAdmin(token, roomId)

            Log.d("ROOM", "dropAdmin response code: ${response.code()}")

            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("ROOM", "Rola admina usunięta pomyślnie")
                } else {
                    Log.e("ROOM", "Niepowodzenie w usunięciu roli admina")
                }
                return@withContext success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "Błąd usuwania roli admina: ${response.code()} $errorBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w dropAdmin\"", e)
            return@withContext false
        }
    }

    /**
     * Prosi ponownie o klucz pokoju (gdy użytkownik stracił klucz, np przy zmianie telefonu)
     *
     * @param roomId ID pokoju
     * @return true jeśli żądanie zostało wysłane, false w przeciwnym razie
     */
    suspend fun requestKeyAgain(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla requestKeyAgain")
            return@withContext false
        }

        try {
            Log.d("ROOM", "Proszenie ponownie o klucz dla pokoju: $roomId")

            val response = api.requestKeyAgain(token, roomId)

            Log.d("ROOM", "requestKeyAgain response code: ${response.code()}")

            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("ROOM", "Żądanie klucza wysłane pomyślnie")
                    Log.d("ROOM", "Status zmieniony na 'waitingForKey'")
                    Log.d("ROOM", "Inni użytkownicy zostaną powiadomieni")
                } else {
                    Log.e("ROOM", "Serwer zwrócił success=false")
                }
                return@withContext success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ROOM", "Błąd żądania klucza: ${response.code()} $errorBody")

                // Parsuj błędy:
                when (response.code()) {
                    403 -> Log.e("ROOM", " Użytkownik nie należy do pokoju")
                    404 -> Log.e("ROOM", " Nie znaleziono żądania dostępu")
                    400 -> Log.e("ROOM", " Można prosić ponownie tylko gdy status='accepted'")
                }

                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("ROOM", "✗ Wyjątek w requestKeyAgain", e)
            return@withContext false
        }
    }


    // ============================================================================
    // FUNKCJE AUTOMATYCZNEJ WERYFIKACJI HASŁA
    // ============================================================================

    /**
     * Wysyła żądanie dołączenia do pokoju z hasłem (dla automatycznej weryfikacji)
     *
     * @param roomId ID pokoju
     * @return true jeśli żądanie zostało wysłane, false w przeciwnym razie
     */
    suspend fun requestJoinByPassword(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) {
            Log.e("ROOM", "Brak tokenu dla requestJoinByPassword")
            return@withContext false
        }

        try {
            val response = api.requestJoinByPassword(token, roomId)
            val success = response.isSuccessful && response.body()?.success == true

            if (success) {
                Log.d("ROOM", "Żądanie dołączenia z hasłem wysłane")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w requestJoinByPassword", e)
            return@withContext false
        }
    }

    /**
     * Deklaruje chęć sprawdzenia hasła użytkownika
     *
     * @param roomId ID pokoju
     * @param targetUserId ID użytkownika do weryfikacji
     * @return true jeśli deklaracja została wysłana, false w przeciwnym razie
     */
    suspend fun declarePasswordCheck(roomId: String, targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false

        try {
            val response = api.declarePasswordCheck(
                token,
                roomId,
                DeclarePasswordCheckRequest(targetUserId)
            )

            val success = response.isSuccessful && response.body()?.success == true

            if (success) {
                Log.d("ROOM", "Deklaracja sprawdzenia hasła dla użytkownika: $targetUserId")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w declarePasswordCheck", e)
            return@withContext false
        }
    }


    /**
     * Wysyła zaszyfrowane hasło do użytkownika oczekującego na weryfikację
     *
     * @param roomId ID pokoju
     * @param encryptedPassword Zaszyfrowane hasło
     * @return true jeśli hasło zostało wysłane, false w przeciwnym razie
     */
    suspend fun sendEncryptedPassword(roomId: String, encryptedPassword: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false

        try {
            val response = api.sendEncryptedPassword(
                token,
                roomId,
                SendEncryptedPasswordRequest(encryptedPassword)
            )

            val success = response.isSuccessful && response.body()?.success == true

            if (success) {
                Log.d("ROOM", "Zaszyfrowane hasło wysłane")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w sendEncryptedPassword", e)
            return@withContext false
        }
    }

    /**
     * Pobiera listę użytkowników oczekujących na weryfikację w pokoju
     *
     * @param roomId ID pokoju
     * @return Lista statusów użytkowników lub pusta lista w przypadku błędu
     */
    suspend fun getRoomUsersStatus(roomId: String): List<UserStatus> = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext emptyList()

        try {
            val response = api.getRoomUsersStatus(token, roomId)

            if (response.isSuccessful) {
                val statuses = response.body()?.statuses ?: emptyList()
                Log.d("ROOM", "Pobrano statusy ${statuses.size} użytkowników oczekujących na weryfikację")
                return@withContext statuses
            } else {
                Log.e("ROOM", "Błąd pobierania statusów: ${response.code()}")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w getRoomUsersStatus", e)
            return@withContext emptyList()
        }
    }

    /**
     * Wysyła zaszyfrowany klucz pokoju do użytkownika
     *
     * @param roomId ID pokoju
     * @param targetUserId ID użytkownika docelowego
     * @param encryptedDataJson JSON zawierający zaszyfrowany klucz AES i hasło
     * @return true jeśli klucz został wysłany, false w przeciwnym razie
     */
    suspend fun sendRoomKeyToUser(
        roomId: String,
        targetUserId: String,
        encryptedDataJson: String
    ): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isNullOrBlank()) return@withContext false

        try {
            val response = api.sendRoomKey(
                token,
                roomId,
                SendRoomKeyRequest(targetUserId, encryptedDataJson)
            )

            val success = response.isSuccessful && response.body()?.success == true

            if (success) {
                Log.d("ROOM", "✓ JSON z kluczem i hasłem wysłany do użytkownika $targetUserId")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e("ROOM", "Wyjątek w sendRoomKeyToUser", e)
            return@withContext false
        }
    }



}



