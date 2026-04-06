package io.catsinya.votifierpnx

import cn.nukkit.command.Command
import cn.nukkit.command.CommandSender
import cn.nukkit.Player

class VoteCommand(private val plugin: VotifierPlugin) : Command(
    "vote",
    "Show the available vote links",
    "/vote"
) {
    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return true
        }

        plugin.showVoteLinks(sender)
        return true
    }
}
