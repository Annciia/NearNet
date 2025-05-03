package com.nearnet

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nearnet.sessionlayer.logic.CryptoUtils
import com.nearnet.sessionlayer.logic.UserRepository
import androidx.lifecycle.lifecycleScope
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.sessionlayer.logic.RoomUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val repo = UserRepository(this)
        val roomUtils = RoomUtils()
        userRepository = UserRepository(applicationContext)

        lifecycleScope.launch {


            //REJESTRACJA
//            val keyPair = CryptoUtils.generateRSAKeys()
//            val publicKeyString = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)
//
//            val result = repo.registerUser(
//                userName = "Marek",
//                password = "xyz123",
//                avatar = "default.png",
//                publicKey = publicKeyString
//            )
              //wyswetla w logach kogo zarejestrowalo
//            Log.d("RegisterUser", "Zarejestrowano: ${result.data}")
//
//            //wyswietla w logach wszystkich uzytkownikow w logu z bazy danych na fonie
//            val users = repo.getAllUsers()
//            for (user in users) {
//                Log.d("AllUsers", "ID: ${user.idUser}, Name: ${user.name}")
//            }



//            //LOGOWANIE
//            val loginResult = repo.loginUser("Marek", "xyz123")
//
//
//            if (loginResult.command == "loginUser") {
//                Toast.makeText(this@MainActivity, "Zalogowano", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(this@MainActivity, "Błąd logowania: ${loginResult.data}", Toast.LENGTH_SHORT).show()
//            }

            //POKOJE Z SERWERA - operacje na osobnym watku przeznaczonym do opracji wejscia/wyjscia
//            val roomList = withContext(Dispatchers.IO) {
//                RoomUtils().getRoomList()
//            }
//
//            // Możesz teraz użyć listy na UI thread
//            roomList.forEach { room ->
//                Log.d("RoomList", "Room ID: ${room.idRoom}, Name: ${room.name}")
//            }
//            val roomNames = roomList.joinToString(", ") { it.name }
//            Toast.makeText(this@MainActivity, "Pokoje: $roomNames", Toast.LENGTH_LONG).show()

            //LISTA POKOI UZYTKOWNIKA
//            val userRooms = withContext(Dispatchers.IO) {
//                RoomUtils().getUserRoomList("d911d3bb-f5af-4b9c-a510-4db5e6b5611d") // podaj właściwe ID użytkownika
//            }
//
//            userRooms.forEach {
//                Log.d("UserRoom", "ID: ${it.idRoom}, Name: ${it.name}")
//            }
//
//            Toast.makeText(this@MainActivity, "Masz ${userRooms.size} pokoi", Toast.LENGTH_SHORT).show()

            //WYLOGOWANIE< USUWANIE, UPDATE same TOAST
//            val logOutResult = userRepository.logOutUser("user123")
//            val deleteResult = userRepository.deleteUser("user123")
//            val updateResult = userRepository.updateUser(
//                UserData(
//                    idUser = "user123",
//                    name = "Test",
//                    avatar = "avatar.png",
//                    publicKey = "publicKey",
//                    passwordHash = "hashedPassword",
//                    darkLightMode = false
//                )
//            )


        }
    }
}