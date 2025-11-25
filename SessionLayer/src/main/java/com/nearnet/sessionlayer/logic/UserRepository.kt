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

// ============================================================================
// MODELE DANYCH - Struktury odpowiedzi API
// ============================================================================

//Odpowiedź serwera na rejestrację użytkownika
data class RegisterResponse(
    val success: Boolean = false,
    val error: String? = null
)

//Odpowiedź serwera na logowanie użytkownika
data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val userData: UserData?
)

//Odpowiedź zawierająca klucz publiczny użytkownika
data class PublicKeyResponse(
    val id: String,
    val publicKey: String
)

// ============================================================================
// RETROFIT API SERVICE - Definicje endpointów użytkownika
// ============================================================================

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

// ============================================================================
// USER REPOSITORY - Główna klasa zarządzania użytkownikami
// ============================================================================

class UserRepository(private val context: Context) {
    //private val db = AppDatabase.getDatabase(context)

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://$SERVER_ADDRESS:$SERVER_PORT")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(ApiService::class.java)

    // ============================================================================
    // FUNKCJE REJESTRACJI I LOGOWANIA
    // ============================================================================

    /**
     * Rejestruje nowego użytkownika w systemie
     *
     * @param login Login użytkownika
     * @param password Hasło użytkownika
     * @return true jeśli rejestracja się powiodła, false w przeciwnym razie
     */
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
                Log.d("REST", "Rejestracja zakończona pomyślnie!")
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

    /**
     * Loguje użytkownika do systemu
     *
     *
     * @param login Login użytkownika
     * @param password Hasło użytkownika
     * @return Dane użytkownika (UserData)
     */
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
        // wysłanie żądania logowania
        val response = api.login(body)
        Log.d("LOGIN_DEBUG", "HTTP response code: ${response.code()}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e("LOGIN_DEBUG", "HTTP error body: $errorBody")
            throw Exception("Logowanie nieudane: ${response.code()}")
        }

        val res = response.body()
        if (res == null) {
            Log.e("LOGIN_DEBUG", "Response body is null")
            throw Exception("Logowanie nieudane: pusta odpowiedź")
        }

        Log.d("LOGIN_DEBUG", "Response: success=${res.success}, token=${res.token}, userData=${res.userData}")

