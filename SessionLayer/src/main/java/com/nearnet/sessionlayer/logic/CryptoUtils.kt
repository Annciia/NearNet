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

    private const val AES_KEY_SIZE = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12



    //generowanie pary kluczy RSA - przy rejestracji
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


    //konwersja klucza publicznego na Bsae64
    fun publicKeyToString(publicKey: PublicKey): String {
        Log.d(TAG, "Konwersja klucza publicznego do Base64")
        val encoded = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return encoded
    }


    //konwersja klucza prywatnego do Base64
    fun privateKeyToString(privateKey: PrivateKey): String {
        Log.d(TAG, "Konwersja klucza prywatnego do Base64...")
        val encoded = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        Log.d(TAG, "Klucz prywatny zaszyfrowany (długość: ${encoded.length} znaków)")
        return encoded
    }


    //Base64->klucz publiczny
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


    //Base64-> klucz prywatny
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


    //zapisanie klucza prywatnego w SharedPreferences + weryfikacja zapisu
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


    //zapisanie klucza publicznego w SharedPreferences(lokalnie) - klucz publiczny jest na serwerze i tak
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


    //pobieranie klucza prywatnego
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


    //pobieranie klucza publicznego lokalnie
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



    //sprawdzenie czy klucze sa zapisane lokalnie
    fun hasKeysForUser(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPrivate = prefs.contains("$PRIVATE_KEY_PREFIX$userId")
        val hasPublic = prefs.contains("$PUBLIC_KEY_PREFIX$userId")
        Log.d(TAG, "Sprawdzanie kluczy dla użytkownika $userId: private=$hasPrivate, public=$hasPublic")
        return hasPrivate
    }


    //generacja klucza aes do szyfrowania wiadomosci w pokoju
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


    //konwersja klucza AES do Base64
    fun aesKeyToString(secretKey: SecretKey): String {
        Log.d(TAG, "Konwersja klucza AES do Base64...")
        val encoded = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        return encoded
    }


    //Base64->AES
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

    /**
     szyfrowanie wiadomosci kluczem AES
     zwraca zaszyfrowana wiadomosc (Base64: IV + ciphertext)
     **/

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



    //deszyfruje wiadomosci kluczem AES
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


    //szyfrowanie klucza AES kluczem poblicznym RSA uzytkownika
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


    //deszyfrowanie klucza AES kluczem prywatnym
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

    fun encryptStringWithRSA(plaintext: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
    }

    fun decryptStringWithRSA(encrypted: String, privateKey: PrivateKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedBytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}