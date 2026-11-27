package studio.virel.office

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 노션 통합 시크릿(ntn_...)을 Android Keystore의 AES-256-GCM 키로 암호화해 저장한다.
 * 키 자체는 기기 하드웨어(가능하면 TEE/StrongBox)에 있고 앱 밖으로 꺼낼 수 없다.
 * EncryptedSharedPreferences(androidx.security-crypto)는 지원 중단(deprecated)되어 같은 방식을 직접 구현했다.
 * 토큰은 WebView(JavaScript)로 절대 넘기지 않는다. 노션 호출 때 네이티브 쪽에서만 헤더에 붙인다.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("virel_secure", Context.MODE_PRIVATE)

    fun has(): Boolean = prefs.contains(PREF_KEY)

    fun get(): String? {
        val stored = prefs.getString(PREF_KEY, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_LEN)
            val body = raw.copyOfRange(IV_LEN, raw.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            // 키가 사라졌거나(앱 데이터 초기화 등) 손상된 경우: 다시 입력받도록 비운다.
            clear()
            null
        }
    }

    fun set(token: String) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val out = cipher.iv + body
        prefs.edit().putString(PREF_KEY, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove(PREF_KEY).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "virel_notion_token"
        const val PREF_KEY = "notion_token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
