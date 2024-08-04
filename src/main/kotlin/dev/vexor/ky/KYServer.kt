package dev.vexor.ky

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ServerModuleOptions(val host: String? = null, val agent: Any? = null)

fun createHash(data: ByteArray): String {
    val sha1 = MessageDigest.getInstance("SHA-1")
    val hashBytes = sha1.digest(data)
    return hashBytes.joinToString("") { "%02x".format(it) }
}

class KYServer(private val moduleOptions: ServerModuleOptions) {
    private val host: String = moduleOptions.host ?: defaultHost
    private val agent: Any? = moduleOptions.agent

    /**
     * Client's Mojang handshake call
     */
    suspend fun join(accessToken: String, selectedProfile: String, serverid: String, sharedsecret: String, serverkey: String): Any? {
        val serverIdHash = createHash((serverid + sharedsecret + serverkey).toByteArray())
        return call(
            host,
            "session/minecraft/join",
            mapOf(
                "accessToken" to accessToken,
                "selectedProfile" to selectedProfile,
                "serverId" to serverIdHash
            ),
            agent
        )
    }

    /**
     * Server's Mojang handshake call
     */
    suspend fun hasJoined(username: String, serverid: String, sharedsecret: String, serverkey: String): Any? {
        val hash = createHash((serverid + sharedsecret + serverkey).toByteArray())
        val url = "$host/session/minecraft/hasJoined?username=${java.net.URLEncoder.encode(username, "UTF-8")}&serverId=$hash"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (agent != null) {
            // Set agent or headers as necessary
        }

        connection.inputStream.use { inputStream ->
            val response = inputStream.bufferedReader().readText()
            val body = Json.decodeFromString<Map<String, Any>>(response)
            if (body["id"] != null) {
                return body
            } else {
                throw Exception("Failed to verify username!")
            }
        }
    }

    val joinCallback = callbackify({ args ->
        join(
            args[0] as String,
            args[1] as String,
            args[2] as String,
            args[3] as String,
            args[4] as String
        )
    }, 5)

    val hasJoinedCallback = callbackify({ args ->
        hasJoined(
            args[0] as String,
            args[1] as String,
            args[2] as String,
            args[3] as String
        )
    }, 4)
}