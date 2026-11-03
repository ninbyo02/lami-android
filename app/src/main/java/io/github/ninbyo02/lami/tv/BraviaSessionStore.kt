package io.github.ninbyo02.lami.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BraviaSession(
    val address: String,
    val clientId: String,
    val cookie: String? = null,
    val model: String = "",
    val macAddress: String? = null,
    val enabled: Boolean = true,
) {
    override fun toString() = "BraviaSession(address=$address, model=$model, enabled=$enabled, credentials=<redacted>)"
}

interface BraviaSessionStore {
    fun load(): BraviaSession?
    fun save(session: BraviaSession)
    fun clear()
}

/** Cookie is an application credential: Android Keystore encryption and no-backup storage.
 * The one-time TV PIN is never written to disk. */
internal class AndroidBraviaSessionStore(context: Context) : BraviaSessionStore {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "bravia-session.enc"))
    private val alias = "lami_bravia_session_v1"
    private val aad = "lami-bravia-v1".toByteArray(Charsets.UTF_8)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun load(): BraviaSession? {
        if (!file.baseFile.exists()) return null
        try {
            val data = braviaGson.fromJson(file.readFully().toString(Charsets.UTF_8), com.google.gson.JsonObject::class.java)
            require(data.get("version").asInt == 1)
            val iv = Base64.decode(data.get("iv").asString, Base64.NO_WRAP)
            require(iv.size == 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)); cipher.updateAAD(aad)
            val plain = cipher.doFinal(Base64.decode(data.get("data").asString, Base64.NO_WRAP))
            return braviaGson.fromJson(plain.toString(Charsets.UTF_8), BraviaSession::class.java)
        } catch (_: Exception) {
            throw BraviaException("テレビの登録情報を読み出せません。設定から登録を解除し、再登録してください。")
        }
    }

    override fun save(session: BraviaSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(braviaGson.toJson(session).toByteArray(Charsets.UTF_8))
        val data = braviaJson("version" to 1, "iv" to Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            "data" to Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(data); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
    }

    override fun clear() { file.delete() }
}
