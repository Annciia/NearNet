package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
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

// ============================================================================
// CRYPTO UTILS - Narzędzia kryptograficzne dla aplikacji NearNet
// ============================================================================

/**
 * Singleton zawierający funkcje kryptograficzne dla aplikacji
 *
 * Obsługuje:
 * - Generowanie i zarządzanie kluczami RSA (szyfrowanie asymetryczne)
 * - Generowanie i zarządzanie kluczami AES (szyfrowanie symetryczne)
 * - Szyfrowanie/deszyfrowanie wiadomości
 * - Szyfrowanie kluczy AES kluczami RSA
 * - Konwersje kluczy do/z Base64
 * - Zapis i odczyt kluczy z SharedPreferences
 */

object CryptoUtils {
    private const val TAG = "CryptoUtils"

    // Konfiguracja RSA
    private const val RSA_KEY_SIZE = 2048
    private const val PREFS_NAME = "CryptoPrefs"
    private const val PRIVATE_KEY_PREFIX = "private_key_"
    private const val PUBLIC_KEY_PREFIX = "public_key_"

    // Konfiguracja AES-GCM
    private const val AES_KEY_SIZE = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12


    // ============================================================================
    // FUNKCJE ZARZĄDZANIA KLUCZAMI RSA
    // ============================================================================

