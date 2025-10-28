package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val TAG = "CryptoUtils"
    private const val RSA_KEY_SIZE = 2048
    private const val PREFS_NAME = "CryptoPrefs"
    private const val PRIVATE_KEY_PREFIX = "private_key_"
    private const val PUBLIC_KEY_PREFIX = "public_key_"

    private const val AES_KEY_SIZE = 256 // 256-bit AES
    private const val GCM_TAG_LENGTH = 128 // 128-bit tag for authentication
    private const val GCM_IV_LENGTH = 12 // 12 bytes IV for GCM

    /**
     * Hashuje hasło używając SHA-256
     */
//    fun hashPassword(password: String): String {
//        Log.d(TAG, "Hashowanie hasła...")
//        val digest = MessageDigest.getInstance("SHA-256")
//        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
//        val hash = hashBytes.joinToString("") { "%02x".format(it) }
//        Log.d(TAG, "Hasło zahashowane pomyślnie (długość: ${hash.length})")
//        return hash
//    }

    /**
     * Generuje parę kluczy RSA (2048 bit)
     */
    fun generateRSAKeys(): KeyPair {
        Log.d(TAG, "Rozpoczynam generowanie kluczy RSA...")
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(RSA_KEY_SIZE)
            val keyPair = keyPairGenerator.generateKeyPair()
            Log.d(TAG, "Klucze RSA wygenerowane pomyślnie")
            Log.d(TAG, "- Rozmiar klucza publicznego: ${keyPair.public.encoded.size} bajtów")
            Log.d(TAG, "- Rozmiar klucza prywatnego: ${keyPair.private.encoded.size} bajtów")
            return keyPair
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas generowania kluczy RSA: ${e.message}", e)
            throw e
        }
    }

    /**
     * Konwertuje klucz publiczny do formatu Base64 (do wysłania na serwer)
     */
    fun publicKeyToString(publicKey: PublicKey): String {
        Log.d(TAG, "Konwersja klucza publicznego do Base64...")
        val encoded = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        Log.d(TAG, "Klucz publiczny Base64 (pierwsze 50 znaków): ${encoded.take(50)}...")
        return encoded
    }

    /**
     * Konwertuje klucz prywatny do formatu Base64 (do zapisu w SharedPreferences)
     */
    fun privateKeyToString(privateKey: PrivateKey): String {
        Log.d(TAG, "Konwersja klucza prywatnego do Base64...")
        val encoded = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        Log.d(TAG, "Klucz prywatny zaszyfrowany (długość: ${encoded.length} znaków)")
        return encoded
    }

    /**
     * Odtwarza klucz publiczny z Base64
     */
    fun stringToPublicKey(keyString: String): PublicKey {
        Log.d(TAG, "Odtwarzanie klucza publicznego z Base64...")
        try {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            Log.d(TAG, "Klucz publiczny odtworzony pomyślnie")
            return publicKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania klucza publicznego: ${e.message}", e)
            throw e
        }
    }

    /**
     * Odtwarza klucz prywatny z Base64
     */
    fun stringToPrivateKey(keyString: String): PrivateKey {
        Log.d(TAG, "Odtwarzanie klucza prywatnego z Base64...")
        try {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)
            Log.d(TAG, "Klucz prywatny odtworzony pomyślnie")
            return privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania klucza prywatnego: ${e.message}", e)
            throw e
        }
    }

    /**
     * Zapisuje klucz prywatny w SharedPreferences dla konkretnego użytkownika
     */
    fun savePrivateKey(context: Context, userId: String, privateKey: PrivateKey) {
        Log.d(TAG, "Zapisywanie klucza prywatnego dla użytkownika: $userId")
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = privateKeyToString(privateKey)
            prefs.edit().putString("$PRIVATE_KEY_PREFIX$userId", keyString).apply()
            Log.d(TAG, "Klucz prywatny zapisany pomyślnie dla użytkownika: $userId")

            // Weryfikacja zapisu
            val saved = prefs.getString("$PRIVATE_KEY_PREFIX$userId", null)
            if (saved != null) {
                Log.d(TAG, "Weryfikacja: Klucz znajduje się w SharedPreferences")
            } else {
                Log.e(TAG, "BŁĄD: Klucz nie został zapisany w SharedPreferences!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas zapisywania klucza prywatnego: ${e.message}", e)
            throw e
        }
    }

    /**
     * Opcjonalnie zapisuje klucz publiczny (może być potrzebny lokalnie)
     */
    fun savePublicKey(context: Context, userId: String, publicKey: PublicKey) {
        Log.d(TAG, "Zapisywanie klucza publicznego dla użytkownika: $userId")
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = publicKeyToString(publicKey)
            prefs.edit().putString("$PUBLIC_KEY_PREFIX$userId", keyString).apply()
            Log.d(TAG, "Klucz publiczny zapisany pomyślnie dla użytkownika: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas zapisywania klucza publicznego: ${e.message}", e)
            throw e
        }
    }

    /**
     * Pobiera klucz prywatny dla konkretnego użytkownika
     */
    fun getPrivateKey(context: Context, userId: String): PrivateKey? {
        Log.d(TAG, "Pobieranie klucza prywatnego dla użytkownika: $userId")
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = prefs.getString("$PRIVATE_KEY_PREFIX$userId", null)

            if (keyString == null) {
                Log.w(TAG, "Brak klucza prywatnego dla użytkownika: $userId")
                return null
            }

            Log.d(TAG, "Klucz prywatny znaleziony, odtwarzanie...")
            val privateKey = stringToPrivateKey(keyString)
            Log.d(TAG, "Klucz prywatny pobrany i odtworzony pomyślnie")
            return privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas pobierania klucza prywatnego: ${e.message}", e)
            return null
        }
    }

    /**
     * Pobiera klucz publiczny dla konkretnego użytkownika
     */
    fun getPublicKey(context: Context, userId: String): PublicKey? {
        Log.d(TAG, "Pobieranie klucza publicznego dla użytkownika: $userId")
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = prefs.getString("$PUBLIC_KEY_PREFIX$userId", null)

            if (keyString == null) {
                Log.w(TAG, "Brak klucza publicznego dla użytkownika: $userId")
                return null
            }

            Log.d(TAG, "Klucz publiczny znaleziony, odtwarzanie...")
            return stringToPublicKey(keyString)
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas pobierania klucza publicznego: ${e.message}", e)
            return null
        }
    }

    /**
     * Usuwa klucze dla konkretnego użytkownika (np. przy wylogowaniu/usunięciu konta)
     */
    fun deleteKeysForUser(context: Context, userId: String) {
        Log.d(TAG, "Usuwanie kluczy dla użytkownika: $userId")
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("$PRIVATE_KEY_PREFIX$userId")
                .remove("$PUBLIC_KEY_PREFIX$userId")
                .apply()
            Log.d(TAG, "Klucze usunięte pomyślnie dla użytkownika: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas usuwania kluczy: ${e.message}", e)
        }
    }

    /**
     * Sprawdza czy użytkownik ma zapisane klucze
     */
    fun hasKeysForUser(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPrivate = prefs.contains("$PRIVATE_KEY_PREFIX$userId")
        val hasPublic = prefs.contains("$PUBLIC_KEY_PREFIX$userId")
        Log.d(TAG, "Sprawdzanie kluczy dla użytkownika $userId: private=$hasPrivate, public=$hasPublic")
        return hasPrivate
    }

    /**
     * Generuje nowy losowy klucz AES (256-bit)
     * Ten klucz będzie używany do szyfrowania wszystkich wiadomości w pokoju
     */
    fun generateAESKey(): SecretKey {
        Log.d(TAG, "Generowanie nowego klucza AES...")
        try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(AES_KEY_SIZE, SecureRandom())
            val secretKey = keyGenerator.generateKey()
            Log.d(TAG, "✓ Klucz AES wygenerowany pomyślnie")
            Log.d(TAG, "  - Algorytm: ${secretKey.algorithm}")
            Log.d(TAG, "  - Format: ${secretKey.format}")
            Log.d(TAG, "  - Rozmiar: ${secretKey.encoded.size} bajtów")
            return secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas generowania klucza AES: ${e.message}", e)
            throw e
        }
    }

    /**
     * Konwertuje klucz AES do formatu Base64 (do wysłania na serwer)
     */
    fun aesKeyToString(secretKey: SecretKey): String {
        Log.d(TAG, "Konwersja klucza AES do Base64...")
        val encoded = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        Log.d(TAG, "✓ Klucz AES Base64 (długość: ${encoded.length})")
        return encoded
    }

    /**
     * Odtwarza klucz AES z Base64
     */
    fun stringToAESKey(keyString: String): SecretKey {
        Log.d(TAG, "Odtwarzanie klucza AES z Base64...")
        try {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            Log.d(TAG, "✓ Klucz AES odtworzony pomyślnie")
            return secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania klucza AES: ${e.message}", e)
            throw e
        }
    }

    /**
     * Szyfruje wiadomość kluczem AES (GCM mode dla bezpieczeństwa)
     * @param message - tekst do zaszyfrowania
     * @param secretKey - klucz AES pokoju
     * @return Zaszyfrowana wiadomość (Base64: IV + ciphertext)
     */
    fun encryptMessage(message: String, secretKey: SecretKey): String {
        Log.d(TAG, "Szyfrowanie wiadomości...")
        Log.d(TAG, "  Długość wiadomości: ${message.length} znaków")

        try {
            // Generuj losowy IV (Initialization Vector)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // Skonfiguruj cipher w trybie GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Zaszyfruj
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val encryptedBytes = cipher.doFinal(messageBytes)

            // Połącz IV + zaszyfrowane dane
            val combined = iv + encryptedBytes
            val result = Base64.encodeToString(combined, Base64.NO_WRAP)

            Log.d(TAG, "✓ Wiadomość zaszyfrowana")
            Log.d(TAG, "  Długość zaszyfrowana: ${result.length} znaków")

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas szyfrowania: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deszyfruje wiadomość kluczem AES
     * @param encryptedMessage - zaszyfrowana wiadomość (Base64)
     * @param secretKey - klucz AES pokoju
     * @return Odszyfrowana wiadomość (plaintext)
     */
    fun decryptMessage(encryptedMessage: String, secretKey: SecretKey): String {
        Log.d(TAG, "Deszyfrowanie wiadomości...")

        try {
            // Dekoduj Base64
            val combined = Base64.decode(encryptedMessage, Base64.NO_WRAP)

            // Rozdziel IV i zaszyfrowane dane
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encryptedBytes = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            // Skonfiguruj cipher w trybie deszyfrowania
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Odszyfruj
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val result = String(decryptedBytes, Charsets.UTF_8)

            Log.d(TAG, "✓ Wiadomość odszyfrowana")
            Log.d(TAG, "  Długość: ${result.length} znaków")

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas deszyfrowania: ${e.message}", e)
            throw e
        }
    }

    /**
     * Szyfruje klucz AES kluczem publicznym RSA użytkownika
     * (Admin używa tego aby zaszyfrować klucz pokoju dla każdego użytkownika)
     */
    fun encryptAESKeyWithRSA(aesKey: SecretKey, rsaPublicKey: PublicKey): String {
        Log.d(TAG, "Szyfrowanie klucza AES kluczem RSA...")

        try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)

            val encryptedKeyBytes = cipher.doFinal(aesKey.encoded)
            val result = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)

            Log.d(TAG, "✓ Klucz AES zaszyfrowany kluczem RSA")
            Log.d(TAG, "  Długość: ${result.length} znaków")

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas szyfrowania klucza AES: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deszyfruje klucz AES kluczem prywatnym RSA użytkownika
     * (Użytkownik używa tego aby odszyfrować klucz pokoju)
     */
    fun decryptAESKeyWithRSA(encryptedAESKey: String, rsaPrivateKey: PrivateKey): SecretKey {
        Log.d(TAG, "Deszyfrowanie klucza AES kluczem RSA...")

        try {
            val encryptedKeyBytes = Base64.decode(encryptedAESKey, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey)

            val decryptedKeyBytes = cipher.doFinal(encryptedKeyBytes)
            val secretKey = SecretKeySpec(decryptedKeyBytes, "AES")

            Log.d(TAG, "✓ Klucz AES odszyfrowany kluczem RSA")

            return secretKey

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas deszyfrowania klucza AES: ${e.message}", e)
            throw e
        }
    }
}