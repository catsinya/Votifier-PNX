package io.catsinya.votifierpnx

import cn.nukkit.Player
import cn.nukkit.command.CommandSender
import cn.nukkit.plugin.PluginBase

class VoteService(
    private val plugin: PluginBase,
    private val voteConfig: VoteConfig,
    private val voteStore: VoteStore
) {
    fun showVoteLinks(sender: CommandSender) {
        sender.sendMessage(voteConfig.color(voteConfig.message(Messages.VOTE_HEADER, Messages.DEFAULT_HEADER)))
        sender.sendMessage("")
        sender.sendMessage(voteConfig.color(voteConfig.message(Messages.VOTE_LINKS_LABEL, Messages.DEFAULT_LINKS_LABEL)))

        for (link in voteConfig.voteLinks()) {
            val line = voteConfig.message(Messages.VOTE_LINK_LINE, Messages.DEFAULT_LINK_LINE)
                .replace("%name%", link.name)
                .replace("%url%", link.url)
            sender.sendMessage(voteConfig.color(line))
        }
    }

    fun handleVote(serviceName: String, username: String) {
        plugin.logger.info("Vote received from $serviceName for $username")

        val player = plugin.server.getPlayer(username)
        if (player?.isOnline == true) {
            plugin.server.scheduler.scheduleDelayedTask(plugin, Runnable {
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

    fun deliverPendingVote(player: Player) {
        val pending = voteStore.getPendingVote(player.name) ?: return
        plugin.server.scheduler.scheduleDelayedTask(plugin, Runnable {
            val currentPlayer = plugin.server.getPlayer(player.name) ?: return@Runnable
            if (!currentPlayer.isOnline) return@Runnable

            repeat(pending.count) {
                rewardPlayer(currentPlayer.name, pending.serviceName)
            }
            sendMessage(currentPlayer, Messages.PENDING_DELIVERED, Messages.DEFAULT_PENDING_DELIVERED)
            sendVoteReceivedMessage(currentPlayer, pending.serviceName)
            voteStore.removePendingVote(currentPlayer.name)
            voteStore.save()
        }, 20)
    }

    private fun rewardPlayer(playerName: String, serviceName: String) {
        val commands = voteConfig.rewardCommands()

        if (commands.isEmpty()) {
            plugin.logger.info("No reward-commands configured for vote from $serviceName")
            return
        }

        for (command in commands) {
            val resolved = command
                .replace("%player%", playerName)
                .replace("%service%", serviceName)
            try {
                plugin.server.executeCommand(plugin.server.consoleSender, resolved)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to execute reward command '$resolved': ${e.message}")
            }
        }
    }

    private fun sendVoteReceivedMessage(player: Player, serviceName: String) {
        val message = voteConfig.message(Messages.VOTE_RECEIVED, Messages.DEFAULT_RECEIVED)
            .replace("%service%", serviceName)
        player.sendMessage(voteConfig.color(message))
    }

    private fun sendMessage(sender: CommandSender, key: String, defaultValue: String) {
        sender.sendMessage(voteConfig.color(voteConfig.message(key, defaultValue)))
    }

    private object Messages {
        const val VOTE_HEADER = "messages.vote-command-header"
        const val VOTE_LINKS_LABEL = "messages.vote-command-links"
        const val VOTE_LINK_LINE = "messages.vote-command-line"
        const val VOTE_RECEIVED = "messages.vote-received"
        const val PENDING_DELIVERED = "messages.vote-pending-delivered"

        const val DEFAULT_HEADER = "&6=== &e&lVote for the Server &6==="
        const val DEFAULT_LINKS_LABEL = "&eVote links:"
        const val DEFAULT_LINK_LINE = "  - %name%: %url%"
        const val DEFAULT_RECEIVED = "&6Thanks for voting on &e%service%&6!"
        const val DEFAULT_PENDING_DELIVERED = "&6Your pending vote reward has been delivered."
    }
}
