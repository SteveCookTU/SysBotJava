package me.ezpzstreamz.sysbotjava;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RegisterSlashCommands {

    public static void registerSlashCommands(JDA jda) {
        jda.upsertCommand(new CommandData("queue", "Command to join or leave the giveaway queue.").addOptions(
                new OptionData(OptionType.STRING, "join_leave", "Option to join or leave", true).addChoices(
                        new Command.Choice("join", "join"), new Command.Choice("leave", "leave")))).queue();
    }

}
