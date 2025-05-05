package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.nearnet.sessionlayer.data.db.AppDatabase
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.sessionlayer.network.SocketClient
import com.nearnet.sessionlayer.data.PackageCommand
import io.socket.client.Ack
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

//1 pytanie - gdzie bedziemy przetrzymywac dane zalogowanego uzytkownika -shered raczej bo tam juz token trzymam

//Musze dopisac obsluge bazy jeszcze
class UserRepository(private val context: Context, private val socket: Socket) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun registerUser(
        userName: String,
        password: String,
        avatar: String,
    ) = withContext(Dispatchers.IO) {
        val idUser = UUID.randomUUID().toString()
        val passwordHash = CryptoUtils.hashPassword(password)
        val publicKey = CryptoUtils.generateRSAKeys()


        val user = UserData(
            idUser = idUser,
            name = userName,
            avatar = avatar,
            publicKey = publicKey.public.toString(),
            passwordHash = passwordHash,
            darkLightMode = false //wersja biala bazowo
        )

        // zapis do lokalnej bazy danych - tutaj jeszcze moze czekac na odp z serwera, ze ok rejestracja?
        db.userDao().insertUser(user)

        // paczka do serwera - teraz przesylamy obiekt JSON to narazie komentuje - do wyjebania potem
//        val data = "$idUser|$userName|$avatar|$publicKey|$passwordHash"
//        val pkg = PackageCommand(
//            roomID = "",
//            command = "registerUser",
//            data = data
//        )
        val data = JSONObject().apply {
            put("idUser", idUser)
            put("username", userName)
            put("avatar", avatar)
            put("publicKey", publicKey)
            put("passwordHash", passwordHash)
        }

        socket.emit("register_user", data, Ack { args ->
            val res = args[0] as JSONObject
            if (res.getBoolean("success")) {
                Log.d("Socket", "✅ Register success: ${res}")
                //TUTAJ DOPISUJE WSTAWIENIE UZYTKOWNIKA DO BAZY
            } else {
                Log.d("Socket", "❌ Register failed: ${res.getString("error")}")
            }
        })

    }

    suspend fun loginUser(userName: String, password: String) = withContext(Dispatchers.IO) {

        val user = db.userDao().getUserByName(userName)

//        //tutaj moze logowac z lokalnej bazy, ale chyba lepiej zawsze z serwera?
//        if (user == null) {
//            Log.d("Socket", "❌ Login failed: User not found in local DB")
//            return@withContext
//        }

        val data = JSONObject().apply {
            put("username", userName)
            put("password", password)
        }

        //mowiles, ze ma isc jawnie bo https bedzie szyfrowal, a na serwerze bedziesz hashowal?
        //val passwordHash = CryptoUtils.hashPassword(password)

        socket.emit("login", data, Ack { args ->
            val res = args[0] as JSONObject
            if (res.getBoolean("success")) {
                val token = res.getString("token")
                Log.d("Socket", "✅ Login success! Token: $token")
                //zapisanie tokenu z logowania, zeby potem moc sie autoryzowac
                saveTokenToPreferences(token)
                //TUTAJ USTAWIAM UZYTKOWNIKA JAKO ZALOGOWANEGO
            } else {
                Log.d("Socket", "❌ Login failed on server: ${res.getString("error")}")
            }
        })
    }

    private fun saveTokenToPreferences(token: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_token", token)
            apply()
        }
    }

    companion object {
        fun getTokenFromPreferences(context: Context): String? {
            val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return sharedPref.getString("user_token", null)
        }
    }

    //funkcja pobierajaca uzytkownikow z lokalnej BD
    suspend fun getAllUsers(): List<UserData> {
        return db.userDao().getAllUsers()
    }



    //funkcja pomocnicza, zeby wyciagnac id z BD po nazwie
    suspend fun getUserIdByName(userName: String): String? = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserByName(userName)
        return@withContext user?.idUser
    }

    //pobiera id z lokalnej bazy danych po name i wysyla na serwer - tutaj musze ogarnac jak obecnie zalogowanego uzytkownika trzymac na apce
    //zeby tylko jego mozna bylo usunac
    suspend fun logOutUser(userName: String) = withContext(Dispatchers.IO) {
        val userId = getUserIdByName(userName)

        if (userId == null) {
            Log.d("Socket", "❌ Logout failed: User '$userName' not found in local DB")
            return@withContext
        }

        val data = JSONObject().apply {
            put("userId", userId)
        }

        socket.emit("logout", data, Ack { args ->
            val res = args[0] as JSONObject
            if (res.getBoolean("success")) {
                Log.d("Socket", "✅ Logout success for user: $userName ($userId)")
                //TUTAJ USUWAM UZYTKOWNIKA Z ZALOGOWANEGO
            } else {
                val error = res.optString("error", "Unknown error")
                Log.d("Socket", "❌ Logout failed on server: $error")
            }
        })
    }

    //wersja tylko z Toast
