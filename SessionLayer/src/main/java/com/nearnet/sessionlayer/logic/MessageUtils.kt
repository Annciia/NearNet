package com.nearnet.sessionlayer.logic


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

    fun init(provider: () -> String?) {
        tokenProvider = provider
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

    suspend fun sendMessage(roomId: String, message: Message): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider?.invoke() ?: return@withContext false


        val payload = MessagePayload(
            userId = message.userId,
            timestamp = message.timestamp,
            messageType = "text",
            data = message.message,
            additionalData = message.additionalData
        )

        val request = SendMessageRequest(
            channelId = roomId,
            messageList = listOf(payload)
        )

        try {
            val response = api.sendMessage("Bearer $token", request)
            if (response.isSuccessful) {
                response.body()?.success == true
            } else {
                Log.e("MESSAGE", "sendMessage failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("MESSAGE", "Exception in sendMessage", e)
            false
        }
    }

    //Pobranie ostatnich wiadomości
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
    //mapowanie, zeby nie powtarzac w ViewModelu
    fun mapPayloadToMessages(roomId: String, messageList: List<MessagePayload>): List<Message> {
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
    //zmiana id na name, zeby w czatach byly name a nie id
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
    //odbieranie wiadomosci na zywo przy wlaczonym czacie + reconect
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
            true // sleep zakończony normalnie
        } catch (ie: InterruptedException) {
            Log.i("SSE", "Thread interrupted during sleep, stopping SSE")
            false // sleep przerwany
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

