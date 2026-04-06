package io.catsinya.votifierpnx

import cn.nukkit.Player
import cn.nukkit.command.CommandSender
import cn.nukkit.event.EventHandler
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerJoinEvent
import cn.nukkit.plugin.PluginBase
import java.io.File

class VotifierPlugin : PluginBase(), Listener {
    private lateinit var voteStore: VoteStore
    private lateinit var voteReceiver: VoteReceiver
    private val voteHeader = VoteTemplate("&6=== &e&lVote for the Server &6===")
    private val voteLinksLabel = VoteTemplate("&eVote links:")
    private val voteLinkLine = VoteTemplate("  - %name%: %url%")
    private val voteReceived = VoteTemplate("&6Thanks for voting on &e%service%&6!")
    private val pendingDelivered = VoteTemplate("&6Your pending vote reward has been delivered.")

    override fun onLoad() {
        logger.info("Votifier-PNX loading...")
    }

    override fun onEnable() {
        dataFolder.mkdirs()
        saveDefaultConfig()

        val voteConfig = VoteConfig(config)
        voteStore = VoteStore(File(dataFolder, "votes.yml"))
        voteStore.load()

        voteReceiver = VoteReceiver(
            plugin = this,
            port = voteConfig.port
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
        deliverPendingVote(event.player)
    }

    fun showVoteLinks(sender: CommandSender) {
        val voteConfig = VoteConfig(config)
        sendMessage(sender, "messages.vote-command-header", voteHeader.defaultValue)
        sender.sendMessage("")
        sendMessage(sender, "messages.vote-command-links", voteLinksLabel.defaultValue)

        for (link in voteConfig.voteLinks()) {
            val line = voteConfig.message("messages.vote-command-line", voteLinkLine.defaultValue)
                .replace("%name%", link.name)
                .replace("%url%", link.url)
            sender.sendMessage(color(line))
        }
    }

    fun receiveVote(serviceName: String, username: String) {
        logger.info("Vote received from $serviceName for $username")

        val player = server.getPlayer(username)
        if (player?.isOnline == true) {
            server.scheduler.scheduleDelayedTask(this, Runnable {
                if (player.isOnline) {
                    rewardPlayer(player.name, serviceName)
                    sendVoteReceivedMessage(player, serviceName)
                }
            }, 1)
            return
        }

        voteStore.addPendingVote(username, serviceName)
        voteStore.save()
    }

    private fun deliverPendingVote(player: Player) {
        val pending = voteStore.getPendingVote(player.name) ?: return
        server.scheduler.scheduleDelayedTask(this, Runnable {
            val currentPlayer = server.getPlayer(player.name) ?: return@Runnable
            if (!currentPlayer.isOnline) return@Runnable

            repeat(pending.count) {
                rewardPlayer(currentPlayer.name, pending.serviceName)
            }
            sendMessage(currentPlayer, "messages.vote-pending-delivered", pendingDelivered.defaultValue)
            sendVoteReceivedMessage(currentPlayer, pending.serviceName)
            voteStore.removePendingVote(currentPlayer.name)
            voteStore.save()
        }, 20)
    }

    private fun rewardPlayer(playerName: String, serviceName: String) {
        val commands = VoteConfig(config).rewardCommands()

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

    private fun sendVoteReceivedMessage(player: Player, serviceName: String) {
        val message = VoteConfig(config).message("messages.vote-received", voteReceived.defaultValue)
            .replace("%service%", serviceName)
        player.sendMessage(color(message))
    }

    private fun sendMessage(sender: CommandSender, key: String, defaultValue: String) {
        val voteConfig = VoteConfig(config)
        sender.sendMessage(color(voteConfig.message(key, defaultValue)))
    }

    private fun color(message: String): String {
        return message.replace('&', '§')
    }

    private data class VoteTemplate(val defaultValue: String)

    private companion object {
    }
}
