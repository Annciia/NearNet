package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.IOException
import javax.crypto.SecretKey

// ============================================================================
// MODELE DANYCH - Struktury żądań i odpowiedzi API wiadomości
// ============================================================================

//Żądanie wysłania wiadomości do pokoju
data class SendMessageRequest(
    val channelId: String,
    val messageList: List<MessagePayload>
)

//Pojedyncza wiadomość w pakiecie
data class MessagePayload(
    val userId: String? = null,
    val timestamp: String,
    val messageType: String = "TEXT",
    val data: String,
    val additionalData: String = ""
)

//Odpowiedź serwera na wysłanie wiadomości
data class SendMessageResponse(
    val success: Boolean,
    val error: String? = null
)

//Żądanie historii wiadomości z pokoju
data class RequestLastMessagesRequest(
    val idRoom: String
)

//Odpowiedź zawierająca historię wiadomości
data class RequestLastMessagesResponse(
    val roomData: RoomData?,
    val login: String,
    val `package`: PackageData
)

//Pakiet wiadomości z serwera
data class PackageData(
    val channelId: String,
    val encryptedPassword: String,
    val messageList: List<MessagePayload>
)

//Odpowiedź zawierająca listę użytkowników pokoju
data class RoomUsersResponse(
    val roomData: RoomData,
    val userList: UserListWrapper
)

//Wrapper dla listy użytkowników
data class UserListWrapper(
    val rooms: List<UserData>
)

// ============================================================================
// RETROFIT API SERVICE - Definicje endpointów wiadomości
// ============================================================================

interface MessageApiService {
    @POST("/api/messages/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body body: SendMessageRequest
    ): Response<SendMessageResponse>

    @POST("/api/messages/request-last")
    suspend fun requestLastMessages(
        @Header("Authorization") token: String,
        @Body body: RequestLastMessagesRequest
    ): Response<RequestLastMessagesResponse>

    @GET("/api/rooms/{id}/users")
    suspend fun getRoomUsers(
        @Header("Authorization") token: String,
        @Path("id") roomId: String
    ): Response<RoomUsersResponse>
}

// ============================================================================
// MESSAGE UTILS - Główna klasa zarządzania wiadomościami
// ============================================================================

/**
 * Singleton do zarządzania wiadomościami w aplikacji
 *
 * Obsługuje:
 * - Wysyłanie wiadomości (zaszyfrowanych i niezaszyfrowanych)
 * - Odbieranie wiadomości w czasie rzeczywistym (SSE)
 * - Automatyczne szyfrowanie/deszyfrowanie wiadomości
 * - Pobieranie historii wiadomości
 * - Zarządzanie kluczami AES dla pokojów
 * - Pobieranie listy użytkowników pokoju
 */
object MessageUtils {

    private var tokenProvider: (() -> String?)? = null
    private var contextProvider: (() -> Context?)? = null

