package io.catsinya.votifierpnx

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class VoteReceiver(
    private val plugin: VotifierPlugin,
    val port: Int
) {
    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var connectionPool: ExecutorService? = null
    private var token: String = ""

    fun start() {
        token = loadOrCreateToken()
        serverSocket = openServerSocket()
        connectionPool = createConnectionPool()
        running.set(true)
        plugin.logger.info("Vote receiver started on port $port")

        Thread({
            acceptConnections()
        }, "Votifier-PNX-Acceptor").apply {
            isDaemon = true
            start()
        }
    }

    private fun acceptConnections() {
        while (running.get()) {
            try {
                val socket = serverSocket?.accept() ?: break
                connectionPool?.submit {
                    handleConnection(socket)
                }
            } catch (e: Exception) {
                if (running.get()) {
                    plugin.logger.warning("Vote receiver accept error: ${e.message}")
                }
            }
        }
    }

    private fun openServerSocket(): ServerSocket {
        return ServerSocket(port)
    }

    private fun createConnectionPool(): ExecutorService {
        return Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "Votifier-PNX-Worker").apply {
                isDaemon = true
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        try {
            connectionPool?.shutdownNow()
        } catch (_: Exception) {
        }
    }

    private fun loadOrCreateToken(): String {
        val tokenFile = java.io.File(plugin.dataFolder, "votifier_token.txt")
        if (tokenFile.exists()) {
            val existing = tokenFile.readText().trim()
            if (existing.isNotBlank()) {
                return existing
            }
        }

        val generated = UUID.randomUUID().toString().replace("-", "")
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeText(generated)
        return generated
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val out = socket.getOutputStream()
            val input = socket.getInputStream()
            val challenge = UUID.randomUUID().toString().replace("-", "")

            out.write("VOTIFIER 2 $challenge\n".toByteArray(StandardCharsets.UTF_8))
            out.flush()

            if (!tryHandleV2Vote(input, out, challenge)) {
                sendV2Error(out, "protocol", "Unsupported or invalid Votifier payload")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Vote receiver connection error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun tryHandleV2Vote(input: java.io.InputStream, out: java.io.OutputStream, challenge: String): Boolean {
        val dataIn = DataInputStream(input)
        val magic = try {
            dataIn.readUnsignedShort()
        } catch (_: Exception) {
            return false
        }

        if (magic != V2_MAGIC) {
            return false
        }

        val length = dataIn.readUnsignedShort()
        if (length <= 0) {
            sendV2Error(out, "protocol", "Empty Votifier v2 payload")
            return true
        }

        val payloadBytes = ByteArray(length)
        dataIn.readFully(payloadBytes)

        try {
            val rawPayload = String(payloadBytes, StandardCharsets.UTF_8)
            val envelope = gson.fromJson(rawPayload, JsonObject::class.java)
            val payload = envelope.get("payload")?.asString ?: run {
                sendV2Error(out, "payload", "Missing payload")
                return true
            }
            val signature = envelope.get("signature")?.asString ?: run {
                sendV2Error(out, "payload", "Missing signature")
                return true
            }

            val expectedSignature = signPayload(payload)
            if (!MessageDigest.isEqual(
                    signature.toByteArray(StandardCharsets.UTF_8),
                    expectedSignature.toByteArray(StandardCharsets.UTF_8)
                )
            ) {
                sendV2Error(out, "signature", "Invalid signature")
                return true
            }

            val vote = gson.fromJson(payload, JsonObject::class.java)
            if ((vote.get("challenge")?.asString ?: "") != challenge) {
                sendV2Error(out, "challenge", "Challenge mismatch")
                return true
            }

            val serviceName = vote.get("serviceName")?.asString ?: "unknown"
            val username = vote.get("username")?.asString ?: ""
            if (username.isBlank()) {
                sendV2Error(out, "payload", "Missing username")
                return true
            }

            plugin.receiveVote(serviceName, username)
            out.write("""{"status":"ok"}""".toByteArray(StandardCharsets.UTF_8))
            out.flush()
            return true
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse Votifier v2 payload: ${e.message}")
            sendV2Error(out, "payload", "Invalid JSON payload")
            return true
        }
    }

    private fun signPayload(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun sendV2Error(out: java.io.OutputStream, cause: String, errorMessage: String) {
        out.write("""{"status":"error","cause":"$cause","errorMessage":"$errorMessage"}""".toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private companion object {
        private const val V2_MAGIC = 0x733A
    }
}
