package com.nearnet.sessionlayer.logic
import android.content.Context
import android.util.Log
import com.nearnet.sessionlayer.logic.CryptoUtils
import com.nearnet.sessionlayer.logic.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.PublicKey

class PublicKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "PublicKeyManager"
    }

    private val userRepository = UserRepository(context)


    //pobiera klucz publiczny uzytkownika
    suspend fun getPublicKeyForUser(userId: String): PublicKey? = withContext(Dispatchers.IO) {


        try {
            //pobranie klucza publicznego z serwera
            val publicKeyBase64 = userRepository.getUserPublicKey(userId)

            if (publicKeyBase64.isNullOrEmpty()) {
                Log.e(TAG, "Nie udało się pobrac klucza publicznego")
                return@withContext null
            }

            // Base64->PublicKey
            val publicKey = CryptoUtils.stringToPublicKey(publicKeyBase64)

            return@withContext publicKey

        } catch (e: Exception) {
            Log.e(TAG, "  Wiadomość: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

}