    /**
     * Inicjalizacja MessageUtils z providerami tokenu i kontekstu
     *
     * @param tokenProv Lambda dostarczająca token autoryzacyjny
     * @param contextProv Lambda dostarczająca kontekst aplikacji
     */
    fun init(tokenProv: () -> String?, contextProv: () -> Context?) {
        tokenProvider = tokenProv
        contextProvider = contextProv
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://$SERVER_ADDRESS:$SERVER_PORT")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(MessageApiService::class.java)
    private val client = OkHttpClient()
    private val gson = Gson()

    // Zarządzanie SSE Stream
    private var sseThread: Thread? = null
    @Volatile private var running = false
    private var activeRoomId: String? = null

    // ============================================
    // FUNKCJE POMOCNICZE - Zarządzanie kluczami AES
    // ============================================


    /**
     * Pobiera klucz AES dla pokoju z SharedPreferences
     *
     * @param roomId ID pokoju
     * @return Klucz AES lub null jeśli nie znaleziono
     */
    private fun getRoomAESKey(context: Context, roomId: String): SecretKey? {
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        val keyString = prefs.getString("aes_key_$roomId", null)

        return if (keyString != null) {
            try {
                CryptoUtils.stringToAESKey(keyString)
            } catch (e: Exception) {
                Log.e("MESSAGE", "Błąd odczytu klucza AES: ${e.message}")
                null
            }
        } else {
            null
        }
    }


    /**
     * Sprawdza czy pokój ma klucz AES (czy jest zaszyfrowany)
     *
     * @param roomId ID pokoju
     * @return true jeśli pokój ma klucz (jest zaszyfrowany), false jeśli publiczny
     */
    private fun hasRoomKey(context: Context, roomId: String): Boolean {
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        return prefs.contains("aes_key_$roomId")
    }

    // ============================================================================
    // FUNKCJE WYSYŁANIA WIADOMOŚCI
    // ============================================================================

    /**
     * Wysyła wiadomość do pokoju
     *
     * @param roomId ID pokoju docelowego
     * @param message Obiekt wiadomości do wysłania
     * @return true jeśli wysłanie się powiodło, false w przeciwnym razie
     */
    suspend fun sendMessage(roomId: String, message: Message): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext false
        val context = contextProvider?.invoke() ?: return@withContext false


        // sprawdzenie czy pokoj ma klucz szyfrowania
        val roomKey = getRoomAESKey(context, roomId)

        val messageToSend = if (roomKey != null) {

            try {
                val encrypted = CryptoUtils.encryptMessage(message.message, roomKey)
                encrypted
            } catch (e: Exception) {
                Log.e("MESSAGE", "Błąd szyfrowania wiadomości: ${e.message}", e)
                return@withContext false
            }
        } else {
            // POKÓJ NIEZASZYFROWANY (publiczny)
            Log.d("MESSAGE", "Pokoj niezaszyfrowany - wysyłam plaintext")
            message.message
        }

        // wyslanie wiadomosci na serwer
        val payload = MessagePayload(
            userId = message.userId,
            timestamp = message.timestamp,
            messageType = message.messageType,
            data = messageToSend, // zaszyfrowana lub plaintext
            additionalData = message.additionalData
        )
        //wyslanie na serwer
        val request = SendMessageRequest(
            channelId = roomId,
            messageList = listOf(payload)
        )

        try {
            val response = api.sendMessage("Bearer $token", request)
            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("MESSAGE", "Wiadomość wysłana pomyślnie")
                }
                return@withContext success
            } else {
                Log.e("MESSAGE", "sendMessage nieudane: ${response.code()} ${response.errorBody()?.string()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("MESSAGE", "Wyjątek podczas wysyłania wiadomości", e)
            return@withContext false
        }
    }

    // ============================================================================
    // FUNKCJE MAPOWANIA I DESZYFROWANIA WIADOMOŚCI
    // ============================================================================
    /**
     * Mapuje payload z serwera na obiekty Message z automatycznym deszyfrowaniem
     *
     * @param roomId ID pokoju
     * @param messageList Lista payload'ów z serwera
     * @return Lista obiektów Message z odszyfrowanymi wiadomościami
     */
    fun mapPayloadToMessages(roomId: String, messageList: List<MessagePayload>): List<Message> {

        val context = contextProvider?.invoke()
        if (context == null) {
            Log.e("MESSAGE", "Brak kontekstu - nie można deszyfrowac")
            // zwraca wiadomosc bez deszyfrowania
            return messageList.map { payload ->
                Message(
                    id = payload.timestamp,
                    roomId = roomId,
                    userId = payload.userId ?: "unknown",
                    messageType = payload.messageType,
                    message = payload.data,
                    additionalData = payload.additionalData,
                    timestamp = payload.timestamp
                )
            }
        }
        //pobranie klucza pokoju
        val roomKey = getRoomAESKey(context, roomId)
        //mapowanie i deszyfrowanie
        return messageList.map { payload ->
            val decryptedMessage = if (roomKey != null) {
                // POKÓJ ZASZYFROWANY - deszyfrowanie
                try {
                    val decrypted = CryptoUtils.decryptMessage(payload.data, roomKey)
                    decrypted
                } catch (e: Exception) {
                    Log.e("MESSAGE", "Błąd deszyfrowania: ${e.message}", e)
                    "[Nie można odszyfrować]"
                }
            } else {
                // POKÓJ PUBLICZNY - plaintext
                Log.d("MESSAGE", "Wiadomość niezaszyfrowana: ${payload.data}")
                payload.data
            }

            Message(
                id = payload.timestamp,
                roomId = roomId,
                userId = payload.userId ?: "unknown",
                messageType = payload.messageType,
                message = decryptedMessage, // odszyfrowana lub plaintext
                additionalData = payload.additionalData,
                timestamp = payload.timestamp
            )
        }
    }

    // ============================================================================
    // FUNKCJE POBIERANIA HISTORII I UŻYTKOWNIKÓW
    // ============================================================================

