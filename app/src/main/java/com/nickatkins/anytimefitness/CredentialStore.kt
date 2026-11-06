package com.nickatkins.anytimefitness

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CredentialStore {

    private const val PREFS_NAME = "club_os_creds"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, username: String, password: String) {
        prefs(context).edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun load(context: Context): Pair<String?, String?> {
        val p = prefs(context)
        return p.getString(KEY_USERNAME, null) to p.getString(KEY_PASSWORD, null)
    }

    fun hasCredentials(context: Context): Boolean {
        val (u, p) = load(context)
        return !u.isNullOrBlank() && !p.isNullOrBlank()
    }
}
