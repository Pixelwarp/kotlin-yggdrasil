package dev.vexor.ky

import java.util.UUID

val defaultHost = "https://authserver.mojang.com"

data class ClientModuleOptions(val host: String? = null, val agent: Any? = null)

class KYClient(private val moduleOptions: ClientModuleOptions) {
    private val host: String = moduleOptions.host ?: defaultHost
    private val agent: Any? = moduleOptions.agent

    /**
     * Attempts to authenticate a user.
     */
    suspend fun auth(options: Map<String, Any?>): Any? {
        val mutableOptions = options.toMutableMap()
        val token = mutableOptions["token"] as String?
        mutableOptions["token"] = token ?: UUID.randomUUID().toString()
        mutableOptions["agent"] = mutableOptions["agent"] ?: "Minecraft"

        return call(
            host,
            "authenticate",
            mapOf(
                "agent" to mapOf(
                    "name" to mutableOptions["agent"] as String,
                    "version" to if (mutableOptions["agent"] == "Minecraft") 1 else mutableOptions["version"]
                ),
                "username" to mutableOptions["user"],
                "password" to mutableOptions["pass"],
                "clientToken" to mutableOptions["token"],
                "requestUser" to (mutableOptions["requestUser"] == true)
            ),
            agent
        )
    }

    /**
     * Refreshes an accessToken.
     */
    suspend fun refresh(accessToken: String, clientToken: String, requestUser: Boolean = false): Pair<String, Any?> {
        val data = call(
            host,
            "refresh",
            mapOf("accessToken" to accessToken, "clientToken" to clientToken, "requestUser" to requestUser),
            agent
        )
        val dataMap = data as Map<String, Any?>
        if (dataMap["clientToken"] != clientToken) throw Exception("clientToken assertion failed")
        return Pair(dataMap["accessToken"] as String, data)
    }

    /**
     * Validates an access token
     */
    suspend fun validate(accessToken: String): Any? {
        return call(host, "validate", mapOf("accessToken" to accessToken), agent)
    }

    /**
     * Invalidates all access tokens.
     */
    suspend fun signout(username: String, password: String): Any? {
        return call(host, "signout", mapOf("username" to username, "password" to password), agent)
    }

    /**
     * Invalidates all access tokens using client/access token pair.
     */
    suspend fun invalidate(accessToken: String, clientToken: String): Any? {
        return call(host, "invalidate", mapOf("accessToken" to accessToken, "clientToken" to clientToken), agent)
    }

    val authCallback = callbackify({ args -> auth(args[0] as Map<String, Any?>) }, 1)
    val refreshCallback = callbackify({ args -> refresh(args[0] as String, args[1] as String, args[2] as Boolean) }, 3)
    val signoutCallback = callbackify({ args -> signout(args[0] as String, args[1] as String) }, 1)
    val validateCallback = callbackify({ args -> validate(args[0] as String) }, 2)
    val invalidateCallback = callbackify({ args -> invalidate(args[0] as String, args[1] as String) }, 2)
}