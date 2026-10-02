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

/**
 * Descubre el servidor Interfon en la red local: escanea el /24 de cada
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

    /** True si en host:8765 hay un servidor Interfon; devuelve su URL base. */
    private fun esInterfon(host: String): String? = runCatching {
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
        if (body.contains("\"interfon\"")) url else null
    }.getOrNull()

    /**
     * Escanea la red y devuelve la URL del servidor (o null si no aparece).
     * Recorre primero el /24 propio del telefono y luego las subredes comunes,
     * deteniendose en cuanto encuentra un Interfon.
     */
    suspend fun discover(timeoutMs: Long = 45000): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            val orden = LinkedHashSet(subredes() + SUBREDES_COMUNES)
            for (sub in orden) {
                val found = coroutineScope {
                    (1..254).map { i -> async { esInterfon("$sub.$i") } }
                        .awaitAll().firstOrNull { it != null }
                }
                if (found != null) return@withTimeoutOrNull found
            }
            null
        }
    }
}
