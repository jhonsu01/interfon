package com.jhonsu.interfon

import android.content.Context

/** Ajustes persistentes de la app (direccion del servidor). */
object Prefs {
    private const val FILE = "interfon"
    private const val KEY_URL = "server_url"
    private const val KEY_SPEAKER = "speaker"
    private const val KEY_AGENT_NAME = "agent_name"
    const val DEFAULT_URL = "http://192.168.1.50:8765"
    const val DEFAULT_AGENT_NAME = "ZCode"

    fun url(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun setUrl(ctx: Context, value: String) {
        val v = value.trim().removeSuffix("/")
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, v).apply()
    }

    /** false = auricular (llamada normal), true = altavoz. */
    fun speaker(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SPEAKER, false)

    fun setSpeaker(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SPEAKER, value).apply()
    }

    fun agentName(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_AGENT_NAME, DEFAULT_AGENT_NAME) ?: DEFAULT_AGENT_NAME

    fun setAgentName(ctx: Context, value: String) {
        val v = value.trim().ifEmpty { DEFAULT_AGENT_NAME }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_AGENT_NAME, v).apply()
    }
}
