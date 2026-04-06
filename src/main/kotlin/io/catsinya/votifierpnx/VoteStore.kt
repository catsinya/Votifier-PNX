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
            val username = voteSection.getString(Keys.USERNAME, key)
            pendingVotes[normalize(key)] = PendingVote(
                username = username,
                serviceName = voteSection.getString(Keys.SERVICE, Defaults.SERVICE),
                count = voteSection.getInt(Keys.COUNT, Defaults.COUNT).coerceAtLeast(1),
                lastVoteAt = voteSection.getLong(Keys.LAST_VOTE_AT, Defaults.LAST_VOTE_AT)
            )
        }
    }

    fun save() {
        file.parentFile?.mkdirs()
        val config = Config(file, Config.YAML)
        config.set("pending", null)

        for ((key, vote) in pendingVotes) {
            config.set(path(key, Keys.USERNAME), vote.username)
            config.set(path(key, Keys.SERVICE), vote.serviceName)
            config.set(path(key, Keys.COUNT), vote.count)
            config.set(path(key, Keys.LAST_VOTE_AT), vote.lastVoteAt)
        }

        config.save()
    }

    fun addPendingVote(username: String, serviceName: String) {
        val key = normalize(username)
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

    fun removePendingVote(username: String): PendingVote? {
        return pendingVotes.remove(normalize(username))
    }

    fun getPendingVote(username: String): PendingVote? {
        return pendingVotes[normalize(username)]
    }

    private fun normalize(value: String): String = value.lowercase()

    private fun path(key: String, child: String): String = "pending.$key.$child"

    private object Keys {
        const val USERNAME = "username"
        const val SERVICE = "service"
        const val COUNT = "count"
        const val LAST_VOTE_AT = "last-vote-at"
    }

    private object Defaults {
        const val SERVICE = "unknown"
        const val COUNT = 1
        const val LAST_VOTE_AT = 0L
    }
}
