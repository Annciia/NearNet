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

    /**
     * Pobiera klucz publiczny użytkownika
     * Używa istniejącego endpointa: GET /api/users/{id}/publicKey
     */
    suspend fun getPublicKeyForUser(userId: String): PublicKey? = withContext(Dispatchers.IO) {
        Log.d(TAG, "====== POBIERANIE KLUCZA PUBLICZNEGO ======")
        Log.d(TAG, "UserId: $userId")

        try {
            // ============================================
            // KROK 1: Pobierz klucz publiczny z serwera
            // ============================================
            Log.d(TAG, "--- Krok 1: Wywołanie UserRepository.getUserPublicKey ---")

            val publicKeyBase64 = userRepository.getUserPublicKey(userId)

            if (publicKeyBase64.isNullOrEmpty()) {
                Log.e(TAG, "✗ Nie udało się pobrać klucza publicznego")
                Log.e(TAG, "  Użytkownik może nie mieć klucza RSA")
                return@withContext null
            }

            Log.d(TAG, "✓ Klucz publiczny pobrany z serwera")
            Log.d(TAG, "  Długość: ${publicKeyBase64.length} znaków")
            Log.d(TAG, "  Pierwsze 50 znaków: ${publicKeyBase64.take(50)}...")

            // ============================================
            // KROK 2: Konwertuj klucz z Base64 do PublicKey
            // ============================================
            Log.d(TAG, "--- Krok 2: Konwersja klucza publicznego ---")

            val publicKey = CryptoUtils.stringToPublicKey(publicKeyBase64)

            Log.d(TAG, "✓ Klucz publiczny przekonwertowany pomyślnie")
            Log.d(TAG, "  Algorytm: ${publicKey.algorithm}")
            Log.d(TAG, "  Format: ${publicKey.format}")
            Log.d(TAG, "====== POBIERANIE ZAKOŃCZONE SUKCESEM ======")

            return@withContext publicKey

        } catch (e: Exception) {
            Log.e(TAG, "✗✗ WYJĄTEK podczas pobierania klucza publicznego!", e)
            Log.e(TAG, "  Typ: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Wiadomość: ${e.message}")
            e.printStackTrace()
            Log.d(TAG, "====== POBIERANIE ZAKOŃCZONE BŁĘDEM ======")
            return@withContext null
        }
    }

    /**
     * Pobiera klucze publiczne dla wielu użytkowników jednocześnie
     */
    suspend fun getPublicKeysForUsers(userIds: List<String>): Map<String, PublicKey> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Pobieranie kluczy publicznych dla ${userIds.size} użytkowników")

        val keys = mutableMapOf<String, PublicKey>()

        for (userId in userIds) {
            val publicKey = getPublicKeyForUser(userId)
            if (publicKey != null) {
                keys[userId] = publicKey
                Log.d(TAG, "✓ Klucz pobrany dla użytkownika: $userId")
            } else {
                Log.w(TAG, "⚠ Nie można pobrać klucza dla użytkownika: $userId")
            }
        }

        Log.d(TAG, "Pobrano ${keys.size}/${userIds.size} kluczy publicznych")
        return@withContext keys
    }
}