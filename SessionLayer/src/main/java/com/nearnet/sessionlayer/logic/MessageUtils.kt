package com.nearnet.sessionlayer.logic


//import android.util.Log
//import com.google.gson.Gson
//import com.nearnet.sessionlayer.data.model.Message
//import com.nearnet.sessionlayer.data.model.RoomData
//import com.nearnet.sessionlayer.data.model.UserData
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import retrofit2.Response
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Body
//import retrofit2.http.Header
//import retrofit2.http.POST
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import retrofit2.http.GET
//import retrofit2.http.Path
//import java.io.IOException
//
//
//data class SendMessageRequest(
//    val channelId: String,
//    val messageList: List<MessagePayload>
//)
//
//data class MessagePayload(
//    val userId: String? = null,
//    val timestamp: String,
//    val messageType: String = "text",
//    val data: String,
//    val additionalData: String = ""
//)
//
//data class SendMessageResponse(
//    val success: Boolean,
//    val error: String? = null
//)
//
//data class RequestLastMessagesRequest(
//    val idRoom: String
//)
//
//data class RequestLastMessagesResponse(
//    val roomData: RoomData?,
//    val login: String,
//    val `package`: PackageData
//)
//
//data class PackageData(
//    val channelId: String,
//    val encryptedPassword: String,
//    val messageList: List<MessagePayload>
//)
//
//data class RoomUsersResponse(
//    val roomData: RoomData,
//    val userList: UserListWrapper
//)
//
//data class UserListWrapper(
//    val rooms: List<UserData>
//)
//
//data class AckLastMessagesResponse(val success: Boolean)
//
//
//
//// --- Retrofit API ---
//interface MessageApiService {
//    @POST("/api/messages/send")
//    suspend fun sendMessage(
//        @Header("Authorization") token: String,
//        @Body body: SendMessageRequest
//    ): Response<SendMessageResponse>
//
//    @POST("/api/messages/request-last")
//    suspend fun requestLastMessages(
//        @Header("Authorization") token: String,
//        @Body body: RequestLastMessagesRequest
//    ): Response<RequestLastMessagesResponse>
//
//    @GET("/api/rooms/{id}/users")
//    suspend fun getRoomUsers(
//        @Header("Authorization") token: String,
//        @Path("id") roomId: String
//    ): Response<RoomUsersResponse>
//
//
//}
//
//object MessageUtils {
//
//    private var tokenProvider: (() -> String?)? = null
//
//    fun init(provider: () -> String?) {
//        tokenProvider = provider
//    }
//
//    private val retrofit = Retrofit.Builder()
//        .baseUrl("http://95.108.77.201:3002")
//        .addConverterFactory(GsonConverterFactory.create())
//        .build()
//
//    private val api = retrofit.create(MessageApiService::class.java)
//    private val client = OkHttpClient()
//    private val gson = Gson()
//
//    // ---- SSE STREAM ----
//    private var sseThread: Thread? = null
//    @Volatile private var running = false
//    private var activeRoomId: String? = null
//
//    suspend fun sendMessage(roomId: String, message: Message): Boolean = withContext(Dispatchers.IO) {
//        val token = tokenProvider?.invoke() ?: return@withContext false
//
//
//        val payload = MessagePayload(
//            userId = message.userId,
//            timestamp = message.timestamp,
//            messageType = "text",
//            data = message.message,
//            additionalData = message.additionalData
//        )
//
//        val request = SendMessageRequest(
//            channelId = roomId,
//            messageList = listOf(payload)
//        )
//
//        try {
//            val response = api.sendMessage("Bearer $token", request)
//            if (response.isSuccessful) {
//                response.body()?.success == true
//            } else {
//                Log.e("MESSAGE", "sendMessage failed: ${response.code()} ${response.errorBody()?.string()}")
//                false
//            }
//        } catch (e: Exception) {
//            Log.e("MESSAGE", "Exception in sendMessage", e)
//            false
//        }
//    }
//
//    //Pobranie ostatnich wiadomości
//    suspend fun requestLastMessages(roomId: String): RequestLastMessagesResponse? = withContext(Dispatchers.IO) {
//        val token = tokenProvider?.invoke() ?: return@withContext null
//        try {
//            val response = api.requestLastMessages("Bearer $token", RequestLastMessagesRequest(roomId))
//            Log.d("MessageUtils", "Raw response: code=${response.code()}, success=${response.isSuccessful}")
//
//            if (response.isSuccessful) {
//                val body = response.body()
//                Log.d("MessageUtils", "Response body: $body")
//                body
//            } else {
//                Log.e("MessageUtils", "Error body: ${response.errorBody()?.string()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e("MessageUtils", "Exception in requestLastMessages", e)
//            null
//        }
//    }
//    //mapowanie, zeby nie powtarzac w ViewModelu
//    fun mapPayloadToMessages(roomId: String, messageList: List<MessagePayload>): List<Message> {
//        return messageList.map { payload ->
//            Message(
//                id = payload.timestamp,
//                roomId = roomId,
//                userId = payload.userId ?: "unknown",
//                messageType = payload.messageType,
//                message = payload.data,
//                additionalData = payload.additionalData,
//                timestamp = payload.timestamp
//            )
//        }
//    }
//    //zmiana id na name, zeby w czatach byly name a nie id
//    suspend fun requestRoomUsers(roomId: String): RoomUsersResponse? = withContext(Dispatchers.IO) {
//        val token = tokenProvider?.invoke() ?: return@withContext null
//        try {
//            val response = api.getRoomUsers("Bearer $token", roomId)
//            Log.d("MessageUtils", "requestRoomUsers: code=${response.code()}, success=${response.isSuccessful}")
//
//            if (response.isSuccessful) {
//                val body = response.body()
//                Log.d("MessageUtils", "Room users response body: $body")
//                body
//            } else {
//                Log.e("MessageUtils", "requestRoomUsers failed: ${response.code()} ${response.errorBody()?.string()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e("MessageUtils", "Exception in requestRoomUsers", e)
//            null
//        }
//    }
//    //odbieranie wiadomosci na zywo przy wlaczonym czacie + reconect
//    fun receiveMessagesStream(
//        roomId: String,
//        userId: String,
//        onMessage: (List<Message>) -> Unit,
//        onReconnect: (() -> Unit)? = null
//    ) {
//        stopReceivingMessages()
//
//        val token = tokenProvider?.invoke() ?: return
//        val url = "http://95.108.77.201:3002/api/messages/stream/$roomId?userId=$userId"
//
//        val request = Request.Builder()
//            .url(url)
//            .addHeader("Authorization", "Bearer $token")
//            .build()
//
//        running = true
//        activeRoomId = roomId
//
//        sseThread = Thread {
//            while (running) {
//                try {
//                    client.newCall(request).execute().use { response ->
//                        if (!response.isSuccessful) {
//                            Log.e("SSE", "Connection failed: ${response.code}")
//                            Thread.sleep(5000)
//                            return@use
//                        }
//
//                        val source = response.body?.source() ?: return@use
//                        Log.i("SSE", "Connected to stream for room=$roomId")
//
//                        while (running && !source.exhausted()) {
//                            val line = source.readUtf8Line() ?: continue
//                            if (line.startsWith("data: ")) {
//                                val json = line.removePrefix("data: ").trim()
//                                try {
//                                    val pkg = gson.fromJson(json, SendMessageRequest::class.java)
//                                    val msgs = mapPayloadToMessages(roomId, pkg.messageList)
//                                    onMessage(msgs)
//                                } catch (je: Exception) {
//                                    Log.e("SSE", "JSON parse error: $json", je)
//                                }
//                            }
//                        }
//                    }
//                } catch (io: IOException) {
//                    Log.w("SSE", "Connection lost, will retry...", io)
//                    onReconnect?.invoke()
//                    if (!safeSleep(5000)) break
//                } catch (e: Exception) {
//                    Log.e("SSE", "Unexpected error", e)
//                    if (!safeSleep(5000)) break
//                }
//            }
//            Log.i("SSE", "Stream for $roomId closed")
//        }
//
//        sseThread?.start()
//    }
//
//    private fun safeSleep(millis: Long): Boolean {
//        return try {
//            Thread.sleep(millis)
//            true // sleep zakończony normalnie
//        } catch (ie: InterruptedException) {
//            Log.i("SSE", "Thread interrupted during sleep, stopping SSE")
//            false // sleep przerwany
//        }
//    }
//
//
//    fun stopReceivingMessages() {
//        if (running) {
//            running = false
//            sseThread?.interrupt()
//            sseThread = null
//            Log.d("SSE", "Stream stopped for room=$activeRoomId")
//            activeRoomId = null
//        }
//    }
//
//
//    val isRunning: Boolean
//        get() = running
//
//}

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


