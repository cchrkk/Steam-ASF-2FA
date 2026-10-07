package tk.chrk.qrloginapprover.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the ASF base URL and the device token in encrypted preferences, so the app only needs to be
 * paired once and never keeps the ASF IPCPassword.
 */
class SettingsStore(context: Context) {
	private val masterKey = MasterKey.Builder(context)
		.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
		.build()

	private val prefs = EncryptedSharedPreferences.create(
		context,
		"asf_qrlogin",
		masterKey,
		EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
		EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
	)

	var baseUrl: String?
		get() = prefs.getString("baseUrl", null)?.takeIf { it.isNotBlank() }
		set(value) {
			prefs.edit().putString("baseUrl", value).apply()
		}

	var token: String?
		get() = prefs.getString("token", null)?.takeIf { it.isNotBlank() }
		set(value) {
			prefs.edit().putString("token", value).apply()
		}

	var deviceName: String
		get() = prefs.getString("deviceName", null)?.takeIf { it.isNotBlank() } ?: "Android"
		set(value) {
			prefs.edit().putString("deviceName", value).apply()
		}

	val isPaired: Boolean
		get() = (baseUrl != null) && (token != null)

	fun unpair() {
		prefs.edit().remove("token").apply()
	}
}
