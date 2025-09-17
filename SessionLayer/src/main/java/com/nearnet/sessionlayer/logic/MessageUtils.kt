package com.nearnet.sessionlayer.logic

import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.network.SocketClient
import com.nearnet.sessionlayer.network.SocketClient.socket
import io.socket.client.Ack
import io.socket.client.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
//w trakcie tworzenia
class MessageUtils (private val messages: MutableList<Message>){


    suspend fun sendMessage(roomId: String, message: Message) = withContext(Dispatchers.IO) {

        val timestamp = System.currentTimeMillis()
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("message", message.message)
            put("timestamp", timestamp)
        }

        //DODANIE SZYFROWANIA

        socket.emit("send_message", data)

        messages.add(Message(message.username, message.message, timestamp, roomId))
    }

    fun getMessageHistory(roomId: String, offset: Int, limit: Int) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            //put("offset", offset) //to bedzie potem obslugiwane
            //put("limit", limit)
        }
        socket.emit("get_last_messages_request", data)
    }
}