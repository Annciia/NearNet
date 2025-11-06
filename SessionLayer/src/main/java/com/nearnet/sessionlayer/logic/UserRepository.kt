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
        .baseUrl("http://$SERVER_ADDRESS:$SERVER_PORT")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(ApiService::class.java)



    suspend fun registerUser(login: String, password: String): Boolean = withContext(Dispatchers.IO) {

        try {
            //generowanie kluczy RSA
            val keyPair = CryptoUtils.generateRSAKeys()

            //konwersja klucza publicznego do Base64
            val publicKeyString = CryptoUtils.publicKeyToString(keyPair.public)

            //przygotowanie danych do wysłania
            val body = mapOf(
                "login" to login.trim(),
                "password" to password.trim(),
                "publicKey" to publicKeyString
            )

            //wysłanie zadania do serwera
            val response = api.register(body)
            Log.d("REST", "Otrzymano odpowiedź HTTP: ${response.code()}")

            if (response.isSuccessful) {
                val registerResponse = response.body()
                Log.d("REST", "✓ Rejestracja zakończona pomyślnie!")
                Log.d("REST", "  Response body: $registerResponse")
                Log.d("REST", "  Success: ${registerResponse?.success}")

                // zapisanie kluczy lokalnie
                //TODO mozna przy rejestracji zwracac ID
                // serwer nie zwraca userId, uzywamy loginu jako identyfikatora
                val keyIdentifier = login.trim()

                CryptoUtils.savePrivateKey(context, keyIdentifier, keyPair.private)
                CryptoUtils.savePublicKey(context, keyIdentifier, keyPair.public)
                Log.d("REST", "Klucze zapisane dla identyfikatora: $keyIdentifier")

                // weryfikacja zapisu - debugowanie
                val hasKeys = CryptoUtils.hasKeysForUser(context, keyIdentifier)
                Log.d("REST", "Klucze zapisane dla '$keyIdentifier': $hasKeys")

                if (hasKeys) {
                    // dodatkowa weryfikacja - spróbuj odczytać klucz
                    val retrievedPrivateKey = CryptoUtils.getPrivateKey(context, keyIdentifier)
                    if (retrievedPrivateKey != null) {
                        Log.d("REST", "Klucz prywatny mozna odczytac z SharedPreferences")
                    } else {
                        Log.e("REST", "Nie mozna odczytac klucza prywatnego!")
                    }
                } else {
                    Log.e("REST", "Weryfikacja negatywna - problem z zapisem kluczy!")
                }

                return@withContext true

            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e("REST", "✗ Rejestracja nieudana!")
                Log.e("REST", "  HTTP Code: ${response.code()}")
                Log.e("REST", "  Error Body: $errorMsg")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("REST", "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }


    suspend fun loginUser(login: String, password: String): UserData = withContext(Dispatchers.IO) {
        //czyszczenie starego tokenu
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

            val userData = res.userData.copy(login = login.trim())

            Log.d("LOGIN_DEBUG", "Mapped UserData: $userData")

            try {
                val keysOk = verifyAndRegenerateKeysIfNeeded(userData.id, login.trim())
                if (keysOk) {
                    Log.d("LOGIN_DEBUG", "RSA keys verified/regenerated successfully")
                } else {
                    Log.w("LOGIN_DEBUG", "RSA keys verification had issues (but login continues)")
                    // Nie przerywamy logowania - user może dalej korzystać
                }
            } catch (e: Exception) {
                Log.e("LOGIN_DEBUG", "Exception during key verification", e)
            }
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


        val payload: MutableMap<String, String> = mutableMapOf(
            "name" to (user.name ?: ""),
            "avatar" to (user.avatar ?: ""),
            "publicKey" to (user.publicKey ?: ""),
            "additionalSettings" to (user.additionalSettings ?: "")
        )

        // dodanie obsługi zmiany hasła jeśli pola są wypełnione i zgodne
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

    //zapisanie tokenu w SharedPreferences
    private fun saveTokenToPreferences(token: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_token", token)
            apply()
        }
    }
    //zapisanie loginu w SharedPreferences
    private fun saveLoginToPreferences(login: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_login", login)
            apply()
        }
    }

    //czyszczenie tokena z SharedPreferences
    private fun clearToken() {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("user_token")
            apply()
        }
    }
    //pobieranie tokenu/loginu
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

    //pobieranie klucza publicznego innego uzytkownika
    suspend fun getUserPublicKey(userId: String): String? = withContext(Dispatchers.IO) {

        val token = getTokenFromPreferences(context) ?: return@withContext null

        try {
            val response = api.getUserPublicKey(userId, "Bearer $token")
            Log.d("REST", "HTTP response code: ${response.code()}")

            if (response.isSuccessful) {
                val publicKeyResponse = response.body()
                Log.d("REST", "  UserId: ${publicKeyResponse?.id}")

                if (publicKeyResponse?.publicKey.isNullOrEmpty()) {
                    Log.w("REST", "Uzytkownik nie ma klucza publicznego")
                    return@withContext null
                }

                Log.d("REST", "Pobrano klucz publiczny")
                return@withContext publicKeyResponse?.publicKey

            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("REST", "  HTTP Code: ${response.code()}")
                Log.e("REST", "  Error Body: $errorBody")

                if (response.code() == 404) {
                    Log.e("REST", "Użytkownik nie został znaleziony")
                }
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e("REST", "  Bład:: ${e.message}")
            return@withContext null
        }
    }
    //aktualizacja klucza publicznego na serwerze,
    private suspend fun updatePublicKeyOnServer(publicKeyString: String): Boolean = withContext(Dispatchers.IO) {
        val token = getTokenFromPreferences(context) ?: return@withContext false

        Log.d("USER_KEYS", "Updating public key on server...")

        //aktualizacja public key na serwerze przy uzyciu wczesniejszej funkcji updateUser
        val payload: Map<String, String> = mapOf(
            "publicKey" to publicKeyString
        )

        try {
            val response = api.updateUser("Bearer $token", payload)

            if (response.isSuccessful) {
                val raw = response.body()?.string()
                Log.d("USER_KEYS", "✓ Server accepted new public key")
                Log.d("USER_KEYS", "  Response: $raw")
                return@withContext true
            } else {
                val errorText = response.errorBody()?.string() ?: "Unknown error"
                Log.e("USER_KEYS", "✗ Server rejected public key: ${response.code()} $errorText")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("USER_KEYS", "✗ Exception sending public key to server", e)
            return@withContext false
        }
    }

    suspend fun verifyAndRegenerateKeysIfNeeded(userId: String, login: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("USER_KEYS", "=== Verifying RSA keys for user: $login (ID: $userId) ===")

        //sprawdzanie, czy uzytkownik ma klucze RSA lokalnie
        val hasKeys = CryptoUtils.hasKeysForUser(context, login)
        Log.d("USER_KEYS", "User has local keys: $hasKeys")

        if (hasKeys) {
            // sprawdzenie, czy klucze sa czytelne
            val privateKey = CryptoUtils.getPrivateKey(context, login)
            val publicKey = CryptoUtils.getPublicKey(context, login)

            if (privateKey != null && publicKey != null) {
                Log.d("USER_KEYS", "Keys are valid and readable")
                return@withContext true
            } else {
                Log.w("USER_KEYS", "Keys exist but are corrupted - regenerating")
            }
        } else {
            Log.w("USER_KEYS", "User does NOT have keys - new device detected")
        }

        //jesli nie ma kluczy generuje nowe
        Log.d("USER_KEYS", "Generating new RSA key pair...")

        try {
            val newKeyPair = CryptoUtils.generateRSAKeys()
            Log.d("USER_KEYS", "New key pair generated")

            //zapis kluczy prywatnie
            CryptoUtils.savePrivateKey(context, login, newKeyPair.private)
            CryptoUtils.savePublicKey(context, login, newKeyPair.public)
            Log.d("USER_KEYS", "Keys saved locally")

            // weryfikacja zapisu
            val savedSuccessfully = CryptoUtils.hasKeysForUser(context, login)
            if (!savedSuccessfully) {
                Log.e("USER_KEYS", "Failed to save keys locally!")
                return@withContext false
            }

            //wyslanie nowego klucza publicznego na serwer
            val publicKeyString = CryptoUtils.publicKeyToString(newKeyPair.public)

            val success = updatePublicKeyOnServer(publicKeyString)

            if (success) {
                Log.d("USER_KEYS", "Public key sent to server successfully")
                Log.d("USER_KEYS", "Key regeneration complete")
                return@withContext true
            } else {
                Log.e("USER_KEYS", "Failed to send public key to server")
                // llucze są zapisane lokalnie, ale serwer ich nie ma
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("USER_KEYS", "✗ Exception during key regeneration", e)
            return@withContext false
        }
    }






}