        if (res.success && res.token != null && res.userData != null) {
            // zapisanie tokenu i loginu
            saveTokenToPreferences(res.token)
            saveLoginToPreferences(login.trim())

            val userData = res.userData.copy(login = login.trim())

            Log.d("LOGIN_DEBUG", "Mapped UserData: $userData")
            // weryfikacja i regeneracja kluczy RSA jeśli potrzeba
            try {
                val keysOk = verifyAndRegenerateKeysIfNeeded(userData.id, login.trim())
                if (keysOk) {
                    Log.d("LOGIN_DEBUG", "Klucze RSA zweryfikowane/zregenerowane pomyślnie")
                } else {
                    Log.w("LOGIN_DEBUG", "Problemy z weryfikacją kluczy RSA (ale logowanie kontynuowane)")
                    // Nie przerywamy logowania - user może dalej korzystać
                }
            } catch (e: Exception) {
                Log.e("LOGIN_DEBUG", "Wyjątek podczas weryfikacji kluczy", e)
            }
            return@withContext userData
        } else {
            if (!res.success) Log.e("LOGIN_DEBUG", "Logowanie nieudane: success=false")
            if (res.token == null) Log.e("LOGIN_DEBUG", "Logowanie nieudane: token=null")
            if (res.userData == null) Log.e("LOGIN_DEBUG", "Logowanie nieudane: userData=null")

            throw Exception("Logowanie nieudane: nieprawidłowe dane lub niekompletna odpowiedź")
        }
    }

    // ============================================================================
    // FUNKCJE AKTUALIZACJI I USUWANIA UŻYTKOWNIKA
    // ============================================================================

    /**
     * Aktualizuje dane użytkownika na serwerze
     *
     * @param user Dane użytkownika do aktualizacji
     * @param currentPassword Obecne hasło (opcjonalne, wymagane przy zmianie hasła)
     * @param newPassword Nowe hasło (opcjonalne)
     * @return true jeśli aktualizacja się powiodła, false w przeciwnym razie
     */
    suspend fun updateUser(user: UserData,
                           currentPassword: String = "",
                           newPassword: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val token = getTokenFromPreferences(context) ?: return@withContext false
        Log.d("REST", "Aktualizacja użytkownika dla token: $token")


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
                Log.d("REST", "Response: $json")

                if (json.trimStart().startsWith("<html", ignoreCase = true)) {
                    Log.w("REST", "Serwer zwrócił HTML zamiast JSON")
                }
                Log.e("KOT", ""+json)
                return@withContext true
            } else {
                val errorText = response.errorBody()?.string() ?: "Unknown error"
                Log.e("REST", "Aktualizacja nieudana: ${response.code()} $errorText")
            }
        } catch (e: Exception) {
            Log.e("REST", "Wyjątek podczas aktualizacji: ${e.message}")
        }
        return@withContext false

    }

    /**
     * Usuwa konto użytkownika
     *
     * @param password Hasło użytkownika (potwierdzenie)
     * @return true jeśli usunięcie się powiodło, false w przeciwnym razie
     */
    suspend fun deleteUser(password: String): Boolean = withContext(Dispatchers.IO) {
        val token = getTokenFromPreferences(context) ?: return@withContext false

        return@withContext try {
            val response = api.deleteUser("Bearer $token", mapOf("password" to password))
            if (response.isSuccessful) {
                val res = response.body()
                if (res?.success == true) {
                    Log.d("REST", "Użytkownik usunięty pomyślnie")
                    clearToken()
                    true
                } else {
                    Log.d("REST", "Usuwanie nieudane: ${res?.error}")
                    false
                }
            } else {
                Log.e("REST", "Błąd usuwania: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("REST", "Wyjątek podczas usuwania: ${e.message}")
            false
        }
    }

    /**
     * Wylogowuje użytkownika - czyści token z SharedPreferences
     *
     * @return true zawsze (operacja zawsze się powiedzie)
     */
    fun logOutUser(): Boolean {
        clearToken()
        return true
    }

    // ============================================================================
    // FUNKCJE ZARZĄDZANIA TOKENEM I LOGINEM W SHAREDPREFERENCES
    // ============================================================================

    /**
     * Zapisuje token w SharedPreferences
     *
     * @param token Token autoryzacyjny
     */
    private fun saveTokenToPreferences(token: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_token", token)
            apply()
        }
    }

    /**
     * Zapisuje login w SharedPreferences
     *
     * @param login Login użytkownika
     */
    private fun saveLoginToPreferences(login: String) {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_login", login)
            apply()
        }
    }

    /**
     * Czyści token z SharedPreferences
     */
    private fun clearToken() {
        val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("user_token")
            apply()
        }
    }


    companion object {

        // ============================================================================
        // FUNKCJE STATYCZNE - Pobieranie tokenu i loginu
        // ============================================================================

        /**
         * Pobiera token z SharedPreferences
         *
         * @return Token lub null jeśli nie istnieje
         */
        fun getTokenFromPreferences(context: Context): String? {
            val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return sharedPref.getString("user_token", null)
        }

        /**
         * Pobiera login z SharedPreferences
         *
         * @return Login lub null jeśli nie istnieje
         */
        fun getLoginFromPreferences(context: Context): String? {
            val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return sharedPref.getString("user_login", null)
        }
    }


    // ============================================================================
    // FUNKCJE ZARZĄDZANIA KLUCZAMI PUBLICZNYMI
    // ============================================================================

    /**
     * Pobiera klucz publiczny innego użytkownika z serwera
     *
     * @param userId ID użytkownika
     * @return Klucz publiczny w formacie Base64 lub null jeśli nie znaleziono
     */
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

    /**
     * Aktualizuje klucz publiczny na serwerze
     *
     * @param publicKeyString Klucz publiczny w formacie Base64
     * @return true jeśli aktualizacja się powiodła, false w przeciwnym razie
     */
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
                Log.d("USER_KEYS", "Serwer zaakceptował nowy klucz publiczny")
                Log.d("USER_KEYS", "Response: $raw")
                return@withContext true
            } else {
                val errorText = response.errorBody()?.string() ?: "Unknown error"
                Log.e("USER_KEYS", "Serwer odrzucił klucz publiczny: ${response.code()} $errorText")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("USER_KEYS", "Wyjątek podczas wysyłania klucza publicznego na serwer", e)
            return@withContext false
        }
    }

    /**
     * Weryfikuje i regeneruje klucze RSA jeśli potrzeba
     * Używane przy logowaniu na nowym urządzeniu
     *
     * @param userId ID użytkownika
     * @param login Login użytkownika
     * @return true jeśli klucze są prawidłowe/zregenerowane, false w przypadku błędu
     */
    suspend fun verifyAndRegenerateKeysIfNeeded(userId: String, login: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("USER_KEYS", "Weryfikacja kluczy RSA dla użytkownika: $login (ID: $userId)")

        //sprawdzanie, czy uzytkownik ma klucze RSA lokalnie
        val hasKeys = CryptoUtils.hasKeysForUser(context, login)
        Log.d("USER_KEYS", "Użytkownik ma lokalne klucze: $hasKeys")

        if (hasKeys) {
            // sprawdzenie, czy klucze sa czytelne
            val privateKey = CryptoUtils.getPrivateKey(context, login)
            val publicKey = CryptoUtils.getPublicKey(context, login)

            if (privateKey != null && publicKey != null) {
                Log.d("USER_KEYS", "Klucze są prawidłowe i czytelne")
                return@withContext true
            } else {
                Log.w("USER_KEYS", "Klucze istnieją ale są uszkodzone - regeneracja")
            }
        } else {
            Log.w("USER_KEYS", "Użytkownik NIE ma kluczy - wykryto nowe urządzenie")
        }

        //jesli nie ma kluczy generuje nowe
        Log.d("USER_KEYS", "Generowanie nowej pary kluczy RSA...")

        try {
            val newKeyPair = CryptoUtils.generateRSAKeys()
            Log.d("USER_KEYS", "Nowa para kluczy wygenerowana")

            //zapis kluczy prywatnie
            CryptoUtils.savePrivateKey(context, login, newKeyPair.private)
            CryptoUtils.savePublicKey(context, login, newKeyPair.public)
            Log.d("USER_KEYS", "Klucze zapisane lokalnie")

            // weryfikacja zapisu
            val savedSuccessfully = CryptoUtils.hasKeysForUser(context, login)
            if (!savedSuccessfully) {
                Log.e("USER_KEYS", "Nie udało się zapisać kluczy lokalnie!")
                return@withContext false
            }

            //wyslanie nowego klucza publicznego na serwer
            val publicKeyString = CryptoUtils.publicKeyToString(newKeyPair.public)

            val success = updatePublicKeyOnServer(publicKeyString)

            if (success) {
                Log.d("USER_KEYS", "Klucz publiczny wysłany na serwer pomyślnie")
                Log.d("USER_KEYS", "Regeneracja kluczy zakończona")
                return@withContext true
            } else {
                Log.e("USER_KEYS", "Nie udało się wysłać klucza publicznego na serwer")
                // llucze są zapisane lokalnie, ale serwer ich nie ma
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("USER_KEYS", "Wyjątek podczas regeneracji kluczy", e)
            return@withContext false
        }
    }

}




