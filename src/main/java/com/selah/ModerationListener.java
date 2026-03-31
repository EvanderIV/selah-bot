package com.selah;

import java.util.ArrayList;
import java.util.List;

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
        try {
            double heatIndex = getHeatIndexFromEvent(event);

            // 2. Update the channel's running statistics
            StatsManager.updateChannelStats(serverId, channelId, channelName, heatIndex);
            
            // 3. Update the member's running statistics
            StatsManager.updateMemberHeatLevel(serverId, memberId, memberName, heatIndex);
        } catch (MessageEmptyException e) {
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Skipping heat calculation: " + e.getMessage());
            }
            return; // Skip processing if message is empty after URL stripping
        }
    }

    /**
     * A placeholder for a more complex toxicity detection algorithm.
     * For now, it will just score based on a few keywords and all-caps usage.
     * @param event The message event containing all context.
     * @return A score from 0.0 to 1.0.
     */
    private double getHeatIndexFromEvent(MessageReceivedEvent event) throws MessageEmptyException {
        String originalMessage = event.getMessage().getContentRaw();
        
        // Strip URLs from the message before analysis
        String messageForAnalysis = originalMessage.replaceAll("https?://\\S+", "").trim();

        // If the message is empty after stripping URLs, return 0 heat
        if (messageForAnalysis.isEmpty()) {
            throw new MessageEmptyException("Message is empty after URL stripping.");
        }

        if (App.DEBUG_MODE) {
            String author = event.getAuthor().getName();
            String channel = event.getChannel().getName();
            System.out.println("\n[DEBUG] --- Heat Index for " + author + " in #" + channel + " ---");
            System.out.println("[DEBUG] Original Message: \"" + originalMessage + "\"");
            if (!originalMessage.equals(messageForAnalysis)) {
                System.out.println("[DEBUG] Message for Analysis (URLs stripped): \"" + messageForAnalysis + "\"");
            }

            // Check for reply
            if (event.getMessage().getReferencedMessage() != null) {
                System.out.println("[DEBUG] In reply to: \"" + event.getMessage().getReferencedMessage().getContentRaw() + "\"");
            }
        }

        double heat = getHeatIndex(messageForAnalysis);

        if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Final Heat Index: " + String.format("%.3f", heat));
            System.out.println("[DEBUG] -------------------------------\n");
        }

        return heat;
    }

    public static double getHeatIndex(String message) {

        double heat = 0;
        heat += checkKeywords(message);
        heat += checkCapitalization(message);
        heat += checkMessageLength(message);
        heat += checkPunctuation(message);

        if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Final Heat Index: " + String.format("%.3f", heat));
            System.out.println("[DEBUG] -------------------------------\n");
        }

        return heat;
    }

    private static double checkKeywords(String message) {
        double heat = 0;
        List<Keyword> foundKeywords = new ArrayList<>();
        List<Keyword> foundSafeWords = new ArrayList<>();
        String normalizedMessage = normalizeForKeywordCheck(message);

        if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Normalized message for keyword check: \"" + normalizedMessage + "\"");
        }

        // Check for standard keywords
        for (Keyword keyword : KeywordManager.keywords) {
            // Use word boundaries to match whole words
            String pattern = "\\b" + keyword.word + "\\b";
            if (java.util.regex.Pattern.compile(pattern).matcher(normalizedMessage).find()) {
                foundKeywords.add(keyword);
                heat += keyword.heat;
            } else if (normalizedMessage.contains(keyword.word)) {
                // If it's part of another word, add a reduced heat
                foundKeywords.add(new Keyword(keyword.word + "*", 0.2));
                heat += 0.2;
            }
        }

        // Check for safe words and reduce heat
        for (Keyword safeWord : KeywordManager.safeWords) {
            if (normalizedMessage.contains(safeWord.word)) {
                foundSafeWords.add(safeWord);
                heat += safeWord.heat; // Add the negative heat value
            }
        }

        if (App.DEBUG_MODE) {
            if (!foundKeywords.isEmpty()) {
                System.out.print("[DEBUG] Keyword matches: ");
                double totalKeywordHeat = 0;
                for (int i = 0; i < foundKeywords.size(); i++) {
                    Keyword kw = foundKeywords.get(i);
                    totalKeywordHeat += kw.heat;
                    System.out.print(kw.word + " (+" + kw.heat + ")");
                    if (i < foundKeywords.size() - 1) {
                        System.out.print(", ");
                    }
                }
                System.out.println(" | Total: +" + String.format("%.2f", totalKeywordHeat));
            }

            if (!foundSafeWords.isEmpty()) {
                System.out.print("[DEBUG] Safe word matches: ");
                double totalSafeWordHeat = 0;
                for (int i = 0; i < foundSafeWords.size(); i++) {
                    Keyword sw = foundSafeWords.get(i);
                    totalSafeWordHeat += sw.heat;
                    System.out.print(sw.word + " (" + sw.heat + ")");
                    if (i < foundSafeWords.size() - 1) {
                        System.out.print(", ");
                    }
                }
                System.out.println(" | Total: " + String.format("%.2f", totalSafeWordHeat));
            }
        }
        
        // Ensure heat doesn't go below zero
        heat = Math.max(0, heat);

        return heat;
    }

    private static double checkCapitalization(String message) {
        long upperCaseChars = message.chars().filter(Character::isUpperCase).count();
        long totalLetters = message.chars().filter(Character::isLetter).count();
        double upperCaseRatio = totalLetters == 0 ? 0 : (double) upperCaseChars / totalLetters;

        if (message.length() > 10 && upperCaseRatio > 0.7) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Excessive caps detected (" + String.format("%.2f", upperCaseRatio * 100) + "%) (+0.3)");
            return 0.3;
        } else if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Capitalization: " + String.format("%.2f", upperCaseRatio * 100) + "%");
        }
        return 0;
    }

    private static double checkMessageLength(String message) {
        if (message.length() > 500) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Excessive length (" + message.length() + " chars) (+0.2)");
            return 0.2;
        }
        return 0;
    }

    private static double checkPunctuation(String message) {
        double heat = 0;
        
        // --- 1. Check for sentence-ending periods ---
        String[] sentences = message.split("[.?!]");
        if (sentences.length > 1) {
            long periodCount = message.chars().filter(ch -> ch == '.').count();
            long terminatorCount = message.chars().filter(ch -> ch == '.' || ch == '?' || ch == '!').count();

            if (terminatorCount > 1 && periodCount > 0) {
                double periodRatio = (double) periodCount / terminatorCount;
                if (periodRatio > 0.75) {
                    heat += 0.15;
                    if (App.DEBUG_MODE) System.out.println("[DEBUG] High sentence-period ratio (" + String.format("%.2f", periodRatio * 100) + "%) (+0.15)");
                }
            }
        }


        // --- 2. Count commas and apostrophes ---
        long commaCount = message.chars().filter(ch -> ch == ',').count();
        if (commaCount > 0) {
            double commaHeat = Math.min(0.1, commaCount * 0.01); // Cap the heat from commas
            heat += commaHeat;
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Found " + commaCount + " comma(s) (+" + String.format("%.3f", commaHeat) + ")");
        }

        long apostropheCount = message.chars().filter(ch -> ch == '\'').count();
        if (apostropheCount >= 3) {
            double apostropheHeat = Math.min(0.2, apostropheCount * 0.05); // Cap heat
            heat += apostropheHeat;
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Found " + apostropheCount + " apostrophe(s) (+" + String.format("%.3f", apostropheHeat) + ")");
        }


        // --- 3. Check for multiple exclamation or question marks ---
        if (message.matches(".*(\\?{2,}|!{2,}).*")) {
            heat += 0.1;
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Multiple punctuation marks detected (+0.1)");
        }

        return heat;
    }

    /**
     * Normalizes a message by replacing common leetspeak/character substitutions.
     * This allows for more robust keyword matching.
     * @param message The raw message content.
     * @return A normalized string.
     */
    private static String normalizeForKeywordCheck(String message) {
        return message.toLowerCase()
                .replace("'", "") // Remove apostrophes
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('6', 'g')
                .replace('7', 't')
                .replace('8', 'b')
                .replace('@', 'a')
                .replace('$', 's')
                .replace('!', 'i')
                .replace('*', 'a'); // Common wildcard replacement
    }
}
