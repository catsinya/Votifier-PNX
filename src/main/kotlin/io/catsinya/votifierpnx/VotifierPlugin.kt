package io.catsinya.votifierpnx

import cn.nukkit.event.EventHandler
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerJoinEvent
import cn.nukkit.plugin.PluginBase
import java.io.File

class VotifierPlugin : PluginBase(), Listener {
    private lateinit var voteStore: VoteStore
    private lateinit var voteReceiver: VoteReceiver
    private lateinit var voteConfig: VoteConfig
    private lateinit var voteService: VoteService

    override fun onLoad() {
        logger.info("Votifier-PNX loading...")
    }

    override fun onEnable() {
        dataFolder.mkdirs()
        saveDefaultConfig()

        voteConfig = VoteConfig(config)
        voteStore = VoteStore(File(dataFolder, "votes.yml"))
        voteStore.load()
        voteService = VoteService(this, voteConfig, voteStore)

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
        voteService.deliverPendingVote(event.player)
    }

    fun showVoteLinks(sender: cn.nukkit.command.CommandSender) {
        voteService.showVoteLinks(sender)
    }

    fun handleVote(serviceName: String, username: String) {
        voteService.handleVote(serviceName, username)
    }
}
