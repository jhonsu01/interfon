package com.jhonsu.interfon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import org.json.JSONObject

/**
 * Descubre los servidores Interfon de la red local: escanea el /24 de cada
 * interfaz WiFi/Ethernet del telefono buscando el puerto 8765 y valida que
 * responda como Interfon via /api/status. Sin dependencias ni configuracion.
 */
object ServerDiscovery {

    const val PORT = 8765
    private const val CONNECT_TIMEOUT_MS = 500
    private const val HTTP_TIMEOUT_MS = 1000

    /**
     * Subredes /24 domesticas tipicas, ademas de las propias del telefono.
     * Cubre routers que reparten rangos distintos por interfaz (LAN vs WiFi).
     */
    private val SUBREDES_COMUNES = listOf(
        "192.168.0", "192.168.1", "192.168.2", "192.168.8", "192.168.10",
        "192.168.18", "192.168.31", "192.168.43", "192.168.77", "192.168.100",
        "192.168.137", "192.168.178", "10.0.0", "10.0.1", "172.16.0",
    )

    /** Prefijos /24 de las interfaces activas del telefono. */
    fun subredes(): List<String> {
        val out = LinkedHashSet<String>()
        runCatching {
            val en = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (en.hasMoreElements()) {
                val ni = en.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address ?: continue
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        val b = a.address
                        out.add("${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}")
                    }
                }
            }
        }
        return out.toList()
    }

    /** Servidor encontrado en la red: URL base y nombre del agente que anuncia. */
    data class Found(val url: String, val agent: String?)

    /** Si en host:8765 hay un servidor Interfon, devuelve su URL base y agente. */
    private fun esInterfon(host: String): Found? = runCatching {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)
        } finally {
            s.close()
        }
        val url = "http://$host:$PORT"
        val conn = URL("$url/api/status").openConnection() as HttpURLConnection
        conn.connectTimeout = HTTP_TIMEOUT_MS
        conn.readTimeout = HTTP_TIMEOUT_MS
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        if (json.optString("server") == "interfon")
            Found(url, json.optString("agent").takeIf { it.isNotBlank() })
        else null
    }.getOrNull()

    /**
     * Escanea la red y devuelve TODOS los servidores Interfon encontrados.
     * Recorre primero los /24 propios del telefono; solo si ahi no hay ninguno
     * prueba las subredes comunes, deteniendose en la primera que tenga alguno.
     */
    suspend fun discoverAll(timeoutMs: Long = 45000): List<Found> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Found>()
        withTimeoutOrNull(timeoutMs) {
            val propias = subredes()
            for ((i, sub) in (propias + SUBREDES_COMUNES.filterNot { it in propias }).withIndex()) {
                val found = coroutineScope {
                    (1..254).map { n -> async { esInterfon("$sub.$n") } }.awaitAll().filterNotNull()
                }
                found.forEach { out[it.url] = it }
                if (out.isNotEmpty() && i >= propias.size - 1) break
            }
        }
        out.values.toList()
    }
}
