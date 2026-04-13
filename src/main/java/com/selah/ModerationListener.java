package com.selah;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.Color;

public class ModerationListener extends ListenerAdapter {

    // Static map to define affection point ratings for specific patterns
    private static final Map<String, Double> AFFECTION_PATTERNS = new LinkedHashMap<>();
    
    // Static map to track the last time an alert was sent per channel
    // Key format: "serverId:channelId"
    private static final Map<String, Long> lastAlertTime = new HashMap<>();
    
    // Static map to track recent channel heat values for sustained heat detection
    // Key format: "serverId:channelId", Value is a LinkedHashMap of timestamps to heat values
    private static final Map<String, LinkedHashMap<Long, Double>> recentChannelHeats = new HashMap<>();
    private static final int MAX_HEAT_HISTORY = 10; // Track last 10 messages for sustained heat detection
    private static final double SUSTAINED_HEAT_THRESHOLD = 1.0; // Average must be at least this for sustained status

    static {
        AFFECTION_PATTERNS.put("Y[EA]S+", 0.15); // Case-sensitive match for "YES" followed by any number of 's'
        AFFECTION_PATTERNS.put("(?i)y[ea]sss+", 0.1); // Case-insensitive match for "yesss" followed by any number of 's'
        // Add more patterns and their affection ratings here if needed
    }

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

        // Check for replies and grant affection points if the content matches a pattern
        if (event.getMessage().getReferencedMessage() != null) {
            String replyContent = event.getMessage().getContentRaw();
            String originalAuthorId = event.getMessage().getReferencedMessage().getAuthor().getId();

            for (Map.Entry<String, Double> entry : AFFECTION_PATTERNS.entrySet()) {
                if (replyContent.matches(entry.getKey())) {
                    double affectionChange = Math.round(entry.getValue() * 100.0) / 100.0; // Round to nearest 0.01
                    StatsManager.updateUserRelation(serverId, memberId, originalAuthorId, affectionChange);

                    if (App.DEBUG_MODE) {
                        System.out.println("[DEBUG] Granted affection points for reply: " + replyContent + " | Points: " + affectionChange);
                    }
                    break; // Stop after the first match
                }
            }
        }

        // Check for banned words
        checkForBannedWords(event);

