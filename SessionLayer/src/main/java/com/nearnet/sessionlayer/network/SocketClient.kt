package com.nearnet.sessionlayer.network

import android.util.Log
import com.google.gson.Gson
import com.nearnet.sessionlayer.data.PackageCommand
import com.nearnet.sessionlayer.data.model.Message
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.net.Socket as JavaSocket

//to dla socketIO, z tym serwerem teraz nieuzywane
object SocketClient {
    lateinit var socket: Socket
    //private const val SERVER_URL = "http://192.168.0.10:3000"
    private const val SERVER_URL = "http://95.108.77.201:3001/"
    private var isConnected = false
    private var isInitialized = false


    // funkcja do połączenia z serwerem za pomocą Socket.IO
    fun connectSocket(token: String? = null) {

        // jeśli socket już istnieje i jest połączony, rozłącz go najpierw
        if (isInitialized && socket.connected()) return
        //socket.disconnect()


        val options = IO.Options().apply {
            reconnection = true
            forceNew = true
            //transports = arrayOf(WebSocket.NAME)
            transports = arrayOf("websocket")
            if (token != null) {
                //mapOf("token" to token) // Dodajemy token, jeśli istnieje
                query = "token=$token"
            }
        }

        socket = IO.socket(SERVER_URL, options)

        socket.on(Socket.EVENT_CONNECT) {
            Log.d("Socket", "✅ Connected to server")
            isConnected = true

        }

        socket.on(Socket.EVENT_DISCONNECT) {
            Log.d("Socket", "❌ Disconnected from server")
            isConnected = false
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e("Socket", "❌ Connection error: ${args.joinToString()}")
            isConnected = false
        }

        socket.connect()
        isInitialized = true

        Log.d("Socket", "🔄 Attempting to connect to $SERVER_URL")
    }

    fun isConnected(): Boolean = ::socket.isInitialized && socket.connected()

    //LISTENERY CO CZEKAJA AKTYWNIE NA WIADOMOSC Z SERWERA
    fun initSocketListeners(messages: MutableList<Message>, username: String) {
        socket.on("receive_message") { args ->
            val obj = args.getOrNull(0) as? JSONObject
            obj?.let {
                val user = it.getString("username")
                if (user != username) {
                    val msg = it.getString("message")
                    val time = it.getLong("timestamp")
                    val roomId = it.getString("roomId")
                    messages.add(Message(user, msg, time, roomId))
                }
            }
        }

        socket.on("collect_messages") { args ->
            val obj = args.getOrNull(0) as? JSONObject ?: return@on
            val roomId = obj.getString("roomId")
            val requestId = obj.getString("requestId")
            val requester = obj.getString("requester")

            val messagesForRoom = messages
                .filter { it.roomId == roomId }
                .sortedByDescending { it.timestamp }
                .take(20)

            val arr = JSONArray()
            messagesForRoom.forEach { msg ->
                arr.put(JSONObject().apply {
                    put("username", msg.username)
                    put("message", msg.message)
                    put("timestamp", msg.timestamp)
                    put("roomId", msg.roomId)
                })
            }

            val response = JSONObject().apply {
                put("id", requestId)
                put("messages", arr)
                put("roomId", roomId)
                put("requester", requester)
            }

            socket.emit("provide_messages", response)
        }

        socket.on("request_messages") { args ->
            val obj = args.getOrNull(0) as? JSONObject ?: return@on
            val roomId = obj.getString("roomId")
            val messagesArray = obj.getJSONArray("messages")

            val newMessages = mutableListOf<Message>()

            for (i in 0 until messagesArray.length()) {
                val msg = messagesArray.getJSONObject(i)
                newMessages.add(
                    Message(
                        username = msg.getString("username"),
                        message = msg.getString("message"),
                        timestamp = msg.getLong("timestamp"),
                        roomId = msg.getString("roomId")
                    )
                )
            }

            val existingSet = messages.filter { it.roomId == roomId }
                .map { Triple(it.timestamp, it.username, it.message) }
                .toSet()

            newMessages.forEach { msg ->
                val key = Triple(msg.timestamp, msg.username, msg.message)
                if (key !in existingSet) {
                    messages.add(msg)
                }
            }

            // Sortowanie wiadomości po czasie
            messages.sortBy { it.timestamp }
        }

    }

    fun disconnectSocket() {
        if (::socket.isInitialized && socket.connected()) {
            socket.disconnect()
            socket.close()
            Log.d("SocketClient", "Socket disconnected")
        }
    }

}
