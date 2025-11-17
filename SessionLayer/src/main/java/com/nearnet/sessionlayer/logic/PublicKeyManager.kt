package com.nearnet.sessionlayer.logic
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.PublicKey

class PublicKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "PublicKeyManager"
    }

    private val userRepository = UserRepository(context)


    /**
     * Pobiera klucz publiczny RSA użytkownika z serwera
     *
     * @param userId ID użytkownika, którego klucz chcemy pobrać
     * @return Klucz publiczny RSA użytkownika lub null jeśli nie udało się pobrać
     */
    suspend fun getPublicKeyForUser(userId: String): PublicKey? = withContext(Dispatchers.IO) {


        try {
            //pobranie klucza publicznego z serwera
            val publicKeyBase64 = userRepository.getUserPublicKey(userId)

            if (publicKeyBase64.isNullOrEmpty()) {
                Log.e(TAG, "Nie udało się pobrać klucza publicznego z serwera")
                return@withContext null
            }

            // Konwersja Base64 -> PublicKey
            val publicKey = CryptoUtils.stringToPublicKey(publicKeyBase64)

            return@withContext publicKey

        } catch (e: Exception) {
            Log.e(TAG, "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

}