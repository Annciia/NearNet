package com.nearnet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.socket.client.IO
import io.socket.client.Socket
import com.nearnet.sessionlayer.network.SocketClient
import com.nearnet.sessionlayer.logic.UserRepository
import kotlinx.coroutines.launch
import com.nearnet.sessionlayer.logic.RoomUtils
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.logic.RoomUtils.createRoom
import io.socket.client.Ack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.logic.MessageUtils



class MainActivity : ComponentActivity() {

    private lateinit var socket: Socket
    private val serverUrl = "http://192.168.0.16:3000"
    private var jwtToken: String = ""
    private val messages = mutableListOf<Message>()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val messageUtils = MessageUtils(messages)
        //laczenie zeby sie zalogowac
        SocketClient.connectSocket()
        if (jwtToken.isNotEmpty()) {
            socket.emit("join_all_rooms")
            //RoomUtils.getRoomList(SocketClient.socket) - ogarnac musze jak to wywolac
        }

        //SocketClient.initSocketListeners()

        lifecycleScope.launch {
            val repository = UserRepository(
                context = this@MainActivity,
                socket = SocketClient.socket
            )
            //logowanie
            repository.loginUser("Marek", "123")

            val token = UserRepository.getTokenFromPreferences(this@MainActivity)
            //dopiero teraz polaczenie bo wczesniej nie chcialo sie autoryzowac
            SocketClient.connectSocket(token)
            SocketClient.initSocketListeners(messages, "Marek") //TUTAJ TRZEBA Z SHERED PREFERENCES POTEM BO Z PALCA WPISANE
            SocketClient.socket.emit("join_all_rooms")





//REJESTRACJA
//            repository.registerUser(
//                userName = "exampleUser",
//                password = "password123",
//                avatar = "https://example.com/avatar.png",
//            )
//LOGOWANIE - tutaj bede z shered pobieral jak juz sie zrobi logowanie i rejestracje
            //repository.loginUser("Marek", "123")
            //repository.loginUser("Kuba", "456")
            //repository.loginUser("Ania", "789")

//LOGOUT
            //repository.logOutUser("Marek")
            //repository.deleteUser("Marek")
            //repository.deleteUser("Chuj")
////Lista wszystkich pokoi pokoi - tutaj trza chyba na serwerze zmienic, zeby odsyalalo wszystkie, bo teraz odsyle te w ktorych jest uzytkownik
//            val rooms = RoomUtils.getRoomList(SocketClient.socket)
//            Log.d("Rooms", "Rooms from server: $rooms")
////POKOJE UZYTKOWNIKA - jeszcze nie ma na serwie
//            val userRooms = RoomUtils.getUserRoomList(
//                userName = "Marek",
//                userRepository = repository,
//                socket = SocketClient.socket
//            )

//TWORZENIE POKOJU - trzeba dodac na serwerze obsluge wszystkiego
//            val roomData = RoomData(
//                name = "TestRoom2",
//                password = "5678",
//            )
//            val roomId = createRoom(this@MainActivity, SocketClient.socket, roomData)
//            Log.d("CreateRoom", "Room created with ID: $roomId")

            val message = Message("Marek", "Hello!", System.currentTimeMillis(), "20c2a7e70f1cc281")
            messageUtils.sendMessage("roomId", message)


        }

    }

    override fun onDestroy() {
        super.onDestroy()
        SocketClient.disconnectSocket()
    }
}
