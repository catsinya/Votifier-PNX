package io.catsinya.votifierpnx

import com.google.gson.Gson
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
        if (!isV2Packet(dataIn)) {
            return false
        }

        val rawPayload = readPayload(dataIn, out) ?: return true
        val envelope = parseEnvelope(rawPayload, out) ?: return true
        if (!isValidSignature(envelope)) {
            sendV2Error(out, "signature", "Invalid signature")
            return true
        }

        val payload = envelope.payload?.trim().orEmpty()
        val vote = parseVote(payload, out) ?: return true
        if (vote.challenge != challenge) {
            sendV2Error(out, "challenge", "Challenge mismatch")
            return true
        }

        val username = vote.username?.trim().orEmpty()
        if (username.isBlank()) {
            sendV2Error(out, "payload", "Missing username")
            return true
        }

        plugin.handleVote(vote.serviceName?.trim().orEmpty().ifBlank { "unknown" }, username)
        out.write("""{"status":"ok"}""".toByteArray(StandardCharsets.UTF_8))
        out.flush()
        return true
    }

    private fun isV2Packet(dataIn: DataInputStream): Boolean {
        val magic = try {
            dataIn.readUnsignedShort()
        } catch (_: Exception) {
            return false
        }
        return magic == V2_MAGIC
    }

    private fun readPayload(dataIn: DataInputStream, out: java.io.OutputStream): String? {
        val length = try {
            dataIn.readUnsignedShort()
        } catch (_: Exception) {
            sendV2Error(out, "protocol", "Missing payload length")
            return null
        }

        if (length <= 0) {
            sendV2Error(out, "protocol", "Empty Votifier v2 payload")
            return null
        }

        val payloadBytes = ByteArray(length)
        return try {
            dataIn.readFully(payloadBytes)
            String(payloadBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            sendV2Error(out, "protocol", "Incomplete Votifier v2 payload")
            null
        }
    }

    private fun parseEnvelope(payload: String, out: java.io.OutputStream): V2Envelope? {
        return try {
            gson.fromJson(payload, V2Envelope::class.java)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse Votifier v2 envelope: ${e.message}")
            sendV2Error(out, "payload", "Invalid JSON payload")
            null
        } ?: run {
            sendV2Error(out, "payload", "Invalid JSON payload")
            null
        }
    }

    private fun isValidSignature(envelope: V2Envelope): Boolean {
        val signature = envelope.signature?.trim().orEmpty()
        val payload = envelope.payload?.trim().orEmpty()
        if (signature.isBlank() || payload.isBlank()) {
            return false
        }

        val expectedSignature = signPayload(payload)
        return MessageDigest.isEqual(
            signature.toByteArray(StandardCharsets.UTF_8),
            expectedSignature.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun parseVote(payload: String, out: java.io.OutputStream): V2Vote? {
        return try {
            gson.fromJson(payload, V2Vote::class.java)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse Votifier v2 vote: ${e.message}")
            sendV2Error(out, "payload", "Invalid JSON payload")
            null
        } ?: run {
            sendV2Error(out, "payload", "Invalid JSON payload")
            null
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

    private data class V2Envelope(
        val payload: String? = null,
        val signature: String? = null
    )

    private data class V2Vote(
        val challenge: String? = null,
        val serviceName: String? = null,
        val username: String? = null
    )
}