    /**
     * Pobiera historię wiadomości z pokoju
     *
     * @param roomId ID pokoju
     * @return Odpowiedź z historią wiadomości lub null jeśli błąd
     */
    suspend fun requestLastMessages(roomId: String): RequestLastMessagesResponse? = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext null
        try {
            val response = api.requestLastMessages("Bearer $token", RequestLastMessagesRequest(roomId))
            Log.d("MESSAGE", "Raw response: code=${response.code()}, success=${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d("MESSAGE", "Response body: $body")
                body
            } else {
                Log.e("MESSAGE", " Błąd pobierania historii: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MESSAGE", "Wyjątek podczas pobierania historii", e)
            null
        }
    }

    /**
     * Pobiera listę użytkowników w pokoju
     *
     * @param roomId ID pokoju
     * @return Odpowiedź z listą użytkowników lub null jeśli błąd
     */
    suspend fun requestRoomUsers(roomId: String): RoomUsersResponse? = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext null
        try {
            val response = api.getRoomUsers("Bearer $token", roomId)
            Log.d("MESSAGE", "requestRoomUsers: code=${response.code()}, success=${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d("MESSAGE", "Room users response body: $body")
                body
            } else {
                Log.e("MESSAGE", "requestRoomUsers nieudane: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MESSAGE", "Wyjątek podczas pobierania użytkowników", e)
            null
        }
    }

    // ============================================================================
    // FUNKCJE SSE - Odbieranie wiadomości w czasie rzeczywistym
    // ============================================================================

    /**
     * Uruchamia odbieranie wiadomości w czasie rzeczywistym przez SSE
     *
     * @param roomId ID pokoju do nasłuchiwania
     * @param userId ID użytkownika
     * @param onMessage Callback wywoływany przy nowych wiadomościach
     * @param onReconnect Callback wywoływany przy reconnect (opcjonalny)
     */
    fun receiveMessagesStream(
        roomId: String,
        userId: String,
        onMessage: (List<Message>) -> Unit,
        onReconnect: (() -> Unit)? = null
    ) {
        //zatrzymanie poprzedniego streamu jesli byl
        stopReceivingMessages()

        val token = tokenProvider?.invoke() ?: return
        val url = "https://$SERVER_ADDRESS:$SERVER_PORT/api/messages/stream/$roomId?userId=$userId"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        running = true
        activeRoomId = roomId
        //uruchomienie watku SSE
        sseThread = Thread {
            while (running) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.d("SSE", "Connection failed: ${response.code}")
                            Thread.sleep(5000)
                            return@use
                        }
                        //odebranie wiadomosci
                        val source = response.body?.source() ?: return@use
                        Log.i("SSE", "Połączono ze streamem dla pokoju:$roomId")

                        while (running && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (line.startsWith("data: ")) {
                                val json = line.removePrefix("data: ").trim()
                                try {
                                    //parsowanie
                                    val pkg = gson.fromJson(json, SendMessageRequest::class.java)
                                    //automatyczne deszyfrowanie
                                    val msgs = mapPayloadToMessages(roomId, pkg.messageList)
                                    //callback do viewmodelu
                                    onMessage(msgs)
                                } catch (je: Exception) {
                                    Log.e("SSE", "Błąd parsowania JSON: $json", je)
                                }
                            }
                        }
                    }
                } catch (io: IOException) {
                    //reconect po bledzie
                    Log.w("SSE", "Utracono połączenie, próba ponownego połączenia za 5s...", io)
                    onReconnect?.invoke()
                    if (!safeSleep(5000)) break
                } catch (e: Exception) {
                    Log.e("SSE", "Nieoczekiwany błąd w SSE", e)
                    if (!safeSleep(5000)) break
                }
            }
            Log.i("SSE", "Stream dla pokoju $roomId zamknięty")
        }

        sseThread?.start()
    }

    /**
     * Bezpieczne usypianie wątku z obsługą przerwania
     *
     * @param millis Czas uśpienia w milisekundach
     * @return true jeśli sen zakończył się normalnie, false jeśli przerwano
     */
    private fun safeSleep(millis: Long): Boolean {
        return try {
            Thread.sleep(millis)
            true
        } catch (ie: InterruptedException) {
            Log.i("SSE", "Wątek przerwany podczas uśpienia - zatrzymywanie SSE")
            false
        }
    }

    /**
     * Zatrzymuje odbieranie wiadomości SSE
     *
     * Przerywa wątek SSE i czyści stan
     */
    fun stopReceivingMessages() {
        if (running) {
            running = false
            sseThread?.interrupt()
            sseThread = null
            Log.d("SSE", "Stream zatrzymany dla pokoju: $activeRoomId")
            activeRoomId = null
        }
    }

    val isRunning: Boolean
        get() = running
}
