package cl.kcorp.syncpass.data.remote

import cl.kcorp.syncpass.BuildConfig

object SupabaseConfig {
    val url: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val anonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank()
}
