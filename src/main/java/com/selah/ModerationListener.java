package com.selah;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ModerationListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots, webhooks, and DMs
        if (event.getAuthor().isBot() || event.isWebhookMessage() || !event.isFromType(ChannelType.TEXT)) {
            return;
        }

        String serverId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        String channelName = event.getChannel().getName();
        String memberId = event.getAuthor().getId();
        String memberName = event.getAuthor().getName();

        // 1. Calculate the heat of the message
        double heatIndex = getHeatIndex(event);

        // 2. Update the channel's running statistics
        StatsManager.updateChannelStats(serverId, channelId, channelName, heatIndex);
        
        // 3. Update the member's running statistics
        StatsManager.updateMemberHeatLevel(serverId, memberId, memberName, heatIndex);
    }

    /**
     * A placeholder for a more complex toxicity detection algorithm.
     * For now, it will just score based on a few keywords and all-caps usage.
     * @param event The message event containing all context.
     * @return A score from 0.0 to 1.0.
     */
    private double getHeatIndex(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        int keywordMatches = 0;

        if (App.DEBUG_MODE) {
            String author = event.getAuthor().getName();
            String channel = event.getChannel().getName();
            System.out.println("\n[DEBUG] --- " + author + " wrote in #" + channel + " ---");
            System.out.println("[DEBUG] Message: \"" + message + "\"");

            // Check for reply
            if (event.getMessage().getReferencedMessage() != null) {
                System.out.println("[DEBUG] In reply to: \"" + event.getMessage().getReferencedMessage().getContentRaw() + "\"");
            }
        }

        double heat = 0.0;

        // Simple keyword check
        String[] keywords = {"hate", "stupid", "idiot"};
        for (String keyword : keywords) {
            if (message.toLowerCase().contains(keyword)) {
                keywordMatches++;
            }
        }

        if (keywordMatches > 0) {
            heat += (0.2 * keywordMatches); // Add 0.2 for each keyword
            if (App.DEBUG_MODE) System.out.println("[DEBUG] " + keywordMatches + " keyword match(es) found (+" + String.format("%.2f", 0.2 * keywordMatches) + ")");
        }


        // Check for excessive capitalization
        long upperCaseChars = message.chars().filter(Character::isUpperCase).count();
        double upperCaseRatio = message.isEmpty() ? 0 : (double) upperCaseChars / message.length();
        if (message.length() > 10 && upperCaseRatio > 0.7) {
            heat += 0.3;
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Excessive caps detected (" + String.format("%.2f", upperCaseRatio * 100) + "%) (+0.3)");
        } else if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Capitalization: " + String.format("%.2f", upperCaseRatio * 100) + "%");
        }
        
        // Check for excessive message length
        if (message.length() > 500) {
            heat += 0.2;
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Excessive length (" + message.length() + " chars) (+0.2)");
        }

        double finalHeat = Math.min(1.0, heat); // Cap the heat at 1.0

        if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Final Heat Index: " + finalHeat);
            System.out.println("[DEBUG] -------------------------------\n");
        }

        return finalHeat;
    }
}
