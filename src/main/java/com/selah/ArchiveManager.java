package com.selah;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ArchiveManager {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Fetches the entire message history for a given channel and saves it to a text file.
     * @param jda The JDA instance.
     * @param channelId The ID of the channel to archive.
     */
    public static void archiveChannel(JDA jda, String channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);

        if (channel == null) {
            System.err.println("ERROR: Channel with ID '" + channelId + "' not found.");
            return;
        }

        String guildId = channel.getGuild().getId();
        String channelName = channel.getName();
        String fileName = "archive_" + guildId + "_" + channelName + ".txt";

        System.out.println("Starting archive for channel #" + channelName + " in guild " + channel.getGuild().getName() + "...");
        System.out.println("Output file: " + fileName);
        System.out.println("Fetching messages... (JDA is handling pagination and rate limits natively)");

        try {
            // Asynchronously pull up to 100,000 messages and collect them into a List
            // .get() blocks the thread until the entire fetch operation completes
            List<Message> allMessages = channel.getIterableHistory()
                    .takeAsync(100000) 
                    .thenApply(list -> list.stream().collect(Collectors.toList()))
                    .get();

            if (allMessages.isEmpty()) {
                System.out.println("No messages found in channel.");
                return;
            }

            // JDA retrieves messages from newest to oldest. 
            // We reverse the list so the text file reads chronologically (top to bottom).
            Collections.reverse(allMessages);

            // Write the collected list to the file in one go
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                for (Message message : allMessages) {
                    String timestamp = message.getTimeCreated().format(formatter);
                    String author = message.getAuthor().getName();
                    String content = message.getContentRaw();

                    writer.write(String.format("[%s] %s: %s%n", timestamp, author, content));
                }
                System.out.println("Archive complete! Total messages saved: " + allMessages.size());
                return;
            } catch (IOException e) {
                System.err.println("CRITICAL: Failed to write to archive file '" + fileName + "'.");
                e.printStackTrace();
            }

        } catch (InterruptedException | ExecutionException e) {
            System.err.println("CRITICAL: Failed to fetch messages from Discord API.");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.out.println("WARN: Encountered message of unknown type. Skipping...");
        }
    }
}