    /**
     * Generuje parę kluczy RSA (publiczny + prywatny)
     *
     * Używane przy rejestracji nowego użytkownika
     * Rozmiar klucza: 2048 bitów
     *
     * @return Para kluczy RSA (KeyPair)
     */
    fun generateRSAKeys(): KeyPair {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(RSA_KEY_SIZE)
            val keyPair = keyPairGenerator.generateKeyPair()
            Log.d(TAG, "Klucze RSA wygenerowane pomyślnie")
            return keyPair
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas generowania kluczy RSA: ${e.message}", e)
            throw e
        }
    }


    /**
     * Konwertuje klucz publiczny RSA do formatu Base64
     *
     * @param publicKey Klucz publiczny do konwersji
     * @return Klucz publiczny jako string Base64
     */
    fun publicKeyToString(publicKey: PublicKey): String {
        Log.d(TAG, "Konwersja klucza publicznego do Base64")
        val encoded = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return encoded
    }


    /**
     * Konwertuje klucz prywatny RSA do formatu Base64
     *
     * @param privateKey Klucz prywatny do konwersji
     * @return Klucz prywatny jako string Base64
     */
    fun privateKeyToString(privateKey: PrivateKey): String {
        Log.d(TAG, "Konwersja klucza prywatnego do Base64...")
        val encoded = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        Log.d(TAG, "Klucz prywatny zaszyfrowany (długość: ${encoded.length} znaków)")
        return encoded
    }


    /**
     * Konwertuje string Base64 na klucz publiczny RSA
     *
     * @param keyString Klucz publiczny w formacie Base64
     * @return Obiekt PublicKey
     */
    fun stringToPublicKey(keyString: String): PublicKey {
        Log.d(TAG, "Odtwarzanie klucza publicznego z Base64")
        try {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            return publicKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania klucza publicznego: ${e.message}", e)
            throw e
        }
    }


    /**
     * Konwertuje string Base64 na klucz prywatny RSA
     *
     * @param keyString Klucz prywatny w formacie Base64
     * @return Obiekt PrivateKey
     */
    fun stringToPrivateKey(keyString: String): PrivateKey {
        Log.d(TAG, "Odtwarzanie klucza prywatnego z Base64")
        try {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)
            return privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania klucza prywatnego: ${e.message}", e)
            throw e
        }
    }

    // ============================================================================
    // FUNKCJE ZAPISU I ODCZYTU KLUCZY RSA Z SHAREDPREFERENCES
    // ============================================================================

    /**
     * Zapisuje klucz prywatny w SharedPreferences
     *
     * Klucz jest konwertowany do Base64 i zapisywany lokalnie
     * Po zapisie następuje weryfikacja poprawności zapisu
     *
     * @param userId ID użytkownika (login)
     * @param privateKey Klucz prywatny do zapisania
     */
    fun savePrivateKey(context: Context, userId: String, privateKey: PrivateKey) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = privateKeyToString(privateKey)
            prefs.edit().putString("$PRIVATE_KEY_PREFIX$userId", keyString).apply()
            Log.d(TAG, "Klucz prywatny zapisany pomyślnie dla uzytkownika: $userId")

            // weryfikacja zapisu
            val saved = prefs.getString("$PRIVATE_KEY_PREFIX$userId", null)
            if (saved != null) {
                Log.d(TAG, "Weryfikacja: Klucz znajduje się w SharedPreferences")
            } else {
                Log.e(TAG, "Klucz nie został zapisany w SharedPreferences!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas zapisywania klucza prywatnego: ${e.message}", e)
            throw e
        }
    }


    /**
     * Zapisuje klucz publiczny w SharedPreferences (lokalnie)
     *
     * @param userId ID użytkownika (login)
     * @param publicKey Klucz publiczny do zapisania
     */
    fun savePublicKey(context: Context, userId: String, publicKey: PublicKey) {
        Log.d(TAG, "Zapisywanie klucza publicznego dla uzytkownika: $userId")
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = publicKeyToString(publicKey)
            prefs.edit().putString("$PUBLIC_KEY_PREFIX$userId", keyString).apply()
            Log.d(TAG, "Klucz publiczny zapisany pomyslnie dla użytkownika: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas zapisywania klucza publicznego: ${e.message}", e)
            throw e
        }
    }


    /**
     * Pobiera klucz prywatny z SharedPreferences
     *
     * @param userId ID użytkownika (login)
     * @return Klucz prywatny lub null jeśli nie znaleziono
     */
    fun getPrivateKey(context: Context, userId: String): PrivateKey? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyString = prefs.getString("$PRIVATE_KEY_PREFIX$userId", null)

            if (keyString == null) {
                Log.w(TAG, "Brak klucza prywatnego dla użytkownika: $userId")
                return null
            }
            val privateKey = stringToPrivateKey(keyString)
            return privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas pobierania klucza prywatnego: ${e.message}", e)
            return null
        }
    }


    /**
     * Pobiera klucz publiczny z SharedPreferences (lokalnie)
     *
     * @param userId ID użytkownika (login)
     * @return Klucz publiczny lub null jeśli nie znaleziono
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
     * Sprawdza czy użytkownik ma zapisane klucze lokalnie
     *
     * @param userId ID użytkownika (login)
     * @return true jeśli klucz prywatny istnieje, false w przeciwnym razie
     */
    fun hasKeysForUser(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPrivate = prefs.contains("$PRIVATE_KEY_PREFIX$userId")
        val hasPublic = prefs.contains("$PUBLIC_KEY_PREFIX$userId")
        Log.d(TAG, "Sprawdzanie kluczy dla użytkownika $userId: private=$hasPrivate, public=$hasPublic")
        return hasPrivate
    }

    // ============================================================================
    // FUNKCJE ZARZĄDZANIA KLUCZAMI AES
    // ============================================================================

    /**
     * Generuje klucz AES do szyfrowania wiadomości w pokoju
     * Algorytm: AES-256
     * Używany do szyfrowania wiadomości w pokojach prywatnych
     * @return Wygenerowany klucz AES
     */
    fun generateAESKey(): SecretKey {
        try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(AES_KEY_SIZE, SecureRandom())
            val secretKey = keyGenerator.generateKey()
            Log.d(TAG, "Klucz AES wygenerowany pomyślnie")
            return secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas generowania klucza AES: ${e.message}", e)
            throw e
        }
    }

    /**
     * Konwertuje klucz AES do formatu Base64
     * @param secretKey Klucz AES do konwersji
     * @return Klucz AES jako string Base64
     */
    fun aesKeyToString(secretKey: SecretKey): String {
        Log.d(TAG, "Konwersja klucza AES do Base64...")
        val encoded = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        return encoded
    }


    /**
     * Konwertuje string Base64 na klucz AES
     * @param keyString Klucz AES w formacie Base64
     * @return Obiekt SecretKey (klucz AES)
     */
    fun stringToAESKey(keyString: String): SecretKey {
        Log.d(TAG, "Odtwarzanie klucza AES z Base64")
        try {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            Log.d(TAG, "Klucz AES odtworzony pomyślnie")
            return secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania klucza AES: ${e.message}", e)
            throw e
        }
    }

    // ============================================================================
    // FUNKCJE SZYFROWANIA/DESZYFROWANIA WIADOMOŚCI (AES-GCM)
    // ============================================================================


    /**
     * Szyfruje wiadomość kluczem AES (tryb AES-GCM)
     *
     * Proces:
     * 1. Generuje losowy wektor inicjalizacyjny (IV) - 12 bajtów
     * 2. Szyfruje wiadomość w trybie AES/GCM/NoPadding
     * 3. Łączy IV + zaszyfrowane dane
     * 4. Koduje całość do Base64
     *
     * @param message Wiadomość do zaszyfrowania (plaintext)
     * @param secretKey Klucz AES
     * @return Zaszyfrowana wiadomość w formacie Base64 (IV + ciphertext)
     */
    fun encryptMessage(message: String, secretKey: SecretKey): String {
        Log.d(TAG, "Szyfrowanie wiadomości...")
        Log.d(TAG, "  Długość wiadomości: ${message.length} znaków")

        try {
            // generacja losowego IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // cipher w trybie GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // szyfrowanie wiadomosci
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val encryptedBytes = cipher.doFinal(messageBytes)

            // polaczenie IV + zaszyfrowane dane
            val combined = iv + encryptedBytes
            val result = Base64.encodeToString(combined, Base64.NO_WRAP)


            return result

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas szyfrowania: ${e.message}", e)
            throw e
        }
    }

    /**
     * Odszyfrowuje wiadomość kluczem AES (tryb AES-GCM)
     *
     * Proces:
     * 1. Dekoduje Base64
     * 2. Rozdziela IV (pierwsze 12 bajtów) od zaszyfrowanych danych
     * 3. Odszyfrowuje dane w trybie AES/GCM/NoPadding
     * 4. Weryfikuje integralność (tag GCM)
     * 5. Zwraca odszyfrowaną wiadomość
     *
     * @param encryptedMessage Zaszyfrowana wiadomość w formacie Base64 (IV + ciphertext)
     * @param secretKey Klucz AES
     * @return Odszyfrowana wiadomość (plaintext)
     */
    fun decryptMessage(encryptedMessage: String, secretKey: SecretKey): String {
        Log.d(TAG, "Deszyfrowanie wiadomości...")

        try {
            // dekoduj Base64
            val combined = Base64.decode(encryptedMessage, Base64.NO_WRAP)

            // rozdzielenie IV i zaszyfrowanych danych
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encryptedBytes = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            //konfiguracja cipher w trybie deszyfrowania
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            //odszyfrowanie
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val result = String(decryptedBytes, Charsets.UTF_8)


            return result

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas deszyfrowania: ${e.message}", e)
            throw e
        }
    }

    // ============================================================================
    // FUNKCJE SZYFROWANIA KLUCZY AES KLUCZAMI RSA
    // ============================================================================

    /**
     * Szyfruje klucz AES kluczem publicznym RSA użytkownika
     *
     * Używane do bezpiecznego przekazywania kluczy AES między użytkownikami
     * Algorytm: RSA/ECB/PKCS1Padding
     *
     * @param aesKey Klucz AES do zaszyfrowania
     * @param rsaPublicKey Klucz publiczny RSA użytkownika docelowego
     * @return Zaszyfrowany klucz AES w formacie Base64
     */
    fun encryptAESKeyWithRSA(aesKey: SecretKey, rsaPublicKey: PublicKey): String {

        try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)

            val encryptedKeyBytes = cipher.doFinal(aesKey.encoded)
            val result = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas szyfrowania klucza AES: ${e.message}", e)
            throw e
        }
    }


    /**
     * Odszyfrowuje klucz AES kluczem prywatnym RSA
     *
     * Używane do odbioru klucza AES zaszyfrowanego naszym kluczem publicznym
     * Algorytm: RSA/ECB/PKCS1Padding
     *
     * @param encryptedAESKey Zaszyfrowany klucz AES w formacie Base64
     * @param rsaPrivateKey Nasz klucz prywatny RSA
     * @return Odszyfrowany klucz AES
     */
    fun decryptAESKeyWithRSA(encryptedAESKey: String, rsaPrivateKey: PrivateKey): SecretKey {

        try {
            val encryptedKeyBytes = Base64.decode(encryptedAESKey, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey)

            val decryptedKeyBytes = cipher.doFinal(encryptedKeyBytes)
            val secretKey = SecretKeySpec(decryptedKeyBytes, "AES")

            return secretKey

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas deszyfrowania klucza AES: ${e.message}", e)
            throw e
        }
    }

    // ============================================================================
    // FUNKCJE SZYFROWANIA/DESZYFROWANIA STRINGÓW RSA
    // ============================================================================

    /**
     * Szyfruje dowolny string kluczem publicznym RSA
     *
     * Używane do:
     * - Szyfrowania haseł pokojów
     *
     * Algorytm: RSA/ECB/PKCS1Padding
     *
     * @param plaintext Tekst do zaszyfrowania
     * @param publicKey Klucz publiczny RSA
     * @return Zaszyfrowany tekst w formacie Base64
     */
    fun encryptStringWithRSA(plaintext: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
    }

    /**
     * Odszyfrowuje string zaszyfrowany kluczem RSA
     *
     * Używane do:
     * - Odszyfrowywania haseł pokojów
     *
     * Algorytm: RSA/ECB/PKCS1Padding
     *
     * @param encrypted Zaszyfrowany tekst w formacie Base64
     * @param privateKey Klucz prywatny RSA
     * @return Odszyfrowany tekst (plaintext)
     */
    fun decryptStringWithRSA(encrypted: String, privateKey: PrivateKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedBytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}