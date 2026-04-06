package io.catsinya.votifierpnx

import cn.nukkit.utils.Config

class VoteConfig(private val config: Config) {
    val port: Int
        get() = config.getInt(Keys.PORT, Defaults.PORT)

    fun voteLinks(): List<VoteLink> {
        return config.getMapList(Keys.VOTE_LINKS)
            .mapNotNull { entry ->
                val name = entry["name"]?.toString()?.trim().orEmpty()
                val url = entry["url"]?.toString()?.trim().orEmpty()
                if (name.isBlank() || url.isBlank()) {
                    null
                } else {
                    VoteLink(name, url)
                }
            }
    }

    fun rewardCommands(): List<String> = config.getStringList(Keys.REWARD_COMMANDS)

    fun message(key: String, defaultValue: String): String = config.getString(key, defaultValue)

    fun color(message: String): String = message.replace('&', '§')

    data class VoteLink(val name: String, val url: String)

    private object Keys {
        const val PORT = "port"
        const val VOTE_LINKS = "vote-links"
        const val REWARD_COMMANDS = "reward-commands"
    }

    private object Defaults {
        const val PORT = 8192
    }
}
