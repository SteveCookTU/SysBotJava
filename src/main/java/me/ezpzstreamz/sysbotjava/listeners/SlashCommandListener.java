package me.ezpzstreamz.sysbotjava.listeners;

import me.ezpzstreamz.sysbotjava.SysBotJava;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

public class SlashCommandListener implements EventListener {

    private final SysBotJava sbj;

    public SlashCommandListener(SysBotJava instance) {
        sbj = instance;
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof SlashCommandEvent e) {
            if (e.getName().equalsIgnoreCase("queue")) {
                OptionMapping option = e.getOption("join_leave");
                if (option != null && option.getAsString().equalsIgnoreCase("join")) {
                    if (sbj.queueContainsUser(e.getUser().getIdLong())) {
                        e.reply("You are already in the queue. Position: " +
                                sbj.getQueuePosition(e.getUser().getIdLong())).setEphemeral(true).queue();
                    } else {
                        e.getUser().openPrivateChannel().queue(c -> e.reply(
                                                "You have joined the queue. There are " + sbj.getQueueSize() +
                                                        " users in front of you.").setEphemeral(true)
                                        .queue(m -> sbj.addToQueue(e.getUser().getIdLong()),
                                                err -> e.reply("Unable to open DMs. Please enable private messages.")
                                                        .setEphemeral(true)
                                                        .queue()),
                                err -> e.reply("Unable to open DMs. Please enable private messages.").setEphemeral(true)
                                        .queue());
                    }
                } else if(option != null && option.getAsString().equalsIgnoreCase("leave")) {
                    if(sbj.queueContainsUser(e.getUser().getIdLong())) {
                        sbj.removeFromQueue(e.getUser().getIdLong());
                        e.reply("You have been removed from the queue.").setEphemeral(true).queue();
                    } else {
                        e.reply("You are not currently in the queue.").setEphemeral(true).queue();
                    }
                }
            }

        }
    }
}