//    suspend fun logOutUser(idUser: String) = withContext(Dispatchers.IO) {
//        withContext(Dispatchers.Main) {
//            Toast.makeText(context, "Wylogowywanie uzytkownika: $idUser", Toast.LENGTH_SHORT).show()
//        }
//    }
    //tutaj taka sama sytuacja jak logOut
    suspend fun deleteUser(idUser: String) = withContext(Dispatchers.IO) {
        val user = db.userDao().getAllUsers().find { it.idUser == idUser }

        if (user == null) {
            Log.d("Socket", "❌ Delete failed: User with id $idUser not found in local DB")
            return@withContext
        }

        val data = JSONObject().apply {
            put("userId", idUser)
        }

        socket.emit("delete_user", data, Ack { args ->
            val res = args[0] as JSONObject
            if (res.getBoolean("success")) {
                Log.d("Socket", "✅ User deleted successfully: ${user.name} ($idUser)")
                //TUTAJ USUWAM UZYKOWNIKA Z BD
                // db.userDao().deleteUser(user)
            } else {
                val error = res.optString("error", "Unknown error")
                Log.d("Socket", "❌ Delete failed on server: $error")
            }
        })
    }

    //wersja tylko z printem do testowania
//    suspend fun deleteUser(idUser: String) = withContext(Dispatchers.IO) {
//        withContext(Dispatchers.Main) {
//            Toast.makeText(context, "Usuwanie uzytkownika: $idUser", Toast.LENGTH_SHORT).show()
//        }
//    }

    suspend fun updateUser(user: UserData) = withContext(Dispatchers.IO) {
        // Szukamy użytkownika w lokalnej bazie danych
        val existingUser = db.userDao().getAllUsers().find { it.idUser == user.idUser }

        // Jeżeli użytkownik nie istnieje, zwracamy błąd
        if (existingUser == null) {
            Log.d("Socket", "❌ Update failed: User with id ${user.idUser} not found in local DB")
            return@withContext
        }

        // Przygotowanie danych do wysłania na serwer
        val data = JSONObject().apply {
            put("userId", user.idUser)
            put("name", user.name)
            put("avatar", user.avatar)
            put("publicKey", user.publicKey)
            put("passwordHash", user.passwordHash)
            put("darkLightMode", user.darkLightMode)
        }

        socket.emit("update_user", data, Ack { args ->
            val res = args[0] as JSONObject
            if (res.getBoolean("success")) {
                Log.d("Socket", "✅ User updated successfully: ${user.name} (${user.idUser})")
                //AKTUALIZACJA UZYTKOWNIKA W BD
            } else {

                Log.d("Socket", "❌ Update failed on server!")
            }
        })
    }



//    suspend fun updateUser(user: UserData) = withContext(Dispatchers.IO) {
//        withContext(Dispatchers.Main) {
//            Toast.makeText(context, "Aktualizacja uzytkownika: ${user.name}", Toast.LENGTH_SHORT).show()
//        }
//    }

}


