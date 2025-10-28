package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log
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

data class PublicKeyResponse(
    val id: String,
    val publicKey: String
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

    @GET("/api/users/{id}/publicKey")
    suspend fun getUserPublicKey(
        @Path("id") userId: String,
        @Header("Authorization") auth: String
    ): Response<PublicKeyResponse>
}

class UserRepository(private val context: Context) {
    //private val db = AppDatabase.getDatabase(context)

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://95.108.77.201:3002")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(ApiService::class.java)


//    suspend fun registerUser(login: String, password: String): Boolean = withContext(Dispatchers.IO) {
//        val body = mapOf("login" to login.trim(), "password" to password.trim())
//
//
//        return@withContext try {
//            val response = api.register(body)
//
//            if (response.isSuccessful) {
//                Log.d("REST", "Rejestracja OK dla loginu: $login")
//                true
//            } else {
//                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
//                Log.d("REST", "Rejestracja nieudana: $errorMsg (HTTP ${response.code()})")
//                false
//            }
//
//        } catch (e: Exception) {
//            Log.e("REST", "Wyjątek podczas rejestracji: ${e.message}", e)
//            false
//        }
//    }

    suspend fun registerUser(login: String, password: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("REST", "====== ROZPOCZĘCIE REJESTRACJI ======")
        Log.d("REST", "Login: $login")
        Log.d("REST", "Długość hasła: ${password.length}")

        try {
            // Krok 1: Generowanie kluczy RSA
            Log.d("REST", "--- Krok 1: Generowanie kluczy RSA ---")
            val keyPair = CryptoUtils.generateRSAKeys()
            Log.d("REST", "✓ Para kluczy wygenerowana pomyślnie")

            // Krok 2: Konwersja klucza publicznego do Base64
            Log.d("REST", "--- Krok 2: Konwersja klucza publicznego ---")
            val publicKeyString = CryptoUtils.publicKeyToString(keyPair.public)
            Log.d("REST", "✓ Klucz publiczny (Base64, długość: ${publicKeyString.length})")
            Log.d("REST", "  Początek: ${publicKeyString.take(50)}...")
            Log.d("REST", "  Koniec: ...${publicKeyString.takeLast(50)}")

            // Krok 3: Przygotowanie danych do wysłania
            Log.d("REST", "--- Krok 3: Przygotowanie żądania rejestracji ---")
            val body = mapOf(
                "login" to login.trim(),
                "password" to password.trim(),
                "publicKey" to publicKeyString
            )
            Log.d("REST", "Body request:")
            Log.d("REST", "  - login: ${body["login"]}")
            Log.d("REST", "  - password: [HIDDEN]")
            Log.d("REST", "  - publicKey: ${publicKeyString.take(30)}...${publicKeyString.takeLast(30)}")

            // Krok 4: Wysłanie żądania do serwera
            Log.d("REST", "--- Krok 4: Wysyłanie żądania do serwera ---")
            val response = api.register(body)
            Log.d("REST", "Otrzymano odpowiedź HTTP: ${response.code()}")

            if (response.isSuccessful) {
                val registerResponse = response.body()
                Log.d("REST", "✓ Rejestracja zakończona pomyślnie!")
                Log.d("REST", "  Response body: $registerResponse")
                Log.d("REST", "  Success: ${registerResponse?.success}")

                // Krok 5: Zapisanie klucza prywatnego lokalnie
                // Ponieważ serwer nie zwraca userId, używamy loginu jako identyfikatora
                Log.d("REST", "--- Krok 5: Zapisywanie klucza prywatnego ---")
                Log.d("REST", "Serwer nie zwraca userId - używam loginu jako identyfikatora")
                val keyIdentifier = login.trim()
                Log.d("REST", "Identyfikator kluczy: $keyIdentifier")

                CryptoUtils.savePrivateKey(context, keyIdentifier, keyPair.private)
                CryptoUtils.savePublicKey(context, keyIdentifier, keyPair.public)
                Log.d("REST", "✓ Klucze zapisane dla identyfikatora: $keyIdentifier")

                // Weryfikacja zapisu
                Log.d("REST", "--- Weryfikacja zapisanych kluczy ---")
                val hasKeys = CryptoUtils.hasKeysForUser(context, keyIdentifier)
                Log.d("REST", "Klucze zapisane dla '$keyIdentifier': $hasKeys")

                if (hasKeys) {
                    Log.d("REST", "✓ Weryfikacja pozytywna - klucze są dostępne")

                    // Dodatkowa weryfikacja - spróbuj odczytać klucz
                    val retrievedPrivateKey = CryptoUtils.getPrivateKey(context, keyIdentifier)
                    if (retrievedPrivateKey != null) {
                        Log.d("REST", "✓✓ Klucz prywatny można odczytać z SharedPreferences")
                    } else {
                        Log.e("REST", "✗✗ BŁĄD: Nie można odczytać klucza prywatnego!")
                    }
                } else {
                    Log.e("REST", "✗ Weryfikacja negatywna - problem z zapisem kluczy!")
                }

                Log.d("REST", "====== REJESTRACJA ZAKOŃCZONA SUKCESEM ======")
                return@withContext true

            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e("REST", "✗ Rejestracja nieudana!")
                Log.e("REST", "  HTTP Code: ${response.code()}")
                Log.e("REST", "  Error Body: $errorMsg")
                Log.d("REST", "====== REJESTRACJA ZAKOŃCZONA NIEPOWODZENIEM ======")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("REST", "✗✗ WYJĄTEK podczas rejestracji!", e)
            Log.e("REST", "  Typ wyjątku: ${e.javaClass.simpleName}")
            Log.e("REST", "  Wiadomość: ${e.message}")
            Log.e("REST", "  Stack trace:")
            e.printStackTrace()
            Log.d("REST", "====== REJESTRACJA ZAKOŃCZONA BŁĘDEM ======")
            return@withContext false
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
            saveLoginToPreferences(login.trim())

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


    suspend fun updateUser(user: UserData,
                           currentPassword: String = "",
                           newPassword: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val token = getTokenFromPreferences(context) ?: return@withContext false
        Log.d("REST", "Updating user for token: $token")
//        val body = mapOf(
//            "name" to user.name,
//            "avatar" to user.avatar,
//            "publicKey" to user.publicKey,
//            "additionalSettings" to user.additionalSettings
//        )

        val payload: MutableMap<String, String> = mutableMapOf(
            "name" to (user.name ?: ""),
            "avatar" to (user.avatar ?: ""),
            "publicKey" to (user.publicKey ?: ""),
            "additionalSettings" to (user.additionalSettings ?: "")
        )

        // Dodanie obsługi zmiany hasła jeśli pola są wypełnione i zgodne
        if (newPassword.isNotEmpty() && currentPassword.isNotEmpty()) {
            payload["password"] = currentPassword
            payload["newPassword"] = newPassword
        }

        try {
            val response = api.updateUser("Bearer $token", payload)
            if (response.isSuccessful) {
                val raw = response.body()?.string()
                val json = raw ?: "{}"
                Log.d("REST", "Update response: $json")

                if (json.trimStart().startsWith("<html", ignoreCase = true)) {
                    Log.w("REST", "Server returned HTML instead of JSON (possible error page).")
                }
                Log.e("KOT", ""+json)
                return@withContext true
            } else {
                val errorText = response.errorBody()?.string() ?: "Unknown error"
                Log.e("REST", "Update failed: ${response.code()} $errorText")
            }
        } catch (e: Exception) {
            Log.e("REST", "Exception during update: ${e.message}")
        }
        return@withContext false

//        try {
//            val response = api.updateUser("Bearer $token", body)
//            if (response.isSuccessful) {
//                val json = response.body()?.string()
//                Log.d("REST", "Update response: $json")
//            } else {
//                Log.e("REST", "Update failed: ${response.code()} ${response.errorBody()?.string()}")
//            }
//        } catch (e: Exception) {
//            Log.e("REST", "Exception during update: ${e.message}")
//        }
    }

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

        fun getLoginFromPreferences(context: Context): String? {
            val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return sharedPref.getString("user_login", null)
        }
    }

    suspend fun getUserPublicKey(userId: String): String? = withContext(Dispatchers.IO) {
        Log.d("REST", "====== POBIERANIE KLUCZA PUBLICZNEGO ======")
        Log.d("REST", "UserId: $userId")

        val token = getTokenFromPreferences(context)
        if (token == null) {
            Log.e("REST", "✗ Brak tokena - użytkownik niezalogowany")
            return@withContext null
        }

        try {
            Log.d("REST", "Wysyłanie żądania do serwera...")
            val response = api.getUserPublicKey(userId, "Bearer $token")
            Log.d("REST", "HTTP response code: ${response.code()}")

            if (response.isSuccessful) {
                val publicKeyResponse = response.body()
                Log.d("REST", "✓ Odpowiedź otrzymana pomyślnie")
                Log.d("REST", "  UserId: ${publicKeyResponse?.id}")
                Log.d("REST", "  PublicKey długość: ${publicKeyResponse?.publicKey?.length ?: 0}")

                if (publicKeyResponse?.publicKey.isNullOrEmpty()) {
                    Log.w("REST", "⚠ Użytkownik nie ma klucza publicznego")
                    Log.d("REST", "====== POBIERANIE ZAKOŃCZONE (BRAK KLUCZA) ======")
                    return@withContext null
                }

                Log.d("REST", "  PublicKey (pierwsze 50 znaków): ${publicKeyResponse?.publicKey?.take(50)}...")
                Log.d("REST", "====== POBIERANIE ZAKOŃCZONE SUKCESEM ======")
                return@withContext publicKeyResponse?.publicKey

            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("REST", "✗ Błąd HTTP podczas pobierania klucza")
                Log.e("REST", "  HTTP Code: ${response.code()}")
                Log.e("REST", "  Error Body: $errorBody")

                if (response.code() == 404) {
                    Log.e("REST", "  Powód: Użytkownik nie został znaleziony")
                }

                Log.d("REST", "====== POBIERANIE ZAKOŃCZONE BŁĘDEM ======")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e("REST", "✗✗ WYJĄTEK podczas pobierania klucza!", e)
            Log.e("REST", "  Typ: ${e.javaClass.simpleName}")
            Log.e("REST", "  Wiadomość: ${e.message}")
            Log.d("REST", "====== POBIERANIE ZAKOŃCZONE BŁĘDEM ======")
            return@withContext null
        }
    }
    private fun saveLoginToPreferences(login: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_login", login)
            apply()
        }
    }



}




