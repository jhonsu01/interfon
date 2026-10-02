package com.jhonsu.interfon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Un servidor Interfon configurado. El orden de la lista es la prioridad. */
data class ServerEntry(val id: String, val name: String, val url: String) {
    val host: String
        get() = url.substringAfter("://").substringBefore("/")
}

/**
 * Lista persistente de servidores. La app se conecta a todos a la vez; el primero
 * conectado (en orden de la lista) atiende las llamadas salientes y el walkie.
 */
object Servers {

    @Synchronized
    fun load(ctx: Context): List<ServerEntry> {
        val sp = Prefs.sp(ctx)
        val raw = sp.getString(Prefs.KEY_SERVERS, null)
        if (raw == null) {
            // Migracion desde v1.0.x: la URL unica pasa a ser el primer servidor
            val legacy = sp.getString(Prefs.KEY_URL, null) ?: Prefs.DEFAULT_URL
            val first = ServerEntry(newId(), hostOf(legacy), normalize(legacy) ?: Prefs.DEFAULT_URL)
            save(ctx, listOf(first))
            return listOf(first)
        }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ServerEntry(o.getString("id"), o.getString("name"), o.getString("url"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(ctx: Context, list: List<ServerEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        Prefs.sp(ctx).edit().putString(Prefs.KEY_SERVERS, arr.toString()).apply()
        Bus.servers.value = list
    }

    enum class Result { OK, DUPLICATE, INVALID }

    @Synchronized
    fun add(ctx: Context, name: String, url: String): Result {
        val u = normalize(url) ?: return Result.INVALID
        val list = load(ctx)
        if (list.any { it.url.equals(u, ignoreCase = true) }) return Result.DUPLICATE
        save(ctx, list + ServerEntry(newId(), name.trim().ifEmpty { hostOf(u) }, u))
        return Result.OK
    }

    @Synchronized
    fun update(ctx: Context, id: String, name: String, url: String): Result {
        val u = normalize(url) ?: return Result.INVALID
        val list = load(ctx)
        if (list.any { it.id != id && it.url.equals(u, ignoreCase = true) }) return Result.DUPLICATE
        save(ctx, list.map {
            if (it.id == id) it.copy(name = name.trim().ifEmpty { hostOf(u) }, url = u) else it
        })
        return Result.OK
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    /** Sube (delta=-1) o baja (delta=+1) la prioridad de un servidor. */
    @Synchronized
    fun move(ctx: Context, id: String, delta: Int) {
        val list = load(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list.add(j, list.removeAt(i))
        save(ctx, list)
    }

    /** Agrega los servidores descubiertos que no esten ya en la lista. Devuelve cuantos. */
    @Synchronized
    fun addDiscovered(ctx: Context, found: List<ServerDiscovery.Found>): Int {
        val list = load(ctx)
        val nuevos = found.filter { f -> list.none { it.url.equals(f.url, ignoreCase = true) } }
            .map { f ->
                val host = hostOf(f.url)
                ServerEntry(newId(), f.agent?.let { "$it · $host" } ?: host, f.url)
            }
        if (nuevos.isNotEmpty()) save(ctx, list + nuevos)
        return nuevos.size
    }

    /** Acepta "192.168.1.50", "192.168.1.50:8765" o URLs completas. */
    fun normalize(input: String): String? {
        var v = input.trim().removeSuffix("/")
        if (v.isEmpty()) return null
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://$v"
        val hostPort = v.substringAfter("://").substringBefore("/")
        if (hostPort.isEmpty() || hostPort.contains(' ')) return null
        if (!hostPort.contains(':')) v = v.replaceFirst(hostPort, "$hostPort:${ServerDiscovery.PORT}")
        return v
    }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore(":").substringBefore("/")

    private fun newId() = UUID.randomUUID().toString().take(8)
}
