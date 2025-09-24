package com.nearnet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.logic.RoomRepository
import kotlinx.coroutines.launch

class MainActivity2 : AppCompatActivity() {

    private lateinit var repository: RoomRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        repository = RoomRepository(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val roomNameField = findViewById<EditText>(R.id.editRoomName)
        val roomPasswordField = findViewById<EditText>(R.id.editRoomPassword)
        val btnAddRoom = findViewById<Button>(R.id.btnAddRoom)

        btnAddRoom.setOnClickListener {
            val name = roomNameField.text.toString().trim()
            val password = roomPasswordField.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Podaj nazwę pokoju", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val room = RoomData(
                        name = name,
                        password = password,
                        avatar = "https://example.com/default-avatar.png",
                        description = "",
                        imagesSettings = "",
                        isPrivate = false,
                        isVisible = true,
                        idAdmin = ""
                    )
                    val addedRoom = repository.addRoom(room)
                    if (addedRoom != null) {
                        Toast.makeText(this@MainActivity2, "✅ Pokój dodany: ${addedRoom.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "❌ Dodawanie pokoju nieudane", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity2, "❌ Błąd dodawania pokoju", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val roomIdField = findViewById<EditText>(R.id.editRoomId)
        val btnDeleteRoom = findViewById<Button>(R.id.btnDeleteRoom)

        btnDeleteRoom.setOnClickListener {
            val roomId = roomIdField.text.toString().trim()
            if (roomId.isEmpty()) {
                Toast.makeText(this, "Podaj ID pokoju do usunięcia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = repository.deleteRoom(roomId)
                    if (success) {
                        Toast.makeText(this@MainActivity2, "✅ Pokój usunięty", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "❌ Usuwanie pokoju nieudane", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity2, "❌ Błąd usuwania pokoju", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val editUpdateRoomName = findViewById<EditText>(R.id.editUpdateRoomName)
        val editUpdateRoomAvatar = findViewById<EditText>(R.id.editUpdateRoomAvatar)
        val editUpdateRoomPassword = findViewById<EditText>(R.id.editUpdateRoomPassword)
        val checkUpdatePrivate = findViewById<CheckBox>(R.id.checkUpdatePrivate)
        val checkUpdateVisible = findViewById<CheckBox>(R.id.checkUpdateVisible)
        val btnUpdateRoom = findViewById<Button>(R.id.btnUpdateRoom)

        btnUpdateRoom.setOnClickListener {
            val name = editUpdateRoomName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Podaj nazwę pokoju", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val avatar = editUpdateRoomAvatar.text.toString().trim()
            val password = editUpdateRoomPassword.text.toString().trim()
            val isPrivate = checkUpdatePrivate.isChecked
            val isVisible = checkUpdateVisible.isChecked

            lifecycleScope.launch {
                try {
                    val room = RoomData(
                        name = name,
                        avatar = avatar.ifEmpty { "https://example.com/default-avatar.png" },
                        password = password,
                        isPrivate = isPrivate,
                        isVisible = isVisible,
                        idAdmin = ""
                    )
                    val updated = repository.updateRoom(room)
                    if (updated != null) {
                        Toast.makeText(this@MainActivity2, "✅ Pokój zaktualizowany: ${updated.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "❌ Aktualizacja pokoju nieudana", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity2, "❌ Błąd aktualizacji pokoju", Toast.LENGTH_SHORT).show()
                }
            }
        }


        val btnShowMyRooms = findViewById<Button>(R.id.btnShowMyRooms)

        btnShowMyRooms.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val myRooms = repository.getMyRooms()
                    if (myRooms.isEmpty()) {
                        Toast.makeText(this@MainActivity2, "Nie masz jeszcze pokoi", Toast.LENGTH_SHORT).show()
                    } else {
                        val roomNames = myRooms.joinToString(", ") { it.name }
                        Toast.makeText(this@MainActivity2, "Twoje pokoje: $roomNames", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity2, "Błąd pobierania pokoi", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }

        val btnShowAllRooms = findViewById<Button>(R.id.btnShowAllRooms)

        btnShowAllRooms.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val allRooms = repository.getAllRooms()
                    if (allRooms.isEmpty()) {
                        Toast.makeText(this@MainActivity2, "Brak widocznych pokoi na serwerze", Toast.LENGTH_SHORT).show()
                    } else {
                        val roomNames = allRooms.joinToString(", ") { it.name }
                        Toast.makeText(this@MainActivity2, "Wszystkie pokoje: $roomNames", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity2, "Błąd pobierania pokoi", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }

        val editGetRoomId = findViewById<EditText>(R.id.editGetRoomId)
        val btnGetRoomAndUsers = findViewById<Button>(R.id.btnGetRoomAndUsers)

        btnGetRoomAndUsers.setOnClickListener {
            val roomId = editGetRoomId.text.toString().trim()
            if (roomId.isEmpty()) {
                Toast.makeText(this, "Podaj ID pokoju", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val result = repository.getRoomAndUsers(roomId)
                    if (result != null) {
                        val (roomData, users) = result

                        // Wyciągamy loginy
                        val userLogins = users.joinToString(", ") { it.name }

                        Toast.makeText(
                            this@MainActivity2,
                            "Pokój: ${roomData.name}\nUżytkownicy: $userLogins",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "❌ Brak danych dla tego pokoju", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity2, "❌ Błąd pobierania danych pokoju", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnAddUserToRoom = findViewById<Button>(R.id.btnAddUserToRoom)
        val editAddUserRoomId = findViewById<EditText>(R.id.editAddUserRoomId)
        val editAddUserLogin = findViewById<EditText>(R.id.editAddUserLogin)

        btnAddUserToRoom.setOnClickListener {

            val roomId = editAddUserRoomId.text.toString().trim()
            val login = editAddUserLogin.text.toString().trim()
            if (roomId.isEmpty() || login.isEmpty()) {
                Toast.makeText(this, "Podaj ID pokoju i login użytkownika", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = repository.addUserToRoom(roomId, login)
                    if (success) {
                        Toast.makeText(this@MainActivity2, "✅ Dodano $login do pokoju", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "❌ Nie udało się dodać użytkownika", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity2, "❌ Błąd dodawania użytkownika", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnJoinRoom = findViewById<Button>(R.id.btnJoinRoom)
        val editJoinRoomId = findViewById<EditText>(R.id.editJoinRoomId)
        val editJoinRoomPassword = findViewById<EditText>(R.id.editJoinRoomPassword)

        btnJoinRoom.setOnClickListener {
            val roomId = editJoinRoomId.text.toString().trim()
            val password = editJoinRoomPassword.text.toString().trim()

            if (roomId.isEmpty()) {
                Toast.makeText(this, "Podaj ID pokoju", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = repository.addMyselfToRoom(roomId, password)
                    if (success) {
                        Toast.makeText(this@MainActivity2, "✅ Dołączono do pokoju", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "❌ Nie udało się dołączyć", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity2, "❌ Błąd dołączania do pokoju", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnOpenChat = findViewById<Button>(R.id.btnOpenChat)
        btnOpenChat.setOnClickListener {
            val intent = Intent(this, MainActivity3::class.java)
            startActivity(intent)
        }



    }
}

