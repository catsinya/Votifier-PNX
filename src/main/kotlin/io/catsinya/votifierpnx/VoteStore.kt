package io.catsinya.votifierpnx

import cn.nukkit.utils.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PendingVote(
    var username: String,
    var serviceName: String,
    var count: Int,
    var lastVoteAt: Long
)

class VoteStore(private val file: File) {
    private val pendingVotes = ConcurrentHashMap<String, PendingVote>()

    fun load() {
        if (!file.exists()) {
            return
        }

        val config = Config(file, Config.YAML)
        val section = config.getSection("pending") ?: return

        for (key in section.keys) {
            val voteSection = section.getSection(key) ?: continue
            val username = voteSection.getString("username", key)
            pendingVotes[key.lowercase()] = PendingVote(
                username = username,
                serviceName = voteSection.getString("service", "unknown"),
                count = voteSection.getInt("count", 1).coerceAtLeast(1),
                lastVoteAt = voteSection.getLong("last-vote-at", 0L)
            )
        }
    }

    fun save() {
        file.parentFile?.mkdirs()
        val config = Config(file, Config.YAML)
        config.set("pending", null)

        for ((key, vote) in pendingVotes) {
            config.set("pending.$key.username", vote.username)
            config.set("pending.$key.service", vote.serviceName)
            config.set("pending.$key.count", vote.count)
            config.set("pending.$key.last-vote-at", vote.lastVoteAt)
        }

        config.save()
    }

    fun addPendingVote(username: String, serviceName: String) {
        val key = username.lowercase()
        pendingVotes.compute(key) { _, existing ->
            if (existing == null) {
                PendingVote(username, serviceName, 1, System.currentTimeMillis())
            } else {
                existing.username = username
                existing.serviceName = serviceName
                existing.count += 1
                existing.lastVoteAt = System.currentTimeMillis()
                existing
            }
        }
    }

    fun removePendingVotes(username: String): PendingVote? {
        return pendingVotes.remove(username.lowercase())
    }
}