data class SendMessageRequest(
    val channelId: String,
    val messageList: List<MessagePayload>
)

data class MessagePayload(
    val userId: String? = null,
    val timestamp: String,
    val messageType: String = "text",
    val data: String,
    val additionalData: String = ""
)

data class SendMessageResponse(
    val success: Boolean,
    val error: String? = null
)

data class RequestLastMessagesRequest(
    val idRoom: String
)

data class RequestLastMessagesResponse(
    val roomData: RoomData?,
    val login: String,
    val `package`: PackageData
)

data class PackageData(
    val channelId: String,
    val encryptedPassword: String,
    val messageList: List<MessagePayload>
)

data class RoomUsersResponse(
    val roomData: RoomData,
    val userList: UserListWrapper
)

data class UserListWrapper(
    val rooms: List<UserData>
)

data class AckLastMessagesResponse(val success: Boolean)



// --- Retrofit API ---
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

object MessageUtils {

    private var tokenProvider: (() -> String?)? = null
    private var contextProvider: (() -> Context?)? = null // NOWE: potrzebujemy kontekstu

    // ZMIENIONA: Teraz przyjmuje również Context
    fun init(tokenProv: () -> String?, contextProv: () -> Context?) {
        tokenProvider = tokenProv
        contextProvider = contextProv
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://95.108.77.201:3002")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(MessageApiService::class.java)
    private val client = OkHttpClient()
    private val gson = Gson()

    // ---- SSE STREAM ----
    private var sseThread: Thread? = null
    @Volatile private var running = false
    private var activeRoomId: String? = null

    // ============================================
    // FUNKCJE POMOCNICZE - Zarządzanie kluczami AES
    // ============================================

    /**
     * Pobiera klucz AES dla pokoju
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
     * Sprawdza czy pokój ma klucz AES (czyli czy jest zaszyfrowany)
     */
    private fun hasRoomKey(context: Context, roomId: String): Boolean {
        val prefs = context.getSharedPreferences("RoomKeys", Context.MODE_PRIVATE)
        return prefs.contains("aes_key_$roomId")
    }

    // ============================================
    // ZAKTUALIZOWANE: sendMessage - z szyfrowaniem
    // ============================================
    suspend fun sendMessage(roomId: String, message: Message): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext false
        val context = contextProvider?.invoke() ?: return@withContext false

        Log.d("MESSAGE", "====== WYSYŁANIE WIADOMOŚCI ======")
        Log.d("MESSAGE", "RoomId: $roomId")
        Log.d("MESSAGE", "Oryginalna wiadomość: ${message.message}")

        // ============================================
        // KROK 1: Sprawdź czy pokój ma klucz szyfrowania
        // ============================================
        val roomKey = getRoomAESKey(context, roomId)

        val messageToSend = if (roomKey != null) {
            // POKÓJ ZASZYFROWANY
            Log.d("MESSAGE", "🔒 Pokój zaszyfrowany - szyfruję wiadomość...")

            try {
                val encrypted = CryptoUtils.encryptMessage(message.message, roomKey)
                Log.d("MESSAGE", "✓ Wiadomość zaszyfrowana")
                Log.d("MESSAGE", "  Zaszyfrowana długość: ${encrypted.length} znaków")
                Log.d("MESSAGE", "  Pierwsze 50 znaków: ${encrypted.take(50)}...")
                encrypted
            } catch (e: Exception) {
                Log.e("MESSAGE", "✗ Błąd szyfrowania wiadomości: ${e.message}", e)
                return@withContext false
            }
        } else {
            // POKÓJ NIEZASZYFROWANY (publiczny)
            Log.d("MESSAGE", "🌐 Pokój niezaszyfrowany - wysyłam plaintext")
            message.message
        }

        // ============================================
        // KROK 2: Wyślij wiadomość na serwer
        // ============================================
        val payload = MessagePayload(
            userId = message.userId,
            timestamp = message.timestamp,
            messageType = "text",
            data = messageToSend, // Zaszyfrowana lub plaintext
            additionalData = message.additionalData
        )

        val request = SendMessageRequest(
            channelId = roomId,
            messageList = listOf(payload)
        )

        try {
            val response = api.sendMessage("Bearer $token", request)
            if (response.isSuccessful) {
                val success = response.body()?.success == true
                if (success) {
                    Log.d("MESSAGE", "✓ Wiadomość wysłana pomyślnie")
                }
                return@withContext success
            } else {
                Log.e("MESSAGE", "✗ sendMessage failed: ${response.code()} ${response.errorBody()?.string()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("MESSAGE", "✗ Exception in sendMessage", e)
            return@withContext false
        }
    }

    // ============================================
    // ZAKTUALIZOWANE: mapPayloadToMessages - z deszyfrowaniem
    // ============================================
    fun mapPayloadToMessages(roomId: String, messageList: List<MessagePayload>): List<Message> {
        Log.d("MESSAGE", "====== MAPOWANIE WIADOMOŚCI ======")
        Log.d("MESSAGE", "RoomId: $roomId")
        Log.d("MESSAGE", "Liczba wiadomości: ${messageList.size}")

        val context = contextProvider?.invoke()
        if (context == null) {
            Log.e("MESSAGE", "✗ Brak kontekstu - nie można deszyfrować")
            // Zwróć wiadomości bez deszyfrowania
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

        // Sprawdź czy pokój ma klucz
        val roomKey = getRoomAESKey(context, roomId)

        return messageList.map { payload ->
            val decryptedMessage = if (roomKey != null) {
                // POKÓJ ZASZYFROWANY - deszyfruj
                Log.d("MESSAGE", "🔒 Deszyfrowanie wiadomości...")
                Log.d("MESSAGE", "  Zaszyfrowana: ${payload.data.take(50)}...")

                try {
                    val decrypted = CryptoUtils.decryptMessage(payload.data, roomKey)
                    Log.d("MESSAGE", "✓ Wiadomość odszyfrowana: $decrypted")
                    decrypted
                } catch (e: Exception) {
                    Log.e("MESSAGE", "✗ Błąd deszyfrowania: ${e.message}", e)
                    "[Nie można odszyfrować]"
                }
            } else {
                // POKÓJ NIEZASZYFROWANY
                Log.d("MESSAGE", "🌐 Wiadomość niezaszyfrowana: ${payload.data}")
                payload.data
            }

            Message(
                id = payload.timestamp,
                roomId = roomId,
                userId = payload.userId ?: "unknown",
                messageType = payload.messageType,
                message = decryptedMessage, // Odszyfrowana lub plaintext
                additionalData = payload.additionalData,
                timestamp = payload.timestamp
            )
        }
    }

    // Pozostałe funkcje bez zmian...

    suspend fun requestLastMessages(roomId: String): RequestLastMessagesResponse? = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext null
        try {
            val response = api.requestLastMessages("Bearer $token", RequestLastMessagesRequest(roomId))
            Log.d("MessageUtils", "Raw response: code=${response.code()}, success=${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d("MessageUtils", "Response body: $body")
                body
            } else {
                Log.e("MessageUtils", "Error body: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MessageUtils", "Exception in requestLastMessages", e)
            null
        }
    }

    suspend fun requestRoomUsers(roomId: String): RoomUsersResponse? = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext null
        try {
            val response = api.getRoomUsers("Bearer $token", roomId)
            Log.d("MessageUtils", "requestRoomUsers: code=${response.code()}, success=${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d("MessageUtils", "Room users response body: $body")
                body
            } else {
                Log.e("MessageUtils", "requestRoomUsers failed: ${response.code()} ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MessageUtils", "Exception in requestRoomUsers", e)
            null
        }
    }

    fun receiveMessagesStream(
        roomId: String,
        userId: String,
        onMessage: (List<Message>) -> Unit,
        onReconnect: (() -> Unit)? = null
    ) {
        stopReceivingMessages()

        val token = tokenProvider?.invoke() ?: return
        val url = "http://95.108.77.201:3002/api/messages/stream/$roomId?userId=$userId"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        running = true
        activeRoomId = roomId

        sseThread = Thread {
            while (running) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e("SSE", "Connection failed: ${response.code}")
                            Thread.sleep(5000)
                            return@use
                        }

                        val source = response.body?.source() ?: return@use
                        Log.i("SSE", "Connected to stream for room=$roomId")

                        while (running && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (line.startsWith("data: ")) {
                                val json = line.removePrefix("data: ").trim()
                                try {
                                    val pkg = gson.fromJson(json, SendMessageRequest::class.java)
                                    // AUTOMATYCZNE DESZYFROWANIE przez mapPayloadToMessages
                                    val msgs = mapPayloadToMessages(roomId, pkg.messageList)
                                    onMessage(msgs)
                                } catch (je: Exception) {
                                    Log.e("SSE", "JSON parse error: $json", je)
                                }
                            }
                        }
                    }
                } catch (io: IOException) {
                    Log.w("SSE", "Connection lost, will retry...", io)
                    onReconnect?.invoke()
                    if (!safeSleep(5000)) break
                } catch (e: Exception) {
                    Log.e("SSE", "Unexpected error", e)
                    if (!safeSleep(5000)) break
                }
            }
            Log.i("SSE", "Stream for $roomId closed")
        }

        sseThread?.start()
    }

    private fun safeSleep(millis: Long): Boolean {
        return try {
            Thread.sleep(millis)
            true
        } catch (ie: InterruptedException) {
            Log.i("SSE", "Thread interrupted during sleep, stopping SSE")
            false
        }
    }

    fun stopReceivingMessages() {
        if (running) {
            running = false
            sseThread?.interrupt()
            sseThread = null
            Log.d("SSE", "Stream stopped for room=$activeRoomId")
            activeRoomId = null
        }
    }

    val isRunning: Boolean
        get() = running
}

