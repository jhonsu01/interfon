package com.jhonsu.interfon

import android.content.Context

/** Ajustes persistentes de la app. La lista de servidores vive en [Servers]. */
object Prefs {
    private const val FILE = "interfon"
    const val KEY_URL = "server_url"            // legado (v1.0.x, un solo servidor)
    const val KEY_SERVERS = "servers"
    private const val KEY_SPEAKER = "speaker"
    private const val KEY_AGENT_NAME = "agent_name"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SERVERS_EXPANDED = "servers_expanded"
    const val DEFAULT_URL = "http://192.168.1.50:8765"
    const val DEFAULT_AGENT_NAME = "ZCode"

    fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** false = auricular (llamada normal), true = altavoz. */
    fun speaker(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SPEAKER, false)

    fun setSpeaker(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SPEAKER, value).apply()
    }

    fun agentName(ctx: Context): String =
        sp(ctx).getString(KEY_AGENT_NAME, DEFAULT_AGENT_NAME) ?: DEFAULT_AGENT_NAME

    fun setAgentName(ctx: Context, value: String) {
        val v = value.trim().ifEmpty { DEFAULT_AGENT_NAME }
        sp(ctx).edit().putString(KEY_AGENT_NAME, v).apply()
    }

    /** Etiqueta BCP-47 elegida por el usuario ("es", "en"…), o null si no eligio. */
    fun language(ctx: Context): String? = sp(ctx).getString(KEY_LANGUAGE, null)

    fun setLanguage(ctx: Context, tag: String) {
        sp(ctx).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** Busqueda automatica en la red cuando no hay ningun servidor conectado. */
    fun autoDiscovery(ctx: Context): Boolean = sp(ctx).getBoolean("auto_discovery", true)

    /** Estado plegado/desplegado de la lista de servidores en Inicio. */
    fun serversExpanded(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SERVERS_EXPANDED, true)

    fun setServersExpanded(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SERVERS_EXPANDED, value).apply()
    }
}
