package com.nearnet


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.sessionlayer.logic.UserRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = UserRepository(this)

        val loginField = findViewById<EditText>(R.id.editLogin)
        val passwordField = findViewById<EditText>(R.id.editPassword)
        val avatarField = findViewById<EditText>(R.id.editAvatar)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        val newNameField = findViewById<EditText>(R.id.editNewName)
        val newAvatarField = findViewById<EditText>(R.id.editNewAvatar)
        val btnUpdate = findViewById<Button>(R.id.btnUpdate)

        val deletePasswordField = findViewById<EditText>(R.id.editDeletePassword)
        val btnDelete = findViewById<Button>(R.id.btnDelete)

        val btnGoToRooms = findViewById<Button>(R.id.btnGoToRooms)
        btnGoToRooms.setOnClickListener {
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
        }

        btnRegister.setOnClickListener {
            val login = loginField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val avatar = avatarField.text.toString().trim().ifEmpty { "https://example.com/avatar.png" }

            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wpisz login i hasło", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = repository.registerUser(login, password, avatar)
                    if (success) {
                        Toast.makeText(this@MainActivity, "✅ Rejestracja OK", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Rejestracja nieudana", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Register error: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "❌ Błąd rejestracji", Toast.LENGTH_SHORT).show()
                }
            }
        }

// 🔹 Logowanie
        btnLogin.setOnClickListener {
            val login = loginField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wpisz login i hasło", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = repository.loginUser(login, password)
                    if (success) {
                        val token = UserRepository.getTokenFromPreferences(this@MainActivity)
                        Toast.makeText(this@MainActivity, "✅ Zalogowano", Toast.LENGTH_SHORT).show()
                        Log.d("MainActivity", "Token: $token")
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Logowanie nieudane", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Login error: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "❌ Błąd logowania", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 🔹 Update
        btnUpdate.setOnClickListener {
            val newName = newNameField.text.toString().trim()
            val newAvatar = newAvatarField.text.toString().trim()

            if (newName.isEmpty() && newAvatar.isEmpty()) {
                Toast.makeText(this, "Podaj nowe dane do aktualizacji", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val updated = UserData(
                        idUser = "ignored", // backend bierze ID z tokena
                        name = newName,
                        avatar = newAvatar,
                        publicKey = "",
                        password = "",
                        //passwordHash = "",
                        darkLightMode = false
                    )
                    repository.updateUser(updated)
                    Toast.makeText(this@MainActivity, "✅ Profil zaktualizowany", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Update error: ${e.message}", e)
                }
            }
        }

        // 🔹 Delete
        btnDelete.setOnClickListener {
            val password = deletePasswordField.text.toString().trim()
            if (password.isEmpty()) {
                Toast.makeText(this, "Podaj hasło do usunięcia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    repository.deleteUser(password)
                    Toast.makeText(this@MainActivity, "✅ Konto usunięte", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Delete error: ${e.message}", e)
                }
            }
        }
    }
}

//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import android.widget.Toast
//import androidx.activity.ComponentActivity
//import androidx.lifecycle.lifecycleScope
//import io.socket.client.IO
//import io.socket.client.Socket
//import com.nearnet.sessionlayer.network.SocketClient
//import com.nearnet.sessionlayer.logic.UserRepository
//import kotlinx.coroutines.launch
//import com.nearnet.sessionlayer.logic.RoomUtils
//import com.nearnet.sessionlayer.data.model.RoomData
//import io.socket.client.Ack
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.json.JSONObject
//import com.nearnet.sessionlayer.data.model.Message
//import com.nearnet.sessionlayer.data.model.UserData
//import com.nearnet.sessionlayer.logic.MessageUtils
//import kotlinx.coroutines.suspendCancellableCoroutine


//class MainActivity : ComponentActivity() {
//
//    private val messages = mutableListOf<Message>()
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//
//        lifecycleScope.launch {
//            try {
//                // Pobranie tokena z SharedPreferences
//                val token = UserRepository.getTokenFromPreferences(this@MainActivity)
//
//                // Połączenie socketu
//                SocketClient.connectSocket(token)
//
//                // Czekanie na pełne połączenie
//                suspendCancellableCoroutine<Unit> { cont ->
//                    if (SocketClient.isConnected()) {
//                        cont.resume(Unit) {}
//                    } else {
//                        SocketClient.socket.once(Socket.EVENT_CONNECT) {
//                            cont.resume(Unit) {}
//                        }
//                    }
//                }
//
//                // Inicjalizacja repozytorium
//                val repository = UserRepository(this@MainActivity, SocketClient.socket)
//
//                // Logowanie użytkownika
//                repository.loginUser("Marek", "123")
//
//                // Rejestracja nowego użytkownika
//                repository.registerUser(
//                    userName = "Alealala",
//                    password = "234234",
//                    avatar = "https://example.com/avatar.png"
//                )
//
//                // Pobranie tokena po zalogowaniu
//                val newToken = UserRepository.getTokenFromPreferences(this@MainActivity)
//                Log.d("MainActivity", "Token po logowaniu: $newToken")
//
//            } catch (e: Exception) {
//                Log.e("MainActivity", "❌ Error in onCreate: ${e.message}", e)
//            }
//
//        }
//    }
//
//
//
//    override fun onDestroy() {
//        super.onDestroy()
//        SocketClient.disconnectSocket()
//    }
//}
