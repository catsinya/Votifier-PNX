package io.catsinya.votifierpnx

import cn.nukkit.event.EventHandler
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerJoinEvent
import cn.nukkit.plugin.PluginBase
import java.io.File

class VotifierPlugin : PluginBase(), Listener {
    private lateinit var voteStore: VoteStore
    private lateinit var voteReceiver: VoteReceiver

    override fun onLoad() {
        logger.info("Votifier-PNX loading...")
    }

    override fun onEnable() {
        dataFolder.mkdirs()
        saveDefaultConfig()

        voteStore = VoteStore(File(dataFolder, "votes.yml"))
        voteStore.load()

        voteReceiver = VoteReceiver(
            plugin = this,
            port = config.getInt("port", 8192)
        )
        voteReceiver.start()

        server.pluginManager.registerEvents(this, this)
        server.commandMap.register("votifier-pnx", VoteCommand(this))

        logger.info("Votifier-PNX enabled on port ${voteReceiver.port}")
    }

    override fun onDisable() {
        if (this::voteReceiver.isInitialized) {
            voteReceiver.stop()
        }
        if (this::voteStore.isInitialized) {
            voteStore.save()
        }
        logger.info("Votifier-PNX disabled")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        deliverPendingVotes(event.player.name)
    }

    fun showVoteLinks(sender: cn.nukkit.command.CommandSender) {
        val header = color(config.getString("messages.vote-command-header", "&6=== &e&lVote for the Server &6==="))
        val linksLine = color(config.getString("messages.vote-command-links", "&eVote links:"))
        sender.sendMessage(header)
        sender.sendMessage("")
        sender.sendMessage(linksLine)

        for (link in config.getMapList("vote-links")) {
            val name = link["name"]?.toString().orEmpty()
            val url = link["url"]?.toString().orEmpty()
            if (name.isNotBlank() && url.isNotBlank()) {
                val line = config.getString("messages.vote-command-line", "  - %name%: %url%")
                    .replace("%name%", name)
                    .replace("%url%", url)
                sender.sendMessage(color(line))
            }
        }
    }

    fun receiveVote(serviceName: String, username: String) {
        logger.info("Vote received from $serviceName for $username")

        val player = server.getPlayer(username)
        if (player != null && player.isOnline) {
            server.scheduler.scheduleDelayedTask(this, Runnable {
                if (player.isOnline) {
                    rewardPlayer(player.name, serviceName)
                    player.sendMessage(color(config.getString("messages.vote-received", "&6Thanks for voting on &e%service%&6!")
                        .replace("%service%", serviceName)))
                }
            }, 1)
            return
        }

        voteStore.addPendingVote(username, serviceName)
        voteStore.save()
    }

    private fun deliverPendingVotes(username: String) {
        val pending = voteStore.removePendingVotes(username) ?: return
        server.scheduler.scheduleDelayedTask(this, Runnable {
            val player = server.getPlayer(username) ?: return@Runnable
            if (!player.isOnline) return@Runnable

            repeat(pending.count) {
                rewardPlayer(player.name, pending.serviceName)
            }
            player.sendMessage(color(config.getString("messages.vote-pending-delivered", "&6Your pending vote reward has been delivered.")))
            player.sendMessage(
                color(
                    config.getString("messages.vote-received", "&6Thanks for voting on &e%service%&6!")
                        .replace("%service%", pending.serviceName)
                )
            )
            voteStore.save()
        }, 20)
    }

    private fun rewardPlayer(playerName: String, serviceName: String) {
        val commands = config.getStringList("reward-commands")

        if (commands.isEmpty()) {
            logger.info("No reward-commands configured for vote from $serviceName")
            return
        }

        for (command in commands) {
            val resolved = command
                .replace("%player%", playerName)
                .replace("%service%", serviceName)
            try {
                server.executeCommand(server.consoleSender, resolved)
            } catch (e: Exception) {
                logger.warning("Failed to execute reward command '$resolved': ${e.message}")
            }
        }
    }

    private fun color(message: String): String {
        return message.replace('&', '§')
    }
}
