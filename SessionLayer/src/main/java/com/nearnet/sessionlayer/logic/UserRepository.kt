package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nearnet.sessionlayer.data.db.AppDatabase
import com.nearnet.sessionlayer.data.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class RegisterResponse(
    val success: Boolean = false,
    val error: String? = null
)

data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val userData: UserData?
)


interface ApiService {
    @POST("/api/register")
    suspend fun register(@Body body: Map<String, String>): Response<RegisterResponse>

    @POST("/api/login")
    suspend fun login(@Body body: Map<String, String>): Response<LoginResponse>

    @PUT("/api/user")
    suspend fun updateUser(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    //z delete nie chcialo dzialac z serwerem
    @HTTP(method = "DELETE", path = "/api/user", hasBody = true)
    suspend fun deleteUser(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>
    ): Response<RegisterResponse>
}

class UserRepository(private val context: Context) {
    //private val db = AppDatabase.getDatabase(context)

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://95.108.77.201:3001")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(ApiService::class.java)


    suspend fun registerUser(login: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = mapOf("login" to login.trim(), "password" to password.trim())


        return@withContext try {
            val response = api.register(body)

            if (response.isSuccessful) {
                Log.d("REST", "Rejestracja OK dla loginu: $login")
                true
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.d("REST", "Rejestracja nieudana: $errorMsg (HTTP ${response.code()})")
                false
            }

        } catch (e: Exception) {
            Log.e("REST", "Wyjątek podczas rejestracji: ${e.message}", e)
            false
        }
    }

    suspend fun loginUser(login: String, password: String): UserData = withContext(Dispatchers.IO) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("user_token")
            apply()
        }

        val body = mapOf(
            "login" to login.trim(),
            "password" to password.trim()
        )
        Log.d("LOGIN_DEBUG", "Request body: $body")

        val response = api.login(body)
        Log.d("LOGIN_DEBUG", "HTTP response code: ${response.code()}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e("LOGIN_DEBUG", "HTTP error body: $errorBody")
            throw Exception("Login failed: ${response.code()}")
        }

        val res = response.body()
        if (res == null) {
            Log.e("LOGIN_DEBUG", "Response body is null")
            throw Exception("Login failed: empty response")
        }

        Log.d("LOGIN_DEBUG", "Response: success=${res.success}, token=${res.token}, userData=${res.userData}")