        // 1. Calculate the heat of the message
        try {
            double heatIndex = getHeatIndexFromEvent(event);

            // 2. Update the channel's running statistics
            StatsManager.updateChannelStats(serverId, channelId, channelName, heatIndex);
            
            // 3. Update the member's running statistics
            StatsManager.updateMemberHeatLevel(serverId, memberId, memberName, heatIndex);
            
            // 4. Check if the channel has abnormally high heat and send alerts if needed
            checkAndSendAlerts(event, heatIndex);
        } catch (MessageEmptyException e) {
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Skipping heat calculation: " + e.getMessage());
            }
            return; // Skip processing if message is empty after URL stripping
        }
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        // Ignore bots, webhooks, and DMs
        if (event.getAuthor().isBot() || !event.isFromType(ChannelType.TEXT)) {
            return;
        }

        if (App.DEBUG_MODE) {
            System.out.println("\n[DEBUG] --- Message Edit Detected ---");
            System.out.println("[DEBUG] User: " + event.getAuthor().getName());
            System.out.println("[DEBUG] Channel: #" + event.getChannel().getName());
            // The old message content is not available in the event, so we just log the new one.
            System.out.println("[DEBUG] New Message: \"" + event.getMessage().getContentRaw() + "\"");
        }

        String serverId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        String channelName = event.getChannel().getName();
        String memberId = event.getAuthor().getId();
        String memberName = event.getAuthor().getName();

        // Check for banned words
        checkForBannedWords(event);

        // 1. Calculate the heat of the edited message
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
        if (App.DEBUG_MODE) {
            System.out.println();
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bots and webhooks
        if (event.getUser().isBot()) {
            return;
        }

        String serverId = event.getGuild().getId();
        String reactorId = event.getUserId();
        String messageAuthorId = event.retrieveMessage().complete().getAuthor().getId();

        // Skip if the reactor is the same as the message author
        if (reactorId.equals(messageAuthorId)) {
            return;
        }
        String emoji = event.getReaction().getEmoji().getFormatted();
        
        if (App.DEBUG_MODE) {
            System.out.print("[DEBUG] Reaction added: " + event.getReaction().getEmoji().getFormatted() + " by " + event.getUser().getName() + " on message by " + event.retrieveMessage().complete().getAuthor().getName() + " | ");
        }
        double affectionChange = 0.01; // Default small boost

        switch (emoji) {
            case "❤️":
            case "💗":
            case "💚":
            case "💙":
            case "🩵":
            case "💜":
            case "🖤":
            case "💛":
            case "🤍":
            case "🤎":
            case "💕":
            case "💞":
            case "💓":
            case "💖":
            case "💝":
            case "💘":
            case "💟":
            case "❣️":
            case "💌":
                affectionChange = 0.12;
                break;
            case "👍":
                affectionChange = 0.03;
                break;
            case "💀":
                affectionChange = 0.0;
                break;
            case "☠️":
                affectionChange = 0.0;
                break;
            case "👎":
                affectionChange = -0.05;
                break;
            default:
                if (emoji.toLowerCase().contains("heart")) {
                    affectionChange = 0.12; // Default affection for any "heart" emoji
                }
                break;
        }

        System.out.println("Affection change: " + affectionChange);

        // Update the relations array
        StatsManager.updateUserRelation(serverId, reactorId, messageAuthorId, affectionChange);
    }

    private void checkForBannedWords(MessageReceivedEvent event) {
        String serverId = event.getGuild().getId();
        App.ServerNode config = App.guildConfigs.get(serverId);
        if (config == null || config.config.banned_words == null || config.config.banned_words.isEmpty()) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw();
        for (String bannedWord : config.config.banned_words) {
            String pattern = "\\b" + bannedWord + "\\b";
            if (java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(messageContent).find()) {
                PunishmentManager.invokePunishment(event.getAuthor(), event.getGuild(), "Used banned word: " + bannedWord);
                return; // Stop after first infraction
            }
        }
    }

    private void checkForBannedWords(MessageUpdateEvent event) {
        String serverId = event.getGuild().getId();
        App.ServerNode config = App.guildConfigs.get(serverId);
        if (config == null || config.config.banned_words == null || config.config.banned_words.isEmpty()) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw();
        for (String bannedWord : config.config.banned_words) {
            String pattern = "\\b" + bannedWord + "\\b";
            if (java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(messageContent).find()) {
                PunishmentManager.invokePunishment(event.getAuthor(), event.getGuild(), "Used banned word: " + bannedWord);
                return; // Stop after first infraction
            }
        }
    }

    /**
     * A placeholder for a more complex toxicity detection algorithm.
     * For now, it will just score based on a few keywords and all-caps usage.
     * @param event The message event containing all context.
     * @return A score of how likely the message is to be toxic.
     */
    private double getHeatIndexFromEvent(MessageReceivedEvent event) throws MessageEmptyException {
        String originalMessage = event.getMessage().getContentRaw();

        List<String> attachments = new ArrayList<>();

        // Add URLs from attachments
        event.getMessage().getAttachments().stream()
            .filter(Message.Attachment::isImage) // Only include image attachments
            .map(Message.Attachment::getUrl) // Get the URL of the attachment
            .forEach(attachments::add);

        

        // Log all attachments for debugging
        if (App.DEBUG_MODE) {
            event.getMessage().getAttachments().forEach(attachment -> {
                attachments.add(attachment.getUrl());
                System.out.println("[DEBUG] Attachment: " + attachment.getFileName() + " | URL: " + attachment.getUrl());
            });
        }

        // Use a Set to ensure URLs are unique
        Set<String> imageUrls = new HashSet<>();

        for (String url : attachments) {
            if (url.matches("^https://(cdn|media)\\.discordapp\\.(com|net)/attachments/.*\\.(jpg|jpeg|png|gif|bmp|webp|tiff)(\\?.*)?$")) {
                imageUrls.add(url);
                if (App.DEBUG_MODE) {
                    System.out.println("[DEBUG] Found image URL: " + url);
                }
            } else if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Ignored non-image URL: " + url);
            }
        }

        // Send image URLs to OCR handler
        for (String imageUrl : imageUrls) {
            try {
                String ocrResult = OCRProcessor.processImageFromUrl(imageUrl);
                if (App.DEBUG_MODE) {
                    System.out.println("[DEBUG] OCR Result for URL " + imageUrl + ":\n" + ocrResult.replace("\n", " "));
                }
            } catch (Exception e) {
                if (App.DEBUG_MODE) {
                    System.out.println("[DEBUG] Failed to process OCR for URL " + imageUrl + ": " + e.getMessage());
                }
            }
        }

        // Strip URLs from the message before analysis
        String messageForAnalysis = originalMessage.replaceAll("https?://\\S+", "").trim();

        // If the message is empty after stripping URLs and no image URLs were found, return 0 heat
        if (!originalMessage.isEmpty() && messageForAnalysis.isEmpty() && imageUrls.isEmpty()) {
            throw new MessageEmptyException("Message is empty after URL stripping.");
        } else if (originalMessage.isEmpty() && imageUrls.isEmpty()) {
            throw new MessageEmptyException("Message is empty and contains no image URLs.");
        } else if (originalMessage.isEmpty()) {
            throw new MessageEmptyException("Message is empty after URL stripping, but contains image URLs. Heat will be calculated based on OCR results only.");
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
        
        // Add heat bonus for reply chain depth
        int replyDepth = countReplyChainDepth(event.getMessage());
        double replyBonus = (replyDepth - 1) * 0.1;
        heat += replyBonus;
        if (App.DEBUG_MODE && replyBonus > 0) {
            System.out.println("[DEBUG] Reply chain depth: " + replyDepth + " (+" + String.format("%.1f", replyBonus) + ")");
        }
        
        return heat;
    }

    /**
     * Overloaded method for message edits, since the event type is different.
     * @param event The message update event containing all context.
     * @return A score of how likely the message is to be toxic.
     * @throws MessageEmptyException if the message is empty after URL stripping, which can happen if the edit removed all text or only left URLs.
     */
    private double getHeatIndexFromEvent(MessageUpdateEvent event) throws MessageEmptyException {
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
        
        // Add heat bonus for reply chain depth
        int replyDepth = countReplyChainDepth(event.getMessage());
        double replyBonus = (replyDepth - 1) * 0.1;
        heat += replyBonus;
        if (App.DEBUG_MODE && replyBonus > 0) {
            System.out.println("[DEBUG] Reply chain depth: " + replyDepth + " (+" + String.format("%.1f", replyBonus) + ")");
        }
        
        return heat;
    }

    /**
     * Counts the depth of the reply chain for a given message.
     * Direct messages have depth 0, first replies have depth 1, etc.
     * @param message The message to check for reply chain depth.
     * @return The depth of the reply chain.
     */
    private int countReplyChainDepth(Message message) {
        int depth = 0;
        Message current = message.getReferencedMessage();
        while (current != null) {
            depth++;
            current = current.getReferencedMessage();
        }
        return depth;
    }

    public static double getHeatIndex(String message) {

        double heat = 0;
        heat += checkKeywords(message);
        heat += checkCapitalization(message);
        heat += checkMessageLength(message);
        heat += checkPunctuation(message);
        heat += checkDebatePatterns(message);

        heat = Math.max(0, heat);

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

        heat = Math.max(0, heat);

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

        return heat;
    }

    private static double checkCapitalization(String message) {
        long upperCaseChars = message.chars().filter(Character::isUpperCase).count();
        long totalLetters = message.chars().filter(Character::isLetter).count();
        double upperCaseRatio = totalLetters == 0 ? 0 : (double) upperCaseChars / totalLetters;

        if (message.length() > 35 && upperCaseRatio > 0.06 && upperCaseRatio < 0.4) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Standard caps detected (" + String.format("%.2f", upperCaseRatio * 100) + "%) (+0.1)");
            return 0.1;
        } else if (message.length() > 10 && upperCaseRatio > 0.2 && upperCaseRatio < 0.4) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Standard caps detected on short string (" + String.format("%.2f", upperCaseRatio * 100) + "%) (+0.05)");
            return 0.05;
        } else if (upperCaseRatio > 0.7) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Casually excessive caps detected (" + String.format("%.2f", upperCaseRatio * 100) + "%) (-0.1)");
            return -0.1;
        } else if (upperCaseRatio <= 0.0) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Casually minimal caps detected (" + String.format("%.2f", upperCaseRatio * 100) + "%) (-0.1)");
            return -0.1;
        } else if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Capitalization: " + String.format("%.2f", upperCaseRatio * 100) + "%");
        }
        return 0;
    }

    private static double checkMessageLength(String message) {
        if (message.length() > 2000) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Excessive length (" + message.length() + " chars, extreme spam indicator) (+0.3)");
            return 0.3;
        } else if (message.length() > 500) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Very long message (" + message.length() + " chars, likely debate) (+0.05)");
            return 0.05; // Longer messages are usually debate/discussion, only slight boost
        } else if (message.length() > 300) {
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Long message (" + message.length() + " chars) (+0.02)");
            return 0.02;
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

        // --- 4. Count newlines and apply additive heat ---
        long newlineCount = message.chars().filter(ch -> ch == '\n').count();
        if (newlineCount > 0) {
            double newlineHeat = 0.0;
            for (int i = 0; i < newlineCount; i++) {
                newlineHeat += 0.1 + (i * 0.05);
            }
            heat += newlineHeat;
            if (App.DEBUG_MODE) System.out.println("[DEBUG] Found " + newlineCount + " newline(s) (total heat: +" + String.format("%.3f", newlineHeat) + ")");
        }

        return heat;
    }

    /**
     * Normalizes a message by replacing common leetspeak/character substitutions.
     * This allows for more robust keyword matching.
     * @param message The raw message content.
     * @return A normalized string.
     */
    /**
     * Detects debate-related patterns: question density, conditionals, citations, and structured responses.
     * Adds heat to messages that show debate characteristics.
     */
    private static double checkDebatePatterns(String message) {
        double heat = 0;
        
        // --- 1. Question density ---
        long questionCount = message.chars().filter(ch -> ch == '?').count();
        if (questionCount > 0) {
            // More questions = more debate-like
            double questionHeat = Math.min(0.2, questionCount * 0.08);
            heat += questionHeat;
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Detected " + questionCount + " question(s) (+" + String.format("%.3f", questionHeat) + ")");
            }
        }
        
        // --- 2. Conditional language ---
        String lowerMsg = message.toLowerCase();
        long conditionalCount = 0;
        String[] conditionals = {"if ", " then ", " would ", " could ", " should ", " might ", " may "};
        for (String conditional : conditionals) {
            conditionalCount += countOccurrences(lowerMsg, conditional);
        }
        
        if (conditionalCount > 0) {
            double conditionalHeat = Math.min(0.15, conditionalCount * 0.05);
            heat += conditionalHeat;
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Detected " + conditionalCount + " conditional phrase(s) (+" + String.format("%.3f", conditionalHeat) + ")");
            }
        }
        
        // --- 3. Long, structured response (multiple sentences) ---
        long sentenceCount = message.chars().filter(ch -> ch == '.' || ch == '?' || ch == '!').count();
        if (sentenceCount > 2 && message.length() > 80) {
            heat += 0.1;
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Multi-sentence structured response (" + sentenceCount + " sentences) (+0.1)");
            }
        }
        
        // --- 4. Citation/quote patterns ---
        long quoteCount = message.chars().filter(ch -> ch == '"').count();
        if (quoteCount >= 2) {
            heat += 0.1;
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Detected quotes/citations (+0.1)");
            }
        }
        
        return heat;
    }

    /**
     * Helper method to count occurrences of a substring in a string.
     */
    private static long countOccurrences(String text, String substring) {
        if (substring.isEmpty()) return 0;
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

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

    /**
     * Checks if a channel has abnormally high heat and sends an alert if configured.
     * Abnormally high heat is defined as average heat index > 0.8.
     * @param event The message event containing server and channel context.
     * @param messageHeatIndex The heat index of the current message.
     */
    private void checkAndSendAlerts(MessageReceivedEvent event, double messageHeatIndex) {
        String serverId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        String channelName = event.getChannel().getName();
        
        App.ServerNode serverConfig = App.guildConfigs.get(serverId);
        if (serverConfig == null || serverConfig.config.alert_channel_id == null) {
            return; // No alert channel configured
        }
        
        StatsManager.ServerStats serverStats = StatsManager.liveStats.get(serverId);
        if (serverStats == null) {
            return;
        }
        
        StatsManager.ChannelStats channelStats = serverStats.channels.stream()
                .filter(c -> c.channelId.equals(channelId))
                .findFirst()
                .orElse(null);
        
        if (channelStats == null) {
            return;
        }
        
        // Track recent heat for sustained heat detection
        String heatKey = serverId + ":" + channelId;
        addToHeatHistory(heatKey, channelStats.averageHeatIndex);
        
        // Check if channel has abnormally high average heat AND sustained high heat
        double HEAT_ALERT_THRESHOLD = 0.35;
        if (channelStats.averageHeatIndex > HEAT_ALERT_THRESHOLD && isSustainedHighHeat(heatKey)) {
            // Check if alert cooldown has expired
            Long lastAlert = lastAlertTime.get(heatKey);
            long currentTime = System.currentTimeMillis();
            long cooldownMs = serverConfig.config.alert_cooldown_seconds * 1000; // Convert seconds to ms
            
            if (lastAlert == null || (currentTime - lastAlert) >= cooldownMs) {
                sendAlert(event.getJDA(), serverId, channelName, channelStats.averageHeatIndex, messageHeatIndex);
                lastAlertTime.put(heatKey, currentTime);
            }
        }
    }
    
    /**
     * Adds a heat value to the recent heat history for a channel.
     * Maintains only the last MAX_HEAT_HISTORY values.
     */
    private void addToHeatHistory(String heatKey, double heat) {
        LinkedHashMap<Long, Double> history = recentChannelHeats.get(heatKey);
        if (history == null) {
            history = new LinkedHashMap<Long, Double>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > MAX_HEAT_HISTORY;
                }
            };
            recentChannelHeats.put(heatKey, history);
        }
        history.put(System.currentTimeMillis(), heat);
    }
    
    /**
     * Checks if a channel has sustained high heat (multiple recent messages with elevated heat).
     * Returns true if the average of recent heat values meets or exceeds SUSTAINED_HEAT_THRESHOLD.
     */
    private boolean isSustainedHighHeat(String heatKey) {
        LinkedHashMap<Long, Double> history = recentChannelHeats.get(heatKey);
        if (history == null || history.size() < 3) {
            // Need at least 3 recent messages to consider it "sustained"
            return false;
        }
        
        double sum = history.values().stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / history.size();
        return average >= SUSTAINED_HEAT_THRESHOLD;
    }

    /**
     * Sends an alert to the configured alerts channel.
     * @param jda The JDA instance for sending messages.
     * @param serverId The ID of the server.
     * @param channelName The name of the channel with high heat.
     * @param averageHeat The average heat index of the channel.
     * @param latestHeat The heat index of the latest message.
     */
    private void sendAlert(net.dv8tion.jda.api.JDA jda, String serverId, String channelName, double averageHeat, double latestHeat) {
        App.ServerNode serverConfig = App.guildConfigs.get(serverId);
        if (serverConfig == null || serverConfig.config.alert_channel_id == null) {
            return;
        }
        
        try {
            TextChannel alertChannel = jda.getTextChannelById(serverConfig.config.alert_channel_id);
            if (alertChannel == null) {
                System.err.println("Alert channel not found for server ID: " + serverId);
                return;
            }
            
            // Determine color based on heat level severity
            Color embedColor = Color.YELLOW;
            if (averageHeat > 0.8) {
                embedColor = new Color(255, 69, 0); // Orange-red
            } else if (averageHeat > 0.5) {
                embedColor = Color.ORANGE;
            }
            
            // Find the actual channel ID
            String targetChannelId = alertChannel.getGuild().getTextChannelsByName(channelName, true)
                    .stream()
                    .findFirst()
                    .map(c -> c.getId())
                    .orElse("unknown");
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(embedColor)
                    .setTitle("⚠ Channel Heat Alert", null)
                    .setDescription("A channel is experiencing abnormally high heat levels.")
                    .addField("Channel", "<#" + targetChannelId + ">", false)
                    .addField("Average Heat", String.format("%.3f", averageHeat), true)
                    .addField("Latest Message Heat", String.format("%.3f", latestHeat), true)
                    .setFooter("Selah Moderation System", null)
                    .setTimestamp(java.time.Instant.now());
            
            alertChannel.sendMessageEmbeds(embed.build()).queue();
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Heat alert sent for channel #" + channelName);
            }
        } catch (Exception e) {
            System.err.println("Failed to send alert: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stub method to send warnings to a user or channel.
     * To be implemented when warning functionality is added.
     * @param jda The JDA instance for sending messages.
     * @param serverId The ID of the server.
     * @param userId The ID of the user to warn.
     * @param reason The reason for the warning.
     */
    private void sendWarning(net.dv8tion.jda.api.JDA jda, String serverId, String userId, String reason) {
        // TODO: Implement warning functionality
        if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Warning stub called for user: " + userId + " | Reason: " + reason);
        }
    }

    /**
     * Stub method to check if a user should be warned.
     * To be implemented when warning thresholds are defined.
     * @param serverId The ID of the server.
     * @param userId The ID of the user to check.
     * @return true if the user should be warned, false otherwise.
     */
    private boolean shouldWarnUser(String serverId, String userId) {
        // TODO: Implement warning threshold logic
        return false;
    }
}
