package dev.vexor.ky

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// Assume `version` is defined somewhere else
val version = "1.0.1"

val headers = mapOf(
    "User-Agent" to "kotlin-yggdrasil/$version",
    "Content-Type" to "application/json"
)

/**
 * Generic POST request
 */
suspend fun call(host: String, path: String, data: Any, agent: Any?): Any? = suspendCoroutine { cont ->
    try {
        val url = URL("$host/$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.doOutput = true

        val jsonInputString = Json.encodeToString(data)
        connection.outputStream.use { os ->
            val input = jsonInputString.toByteArray(StandardCharsets.UTF_8)
            os.write(input, 0, input.size)
        }

        connection.inputStream.use { inputStream ->
            val response = inputStream.bufferedReader().readText()
            if (response.isEmpty()) {
                cont.resume("")
                return@suspendCoroutine
            }
            try {
                val body = Json.decodeFromString<Map<String, Any>>(response)
                if (body.containsKey("error")) {
                    cont.resumeWithException(Exception(body["errorMessage"]?.toString() ?: body["error"]?.toString()))
                } else {
                    cont.resume(body)
                }
            } catch (e: Exception) {
                if (e is kotlinx.serialization.SerializationException) {
                    if (connection.responseCode == 403) {
                        if (response.contains("Request blocked.")) {
                            cont.resumeWithException(Exception("Request blocked by CloudFlare"))
                        } else if (response.contains("cf-error-code\">1009")) {
                            cont.resumeWithException(Exception("Your IP is banned by CloudFlare"))
                        }
                    } else {
                        cont.resumeWithException(Exception("Response is not JSON. Status code: ${connection.responseCode}"))
                    }
                } else {
                    cont.resumeWithException(e)
                }
            }
        }
    } catch (e: Exception) {
        cont.resumeWithException(e)
    }
}

/**
 * Java's stupid hashing method
 */
fun mcHexDigest(hash: ByteArray): String {
    var negative = hash[0].toInt() < 0
    if (negative) performTwosCompliment(hash)
    return (if (negative) "-" else "") + hash.joinToString("") { "%02x".format(it) }.replace("^0+".toRegex(), "")
}

/**
 * Java's annoying hashing method.
 * All credit to andrewrk
 * https://gist.github.com/andrewrk/4425843
 */
fun performTwosCompliment(buffer: ByteArray) {
    var carry = true
    for (i in buffer.indices.reversed()) {
        val value = buffer[i].toInt()
        val newByte = value.inv() and 0xff
        if (carry) {
            carry = newByte == 0xff
            buffer[i] = if (carry) 0 else (newByte + 1).toByte()
        } else {
            buffer[i] = newByte.toByte()
        }
    }
}

fun <T> callbackify(f: suspend (Array<Any?>) -> T, maxParams: Int): (Array<Any?>) -> Unit = { args ->
    var cb: ((Any?) -> Unit)? = null
    for (i in args.indices.reversed()) {
        if (args[i] is Function<*>) {
            cb = args[i] as ((Any?) -> Unit)
            args[i] = null
            break
        }
    }
    runBlocking {
        try {
            val result = f(args)
            cb?.invoke(result)
        } catch (e: Exception) {
            cb?.invoke(e)
        }
    }
}


object Exported {
    val call: (Array<Any?>) -> Unit = callbackify({ args ->
        call(args[0] as String, args[1] as String, args[2] as Any, args[3] as Any?)
    }, 4)

    fun <T> callbackify(f: suspend (Array<Any?>) -> T, maxParams: Int): (Array<Any?>) -> Unit = callbackify(f, maxParams)

    val mcHexDigest: (ByteArray) -> String = ::mcHexDigest
}