        if (res.success && res.token != null && res.userData != null) {
            saveTokenToPreferences(res.token)

            //TODO dodalem w UserData @SerializedName, zeby dopasowac nazwy z backendu do nas
            val userData = res.userData.copy(login = login.trim())

            Log.d("LOGIN_DEBUG", "Mapped UserData: $userData")
            return@withContext userData
        } else {
            if (!res.success) Log.e("LOGIN_DEBUG", "Login failed: success=false")
            if (res.token == null) Log.e("LOGIN_DEBUG", "Login failed: token=null")
            if (res.userData == null) Log.e("LOGIN_DEBUG", "Login failed: userData=null")

            throw Exception("Login failed: invalid credentials or incomplete response")
        }
    }


    suspend fun updateUser(user: UserData) = withContext(Dispatchers.IO) {
        val token = getTokenFromPreferences(context) ?: return@withContext
        Log.d("REST", "Updating user for token: $token")
        val body = mapOf(
            "name" to user.name,
            "avatar" to user.avatar,
            "publicKey" to user.publicKey,
            "additionalSettings" to user.additionalSettings
        )
        try {
            val response = api.updateUser("Bearer $token", body)
            if (response.isSuccessful) {
                val json = response.body()?.string()
                Log.d("REST", "Update response: $json")
            } else {
                Log.e("REST", "Update failed: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("REST", "Exception during update: ${e.message}")
        }
    }


    //zostaje 1:1 jak ze starym bo wysyla haslo, a otrzymuje boola
    suspend fun deleteUser(password: String): Boolean = withContext(Dispatchers.IO) {
        val token = getTokenFromPreferences(context) ?: return@withContext false

        return@withContext try {
            val response = api.deleteUser("Bearer $token", mapOf("password" to password))
            if (response.isSuccessful) {
                val res = response.body()
                if (res?.success == true) {
                    Log.d("REST", "User deleted")
                    clearToken()
                    true
                } else {
                    Log.d("REST", "Delete failed: ${res?.error}")
                    false
                }
            } else {
                Log.e("REST", "Delete error: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("REST", "Exception during delete: ${e.message}")
            false
        }
    }



    fun logOutUser(): Boolean {
        clearToken()
        return true
    }

    private fun saveTokenToPreferences(token: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_token", token)
            apply()
        }
    }

    private fun clearToken() {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("user_token")
            apply()
        }
    }

    companion object {
        fun getTokenFromPreferences(context: Context): String? {
            val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return sharedPref.getString("user_token", null)
        }
    }
}

//import android.content.Context
//import android.util.Log
//import com.nearnet.sessionlayer.data.db.AppDatabase
//import com.nearnet.sessionlayer.data.model.UserData
//import io.socket.client.Ack
//import io.socket.client.Socket
//import io.socket.emitter.Emitter
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.withContext
//import org.json.JSONObject
//import java.util.*
//import kotlinx.coroutines.launch
//
//
////1 pytanie - gdzie bedziemy przetrzymywac dane zalogowanego uzytkownika -shered raczej bo tam juz token trzymam
//
////musze dopisac obsluge bazy jeszcze
//class UserRepository(private val context: Context, private val socket: Socket) {
//
//    private val db = AppDatabase.getDatabase(context)
//
//    //dziala
//    suspend fun registerUser(
//        userName: String,
//        password: String,
//        avatar: String,
//    ) = withContext(Dispatchers.IO) {
//        // poczekaj, az socket będzie połączony
//        if (!socket.connected()) {
//            suspendCancellableCoroutine<Unit> { cont ->
//                socket.once(Socket.EVENT_CONNECT) { cont.resume(Unit) {} }
//                socket.connect()
//            }
//        }
//
//        val idUser = UUID.randomUUID().toString()
//        val passwordHash = CryptoUtils.hashPassword(password)
//        val publicKey = CryptoUtils.generateRSAKeys()
//
//        val user = UserData(
//            idUser = idUser,
//            name = userName,
//            avatar = avatar,
//            publicKey = publicKey.public.toString(),
//            passwordHash = passwordHash,
//            darkLightMode = false
//        )
//
//        // Zapis lokalny
//        db.userDao().insertUser(user)
//
//        val data = JSONObject().apply {
//            put("idUser", idUser)
//            put("name", userName)
//            put("avatar", avatar)
//            put("publicKey", publicKey)
//            put("passwordHash", passwordHash)
//        }
//
//
//        socket.once("register") { args ->
//            val response = args.getOrNull(0) as? JSONObject
//            val success = response?.optBoolean("success") ?: false
//            if (success) {
//                Log.d("Debug", "✅ Register success: ${response}")
//            } else {
//                Log.d("Debug", "❌ Register failed: ${response?.optString("error")}")
//            }
//
//
//
//    }
//        socket.emit("register", data)
//    }
//
//    //dziala
//    suspend fun loginUser(userName: String, password: String) = withContext(Dispatchers.IO) {
//
//        val user = db.userDao().getUserByName(userName)
//
//        val data = JSONObject().apply {
//            put("username", userName)
//            put("password", password)
//        }
//
//        //mowiles, ze ma isc jawnie bo https bedzie szyfrowal, a na serwerze bedziesz hashowal?
//        //val passwordHash = CryptoUtils.hashPassword(password)
//
//        socket.once("login_response") { args ->
//            val res = args.getOrNull(0) as? JSONObject
//            if (res != null) {
//                val success = res.optBoolean("success", false)
//                if (success) {
//                    val token = res.optString("token")
//                    Log.d("Debug", "✅ Login success! Token: $token")
//                    saveTokenToPreferences(token)
//                } else {
//                    Log.d("Debug", "❌ Login failed on server: ${res.optString("error")}")
//                }
//            } else {
//                Log.d("Debug", "❌ Login response empty or malformed")
//            }
//        }
//
//        socket.emit("login", data)
//
//
//    }
//
//    private fun saveTokenToPreferences(token: String) {
//        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
//        with(sharedPref.edit()) {
//            putString("user_token", token)
//            apply()
//        }
//    }
//
//    companion object {
//        fun getTokenFromPreferences(context: Context): String? {
//            val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
//            return sharedPref.getString("user_token", null)
//        }
//    }
//
//    //funkcja pobierajaca uzytkownikow z lokalnej BD
//    suspend fun getAllUsers(): List<UserData> {
//        return db.userDao().getAllUsers()
//    }
//
//    //funkcja pomocnicza, zeby wyciagnac id z BD po nazwie
//    suspend fun getUserIdByName(userName: String): String? = withContext(Dispatchers.IO) {
//        val user = db.userDao().getUserByName(userName)
//        return@withContext user?.idUser
//    }
//
//    suspend fun getUserByName(username: String): UserData? = withContext(Dispatchers.IO) {
//        db.userDao().getUserByName(username)
//    }
//
//    //pobiera id z lokalnej bazy danych po name i wysyla na serwer - tutaj musze ogarnac jak obecnie zalogowanego uzytkownika trzymac na apce
//    //zeby tylko jego mozna bylo usunac
//    suspend fun logOutUser(userName: String) = withContext(Dispatchers.IO) {
//        val userId = getUserIdByName(userName)
//
//        if (userId == null) {
//            Log.d("Socket", "❌ Logout failed: User '$userName' not found in local DB")
//            return@withContext
//        }
//
//        val data = JSONObject().apply {
//            put("userId", userId)
//        }
//
//        socket.emit("logout", data, Ack { args ->
//            val res = args[0] as JSONObject
//            if (res.getBoolean("success")) {
//                Log.d("Socket", "✅ Logout success for user: $userName ($userId)")
//                //TUTAJ USUWAM UZYTKOWNIKA Z ZALOGOWANEGO
//            } else {
//                val error = res.optString("error", "Unknown error")
//                Log.d("Socket", "❌ Logout failed on server: $error")
//            }
//        })
//    }
//
//    //tutaj ten token raczej mozna wyjebac bo i tak jest polaczenie na sockecie z tokenem nawiazywane
//    suspend fun deleteUser( password: String, token: String) = withContext(Dispatchers.IO) {
//        val data = JSONObject().apply {
//            put("password", password)
//            put("token", token)
//        }
//
//        // Hoist zmiennej
//        lateinit var listener: Emitter.Listener
//        listener = Emitter.Listener { args ->
//            val res = args.getOrNull(0) as? JSONObject ?: return@Listener
//            val success = res.getBoolean("success")
//            if (success) {
//                Log.d("Socket", "✅ User deleted successfully")
//            } else {
//                val error = res.optString("error", "Unknown error")
//                Log.d("Socket", "❌ Delete failed: $error")
//            }
//
//            // Wyrejestrowanie listenera
//            socket.off("delete_user_response", listener)
//        }
//
//        socket.on("delete_user_response", listener)
//        socket.emit("delete_user", data)
//    }
//
//
//    suspend fun updateUser(user: UserData) = withContext(Dispatchers.IO) {
//        val data = JSONObject().apply {
//            put("idUser", user.idUser)
//            put("name", user.name)
//            put("avatar", user.avatar)
//            put("publicKey", user.publicKey)
//            put("passwordHash", user.passwordHash)
//            put("darkLightMode", user.darkLightMode)
//        }
//
//        // najpierw usuwamy poprzedniego listenera, żeby nie dublował
//        socket.off("user_updated")
//
//        // listener odpowiedzi
//        socket.on("user_updated") { args ->
//            val res = args.getOrNull(0) as? JSONObject ?: return@on
//            val updatedUser = UserData(
//                idUser = res.getString("idUser"),
//                name = res.getString("name"),
//                avatar = res.getString("avatar"),
//                publicKey = res.getString("publicKey"),
//                passwordHash = res.getString("passwordHash"),
//                darkLightMode = res.getBoolean("darkLightMode")
//            )
//
//            // zamiast GlobalScope → od razu w nowym zakresie coroutine
//            CoroutineScope(Dispatchers.IO).launch {
//                db.userDao().updateUser(updatedUser)
//                Log.d("Socket", "✅ User updated successfully: ${updatedUser.name}")
//            }
//        }
//
//        // Wyślij dane do serwera
//        socket.emit("update_user", data)
//    }
//
//
//}


