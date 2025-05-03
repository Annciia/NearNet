package com.nearnet.sessionlayer.logic

import android.content.Context
import android.widget.Toast
import com.nearnet.sessionlayer.data.db.AppDatabase
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.sessionlayer.network.SocketClient
import com.nearnet.sessionlayer.data.PackageCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.util.*


class UserRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun registerUser(
        userName: String,
        password: String,
        avatar: String,
        publicKey: String
    ): PackageCommand = withContext(Dispatchers.IO) {
        val idUser = UUID.randomUUID().toString()
        val passwordHash = CryptoUtils.hashPassword(password)

        val user = UserData(
            idUser = idUser,
            name = userName,
            avatar = avatar,
            publicKey = publicKey,
            passwordHash = passwordHash,
            darkLightMode = false
        )

        // zapis do lokalnej bazy danych
        db.userDao().insertUser(user)

        // paczka do serwera
        val data = "$idUser|$userName|$avatar|$publicKey|$passwordHash"
        val pkg = PackageCommand(
            roomID = "",
            command = "registerUser",
            data = data
        )

        // wysyłka do serwera tutaj jeszcze odbior musze dodac
        SocketClient.sendAndReceive(pkg)

        return@withContext pkg
    }

    suspend fun loginUser(userName: String, password: String): PackageCommand = withContext(Dispatchers.IO) {

        val user = db.userDao().getUserByName(userName)


        if (user == null) {
            return@withContext PackageCommand(
                roomID = "",
                command = "loginUserFailed",
                data = "User not found"
            )
        }

        val passwordHash = CryptoUtils.hashPassword(password)

        if (user.passwordHash == passwordHash) {

            val data = "$userName|$user.passwordHash"
            val pkg = PackageCommand(
                roomID = "",
                command = "loginUser",
                data = data
            )

            SocketClient.sendAndReceive(pkg)

            return@withContext pkg
        } else {
            return@withContext PackageCommand(
                roomID = "",
                command = "loginUserFailed",
                data = "Invalid password"
            )
        }
    }

    suspend fun getAllUsers(): List<UserData> {
        return db.userDao().getAllUsers()
    }

    suspend fun logOutUser(idUser: String) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Wylogowywanie uzytkownika: $idUser", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun deleteUser(idUser: String) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Usuwanie uzytkownika: $idUser", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun updateUser(user: UserData) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Aktualizacja uzytkownika: ${user.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
