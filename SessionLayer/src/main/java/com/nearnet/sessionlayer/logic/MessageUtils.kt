package com.nearnet.sessionlayer.logic


import android.util.Log
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.data.model.RoomData
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



data class SendMessageRequest(
    val channelId: String,
    val messageList: List<MessagePayload>
)

data class MessagePayload(
    val userId: String,
    val timestamp: String,
    val messageType: String = "text",
    val data: String,
    val additionalData: String = ""
)

data class SendMessageResponse(
    val Succes: Boolean,
    val error: String? = null
)

data class RequestLastMessagesRequest(
    val idRoom: String
)

data class RequestLastMessagesResponse(
    val roomData: RoomData?,
    val Login: String,
    val `package`: PackageData
)

data class PackageData(
    val channelId: String,
    val encryptedPassword: String,
    val messageList: List<MessagePayload>
)

data class AckLastMessagesResponse(val Succes: Boolean)



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

    @POST("/api/messages/ack-last")
    suspend fun ackLastMessages(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any> = emptyMap()
    ): Response<AckLastMessagesResponse>
}

class MessageUtils(private val tokenProvider: () -> String?) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://95.108.77.201:3001")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(MessageApiService::class.java)

    suspend fun sendMessage(roomId: String, message: Message): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext false
        val userId = message.username

        val payload = MessagePayload(
            userId = userId,
            timestamp = message.timestamp.toString(),
            data = message.message
        )

        val request = SendMessageRequest(
            channelId = roomId,
            messageList = listOf(payload)
        )

        try {
            val response = api.sendMessage("Bearer $token", request)
            if (response.isSuccessful) {
                response.body()?.Succes == true
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
        val token = tokenProvider() ?: return@withContext null
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

    // --- potwierdzenie odbioru ---
    suspend fun ackLastMessages(): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext false
        try {
            val response = api.ackLastMessages(token)
            response.isSuccessful && (response.body()?.Succes == true)
        } catch (e: Exception) {
            Log.e("MESSAGE", "Exception in ackLastMessages", e)
            false
        }
    }

    // --- Odbiór w czasie rzeczywistym (SSE) ---
    fun receiveMessagesStream(roomId: String, userId: String, onMessage: (String) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://95.108.77.201:3001/api/messages/stream/$roomId?userId=$userId")
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("MESSAGE", "SSE connection failed: ${response.code}")
                        return@use
                    }
                    val source = response.body?.source() ?: return@use
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (line != null && line.startsWith("data: ")) {
                            val json = line.removePrefix("data: ").trim()
                            onMessage(json)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MESSAGE", "Exception in SSE stream", e)
            }
        }.start()
    }




}
//import com.nearnet.sessionlayer.data.model.Message
//import com.nearnet.sessionlayer.network.SocketClient
//import com.nearnet.sessionlayer.network.SocketClient.socket
//import io.socket.client.Ack
//import io.socket.client.Socket
//import kotlinx.coroutines.CompletableDeferred
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.json.JSONArray
//import org.json.JSONObject
////w trakcie tworzenia
//class MessageUtils (private val messages: MutableList<Message>){
//
//
//    suspend fun sendMessage(roomId: String, message: Message) = withContext(Dispatchers.IO) {
//
//        val timestamp = System.currentTimeMillis()
//        val data = JSONObject().apply {
//            put("roomId", roomId)
//            put("message", message.message)
//            put("timestamp", timestamp)
//        }
//
//        //DODANIE SZYFROWANIA
//
//        socket.emit("send_message", data)
//
//        messages.add(Message(message.username, message.message, timestamp, roomId))
//    }
//
//    fun getMessageHistory(roomId: String, offset: Int, limit: Int) {
//        val data = JSONObject().apply {
//            put("roomId", roomId)
//            //put("offset", offset) //to bedzie potem obslugiwane
//            //put("limit", limit)
//        }
//        socket.emit("get_last_messages_request", data)

