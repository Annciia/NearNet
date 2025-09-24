package com.nearnet

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.logic.MessageUtils
import com.nearnet.sessionlayer.logic.RoomRepository
import com.nearnet.sessionlayer.logic.UserRepository
import kotlinx.coroutines.launch

class MainActivity3 : AppCompatActivity() {

    private lateinit var recyclerMessages: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSendMessage: Button
    private lateinit var editRoomName: EditText
    private lateinit var btnLoadMessages: Button

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter
    private lateinit var messageUtils: MessageUtils
    private lateinit var roomRepository: RoomRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)
        enableEdgeToEdge()

        recyclerMessages = findViewById(R.id.recyclerMessages)
        editMessage = findViewById(R.id.editMessage)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        editRoomName = findViewById(R.id.editRoomName)
        btnLoadMessages = findViewById(R.id.btnLoadMessages)

        adapter = MessageAdapter(messages)
        recyclerMessages.layoutManager = LinearLayoutManager(this)
        recyclerMessages.adapter = adapter

        roomRepository = RoomRepository(this)

        messageUtils = MessageUtils {
            UserRepository.getTokenFromPreferences(this)?.let { "Bearer $it" }
        }

        // Ładowanie ostatnich wiadomości
        btnLoadMessages.setOnClickListener {
            val roomName = editRoomName.text.toString().trim()
            if (roomName.isEmpty()) {
                Toast.makeText(this, "Podaj nazwę pokoju", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val idRoom = roomRepository.getRoomIdByName(roomName)
                if (idRoom == null) {
                    Toast.makeText(this@MainActivity3, "Nie znaleziono pokoju", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val response = messageUtils.requestLastMessages(idRoom)
                if (response != null) {
                    messages.clear()
                    messages.addAll(response.`package`.messageList.map { payload ->
                        Message(
                            username = payload.userId,
                            message = payload.data,
                            timestamp = payload.timestamp.toLongOrNull() ?: System.currentTimeMillis(),
                            roomId = idRoom
                        )
                    })
                    adapter.notifyDataSetChanged()
                    recyclerMessages.scrollToPosition(messages.size - 1)

                    // Wysyłamy ACK po odebraniu
                    val ack = messageUtils.ackLastMessages()
                    if (!ack) {
                        Log.w("MESSAGE", "⚠️ Nie udało się wysłać ACK")
                    }

                    // Startujemy stream wiadomości na żywo
                    val userId = response.Login // 👈 zakładam, że Login to ID/username użytkownika
                    startMessageStream(idRoom, userId)

                } else {
                    Toast.makeText(this@MainActivity3, "Nie udało się pobrać wiadomości", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Wysyłanie wiadomości
        btnSendMessage.setOnClickListener {
            val roomName = editRoomName.text.toString().trim()
            val text = editMessage.text.toString().trim()

            if (roomName.isEmpty()) {
                Toast.makeText(this, "Podaj nazwę pokoju", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (text.isEmpty()) {
                Toast.makeText(this, "Wpisz wiadomość", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val idRoom = roomRepository.getRoomIdByName(roomName)
                if (idRoom == null) {
                    Toast.makeText(this@MainActivity3, "Nie znaleziono pokoju lub nie jesteś w tym pokoju", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val message = Message(
                    username = "Ja",
                    message = text,
                    timestamp = System.currentTimeMillis(),
                    roomId = idRoom
                )

                val success = messageUtils.sendMessage(idRoom, message)
                if (success) {
                    messages.add(message)
                    adapter.notifyItemInserted(messages.size - 1)
                    recyclerMessages.scrollToPosition(messages.size - 1)
                    editMessage.text.clear()
                } else {
                    Toast.makeText(this@MainActivity3, "Wysyłanie nieudane", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // SSE – odbiór wiadomości na żywo
    private fun startMessageStream(roomId: String, userId: String) {
        messageUtils.receiveMessagesStream(roomId, userId) { json ->
            runOnUiThread {
                try {
                    val newMessage = Message(
                        username = "Inny użytkownik",
                        message = json,
                        timestamp = System.currentTimeMillis(),
                        roomId = roomId
                    )
                    messages.add(newMessage)
                    adapter.notifyItemInserted(messages.size - 1)
                    recyclerMessages.scrollToPosition(messages.size - 1)
                } catch (e: Exception) {
                    Log.e("MESSAGE", "❌ Błąd parsowania SSE: ${e.message}")
                }
            }
        }
    }

    private fun enableEdgeToEdge() {
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }
}




//package com.nearnet
//
//import android.os.Bundle
//import android.widget.Button
//import android.widget.EditText
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.nearnet.sessionlayer.data.model.Message
//import com.nearnet.sessionlayer.logic.MessageUtils
//import com.nearnet.sessionlayer.logic.RoomRepository
//import com.nearnet.sessionlayer.logic.UserRepository
//import kotlinx.coroutines.launch
//
//class MainActivity3 : AppCompatActivity() {
//
//    private lateinit var recyclerMessages: RecyclerView
//    private lateinit var editMessage: EditText
//    private lateinit var btnSendMessage: Button
//    private lateinit var editRoomName: EditText
//
//    private val messages = mutableListOf<Message>()
//    private lateinit var adapter: MessageAdapter
//    private lateinit var messageUtils: MessageUtils
//    private lateinit var roomRepository: RoomRepository
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main3)
//        enableEdgeToEdge()
//
//        recyclerMessages = findViewById(R.id.recyclerMessages)
//        editMessage = findViewById(R.id.editMessage)
//        btnSendMessage = findViewById(R.id.btnSendMessage)
//        editRoomName = findViewById(R.id.editRoomName)
//
//        adapter = MessageAdapter(messages)
//        recyclerMessages.layoutManager = LinearLayoutManager(this)
//        recyclerMessages.adapter = adapter
//
//        roomRepository = RoomRepository(this)
//
//        messageUtils = MessageUtils {
//            // Pobranie tokena z UserRepository
//            UserRepository.getTokenFromPreferences(this)?.let { "Bearer $it" }
//        }
//
//        val btnLoadMessages = findViewById<Button>(R.id.btnLoadMessages)
//
//        btnLoadMessages.setOnClickListener {
//            val roomName = editRoomName.text.toString().trim()
//            if (roomName.isEmpty()) {
//                Toast.makeText(this, "Podaj nazwę pokoju", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            lifecycleScope.launch {
//                val idRoom = roomRepository.getRoomIdByName(roomName)
//                if (idRoom == null) {
//                    Toast.makeText(this@MainActivity3, "Nie znaleziono pokoju", Toast.LENGTH_SHORT).show()
//                    return@launch
//                }
//
//                val response = messageUtils.requestLastMessages(idRoom)
//                if (response != null) {
//                    messages.clear()
//                    messages.addAll(response.`package`.messageList.map { payload ->
//                        Message(
//                            username = response.Login,
//                            message = payload.data,
//                            timestamp = payload.timestamp.toLongOrNull() ?: System.currentTimeMillis(),
//                            roomId = idRoom
//                        )
//                    })
//                    adapter.notifyDataSetChanged()
//                    recyclerMessages.scrollToPosition(messages.size - 1)
//                } else {
//                    Toast.makeText(this@MainActivity3, "Nie udało się pobrać wiadomości", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//
//
//        btnSendMessage.setOnClickListener {
//            val roomName = editRoomName.text.toString().trim()
//            val text = editMessage.text.toString().trim()
//
//            if (roomName.isEmpty()) {
//                Toast.makeText(this, "Podaj nazwę pokoju", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            if (text.isEmpty()) {
//                Toast.makeText(this, "Wpisz wiadomość", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            lifecycleScope.launch {
//                // Pobieramy tylko swoje pokoje
//                val idRoom = roomRepository.getRoomIdByName(roomName)
//                if (idRoom == null) {
//                    Toast.makeText(this@MainActivity3, "Nie znaleziono pokoju lub nie jesteś w tym pokoju", Toast.LENGTH_SHORT).show()
//                    return@launch
//                }
//
//                val message = Message(
//                    username = "Ja",
//                    message = text,
//                    timestamp = System.currentTimeMillis(),
//                    roomId = idRoom
//                )
//
//                val success = messageUtils.sendMessage(idRoom, message)
//                if (success) {
//                    messages.add(message)
//                    adapter.notifyItemInserted(messages.size - 1)
//                    recyclerMessages.scrollToPosition(messages.size - 1)
//                    editMessage.text.clear()
//                } else {
//                    Toast.makeText(this@MainActivity3, "Wysyłanie nieudane", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//
//        // Edge-to-edge padding
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//    }
//
//    private fun enableEdgeToEdge() {
//        window.decorView.systemUiVisibility =
//            window.decorView.systemUiVisibility or
//                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//    }
//}



