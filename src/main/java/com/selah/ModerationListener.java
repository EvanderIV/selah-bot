package com.selah;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    
    // Static cache for compiled regex patterns to avoid recompilation
    // Key format: "regex:flags" (e.g., "\\b\\w+\\b:2" for CASE_INSENSITIVE flag)
    private static final Map<String, Pattern> patternCache = new HashMap<>();
    
    // Static map to track the last time an alert was sent per channel
    // Key format: "serverId:channelId"
    private static final Map<String, Long> lastAlertTime = new HashMap<>();
    
    // Static map to track recent channel heat values for sustained heat detection
    // Key format: "serverId:channelId", Value is a LinkedHashMap of timestamps to heat values
    private static final Map<String, LinkedHashMap<Long, Double>> recentChannelHeats = new HashMap<>();
    private static final int MAX_HEAT_HISTORY = 10; // Track last 10 messages for sustained heat detection
    private static final double SUSTAINED_HEAT_THRESHOLD = 1.0; // Average must be at least this for sustained status
    
    // Number of previous messages to include in context for split-message detection
    // Recommended: 8 (catches most split attempts without excessive API calls)
    // Can be adjusted 1-10 for different sensitivity levels
    private static final short PREVIOUS_MESSAGES_TO_CHECK = 8;
    
    // Dynamic message length thresholds for optimizing historical context depth
    // Longer messages result in fewer historical checks to reduce network/processing overhead
    private static final int MESSAGE_LENGTH_THRESHOLD_SHORT = 4;      // < 4 chars: use full depth
    private static final int MESSAGE_LENGTH_THRESHOLD_MEDIUM = 6;    // 4-6 chars: medium depth
    private static final int MESSAGE_LENGTH_THRESHOLD_LONG = 10;      // 6-10 chars: shallow depth
    // > 300 chars: minimal depth
    
    /**
     * Calculates the number of previous messages to check based on current message length.
     * Longer messages reduce the historical depth to minimize network calls and processing time.
     * @param messageLength The length of the current message in characters.
     * @return The number of previous messages to check (1-8).
     */
    private static short calculateHistoricalDepth(int messageLength) {
        if (messageLength < MESSAGE_LENGTH_THRESHOLD_SHORT) {
            return PREVIOUS_MESSAGES_TO_CHECK; // Full depth: 8 messages
        } else if (messageLength < MESSAGE_LENGTH_THRESHOLD_MEDIUM) {
            return 6; // Medium depth: 6 messages
        } else if (messageLength < MESSAGE_LENGTH_THRESHOLD_LONG) {
            return 4; // Shallow depth: 4 messages
        } else {
            return 0; // Minimal depth: 1 messages (very long current message)
        }
    }

    /**
     * Retrieves or creates a cached Pattern for the given regex string and flags.
     * Patterns are compiled once and reused for all subsequent calls.
     * @param regex The regex pattern string to compile.
     * @param flags The Pattern flags (e.g., Pattern.CASE_INSENSITIVE).
     * @return A compiled Pattern object, either from cache or newly compiled.
     */
    private static Pattern getPattern(String regex, int flags) {
        String key = regex + ":" + flags;
        return patternCache.computeIfAbsent(key, k -> Pattern.compile(regex, flags));
    }

    static {
        AFFECTION_PATTERNS.put("Y[EA]S+", 0.15); // Case-sensitive match for "YES" followed by any number of 's'
        AFFECTION_PATTERNS.put("(?i)y[ea]sss+", 0.1); // Case-insensitive match for "yesss" followed by any number of 's'
        // Add more patterns and their affection ratings here if needed
    }

    /**
     * Calculates the timeout duration (in seconds) based on the user's infraction level.
     * Supports multiple calculation modes: flat, factorial, and exponential.
     * @param infractionLevel The user's current infraction level.
     * @param infractionMode The mode for calculating timeout: "flat", "factorial", or "exponential".
     * @param baseTimeoutSeconds The base timeout in seconds.
     * @return The timeout duration in seconds. Returns 0 if no timeout should be applied.
     */
    private static long getTimeoutDurationForLevel(int infractionLevel, String infractionMode, long baseTimeoutSeconds) {
        if (infractionLevel <= 0 || baseTimeoutSeconds <= 0) {
            return 0; // No timeout for level 0 or invalid base
        }
        
        switch (infractionMode.toLowerCase()) {
            case "factorial": {
                // Calculate n! where n is the infraction level, multiplied by base timeout
                long factorial = 1;
                for (int i = 2; i <= infractionLevel; i++) {
                    factorial *= i;
                }
                // Cap at a reasonable maximum (e.g., 24 hours = 86400 seconds)
                long result = factorial * baseTimeoutSeconds;
                return Math.min(result, 86400);
            }
            case "exponential": {
                // Calculate base^n where base is baseTimeoutSeconds and n is infractionLevel
                long result = (long) Math.pow(baseTimeoutSeconds, infractionLevel);
                // Cap at a reasonable maximum (e.g., 24 hours = 86400 seconds)
                return Math.min(result, 86400);
            }
            case "flat":
            default: {
                // Flat mode: just return base timeout applied at level 1+
                // Optionally scale by infraction level: baseTimeoutSeconds * infractionLevel
                return baseTimeoutSeconds;
            }
        }
    }

    /**
     * Converts a duration in seconds to a human-readable format.
     * Examples: 30s, 1m 30s, 1h 5m, 1d 2h 30m
     * @param seconds The duration in seconds.
     * @return A human-readable string representation.
     */
    private static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0) sb.append(secs).append("s");
        
        return sb.toString().trim();
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

        if (App.DEBUG_MODE) {
            System.out.println("Affection change: " + affectionChange);
        }

        // Update the relations array
        StatsManager.updateUserRelation(serverId, reactorId, messageAuthorId, affectionChange);
    }

    /**
     * Strips Discord markdown from a message.
     * Removes: **bold**, __bold__, *italic*, _italic_, `code`, ```code blocks```, ~~strikethrough~~, ||spoilers||, etc.
     * @param message The message content to strip markdown from.
     * @return The message with markdown removed.
     */
    private static String stripMarkdown(String message) {
        return BannedWordScanner.stripMarkdown(message);
    }

    private void checkForBannedWords(MessageReceivedEvent event) {
        String serverId = event.getGuild().getId();
        App.ServerNode config = App.guildConfigs.get(serverId);
        if (config == null || config.config.banned_words == null || config.config.banned_words.isEmpty()) {
            return;
        }

        String messageContentRaw = event.getMessage().getContentRaw();
        // Strip markdown before checking for banned words
        final String messageContent = stripMarkdown(messageContentRaw);
        
        // Check current message first (non-blocking)
        if (checkBannedWordsInMessage(event, messageContent, messageContent, config, serverId)) {
            return; // Found in current message, already handled
        }
        
        // Only fetch context asynchronously if:
        // 1. Not in diet mode
        // 2. Current message was clean
        // 3. Message is shorter than 10 characters (longer messages have enough space for a slur)
        if (!App.DIET_MODE && messageContent.length() < 10) {
            // Fetch history asynchronously to check context
            short historicalDepth = calculateHistoricalDepth(messageContent.length());
            TextChannel textChannel = (TextChannel) event.getChannel();
            
            textChannel.getHistory().retrievePast(historicalDepth + 1).queue(
                history -> {
                    // Process with context asynchronously
                    if (history.size() > 1) {
                        StringBuilder contextBuilder = new StringBuilder();
                        
                        // Iterate from the end of history (oldest) to beginning (newest, just before current)
                        for (int i = history.size() - 1; i >= 1; i--) {
                            String previousContent = history.get(i).getContentRaw();
                            previousContent = stripMarkdown(previousContent);
                            
                            if (!previousContent.isEmpty()) {
                                if (contextBuilder.length() > 0) {
                                    contextBuilder.append(" ");
                                }
                                contextBuilder.append(previousContent);
                            }
                        }
                        
                        // Check with context
                        String messageWithContext = contextBuilder.length() > 0 
                            ? contextBuilder.toString() + " " + messageContent 
                            : messageContent;
                        
                        checkBannedWordsInMessage(event, messageContent, messageWithContext, config, serverId);
                    }
                },
                error -> {
                    if (App.DEBUG_MODE) {
                        System.out.println("[DEBUG] Could not retrieve previous messages: " + error.getMessage());
                    }
                }
            );
        }
    }

    /**
     * Core method for checking banned words in a message.
     * @param event The message event
     * @param messageContent The current message content
     * @param messageWithContext The message with previous message context
     * @param config The server config
     * @param serverId The server ID
     * @return true if a banned word was found and punishment was applied, false otherwise
     */
    private boolean checkBannedWordsInMessage(MessageReceivedEvent event, String messageContent, String messageWithContext, App.ServerNode config, String serverId) {
        
        for (String bannedWord : config.config.banned_words) {
            // Check if the word appears anywhere (isolated or not)
            // First check the current message alone
            boolean foundInCurrentMessage = BannedWordScanner.isBannedWordPresent(messageContent, bannedWord);
            
            // Only check context variations if:
            // 1. Diet mode is disabled
            // 2. Current message check was clean
            // 3. Message is short enough that we would have fetched context (<10 chars)
            boolean shouldCheckContext = !App.DIET_MODE && !foundInCurrentMessage && messageContent.length() < 10;
            
            boolean foundWithContext = false;
            boolean foundWithContextNoSpaces = false;
            
            if (shouldCheckContext) {
                // Only check context if current message is clean AND diet mode is disabled AND message is short
                foundWithContext = BannedWordScanner.isBannedWordPresent(messageWithContext, bannedWord);
                // Also check for split words with spaces removed
                String messageWithContextNoSpaces = messageWithContext.replaceAll("\\s+", "");
                foundWithContextNoSpaces = BannedWordScanner.isBannedWordPresent(messageWithContextNoSpaces, bannedWord);
            }
            
            if (foundInCurrentMessage || foundWithContext || foundWithContextNoSpaces) {
                String userId = event.getAuthor().getId();
                
                // Get the user's current infraction level to determine timeout duration
                long timeoutDurationSeconds = 0;
                int infractionLevel = 0;
                StatsManager.ServerStats serverStats = StatsManager.liveStats.get(serverId);
                if (serverStats != null) {
                    StatsManager.MemberStats memberStats = serverStats.members.get(userId);
                    if (memberStats != null) {
                        infractionLevel = memberStats.infractionLevel;
                    }
                }
                
                // Calculate timeout duration based on current infraction level (before increment)
                timeoutDurationSeconds = getTimeoutDurationForLevel(infractionLevel, config.config.timeout_mode, config.config.timeout_base_seconds);
                
                // Attempt to timeout the user if configured and timeout duration > 0
                boolean timeoutApplied = false;
                if (config.config.timeout_for_filtered_messages && timeoutDurationSeconds > 0) {
                    try {
                        java.time.Duration timeout = java.time.Duration.ofSeconds(timeoutDurationSeconds);
                        net.dv8tion.jda.api.entities.Member member = event.getMember();
                        if (member != null) {
                            member.timeoutFor(timeout).queue();
                            timeoutApplied = true;
                            if (App.DEBUG_MODE) {
                                System.out.println("[DEBUG] Timed out user " + event.getAuthor().getName() + " (level " + infractionLevel + ") for " + formatDuration(timeoutDurationSeconds));
                            }
                        } else {
                            if (App.DEBUG_MODE) {
                                System.out.println("[DEBUG] Could not find member to timeout: " + event.getAuthor().getName());
                            }
                        }
                    } catch (Exception e) {
                        if (App.DEBUG_MODE) {
                            System.out.println("[DEBUG] Failed to timeout user: " + e.getMessage());
                        }
                    }
                }
                
                // Send warning for any detection (include full message content and timeout status)
                String reason = "Used or attempted to use banned word: " + bannedWord;
                sendWarning(event.getJDA(), serverId, userId, reason, messageContent, timeoutApplied, timeoutDurationSeconds);
                
                // Delete the message AFTER sending the warning (send is async, so this queues the delete after)
                if (config.config.delete_filtered_messages) {
                    // Queue delete with a 100ms delay to ensure the warning is processed first
                    event.getMessage().delete().queueAfter(100, java.util.concurrent.TimeUnit.MILLISECONDS, 
                        success -> {
                            if (App.DEBUG_MODE) {
                                System.out.println("[DEBUG] Deleted message containing banned word: " + bannedWord);
                            }
                        }
                    );
                }
                
                // Check if it's isolated (word boundaries) - only then punish
                String isolatedPattern = "\\b" + bannedWord + "\\b";
                if (getPattern(isolatedPattern, Pattern.CASE_INSENSITIVE).matcher(messageContent).find()) {
                    try {
                        PunishmentManager.invokePunishment(event.getAuthor(), event.getGuild(), "Used banned word: " + bannedWord);
                        if (App.DEBUG_MODE) {
                            System.out.println("[DEBUG] Punishment invoked for user " + event.getAuthor().getName());
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to invoke punishment: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                return true; // Stop after first infraction
            }
        }
        return false; // No banned words found
    }

    private void checkForBannedWords(MessageUpdateEvent event) {
        String serverId = event.getGuild().getId();
        App.ServerNode config = App.guildConfigs.get(serverId);
        if (config == null || config.config.banned_words == null || config.config.banned_words.isEmpty()) {
            return;
        }

        String messageContentRaw = event.getMessage().getContentRaw();
        // Strip markdown before checking for banned words
        final String messageContent = stripMarkdown(messageContentRaw);
        
        // Check current message first (non-blocking)
        if (checkBannedWordsInMessageUpdate(event, messageContent, messageContent, config, serverId)) {
            return; // Found in current message, already handled
        }
        
        // Only fetch context asynchronously if:
        // 1. Not in diet mode
        // 2. Current message was clean
        // 3. Message is shorter than 10 characters (longer messages have enough space for a slur)
        if (!App.DIET_MODE && messageContent.length() < 10) {
            // Fetch history asynchronously to check context
            short historicalDepth = calculateHistoricalDepth(messageContent.length());
            TextChannel textChannel = (TextChannel) event.getChannel();
            
            textChannel.getHistory().retrievePast(historicalDepth + 1).queue(
                history -> {
                    // Process with context asynchronously
                    if (history.size() > 1) {
                        StringBuilder contextBuilder = new StringBuilder();
                        
                        // Iterate from the end of history (oldest) to beginning (newest, just before current)
                        for (int i = history.size() - 1; i >= 1; i--) {
                            String previousContent = history.get(i).getContentRaw();
                            previousContent = stripMarkdown(previousContent);
                            
                            if (!previousContent.isEmpty()) {
                                if (contextBuilder.length() > 0) {
                                    contextBuilder.append(" ");
                                }
                                contextBuilder.append(previousContent);
                            }
                        }
                        
                        // Check with context
                        String messageWithContext = contextBuilder.length() > 0 
                            ? contextBuilder.toString() + " " + messageContent 
                            : messageContent;
                        
                        checkBannedWordsInMessageUpdate(event, messageContent, messageWithContext, config, serverId);
                    }
                },
                error -> {
                    if (App.DEBUG_MODE) {
                        System.out.println("[DEBUG] Could not retrieve previous messages: " + error.getMessage());
                    }
                }
            );
        }
    }

    /**
     * Core method for checking banned words in an updated message.
     * @param event The message update event
     * @param messageContent The current message content
     * @param messageWithContext The message with previous message context
     * @param config The server config
     * @param serverId The server ID
     * @return true if a banned word was found and punishment was applied, false otherwise
     */
    private boolean checkBannedWordsInMessageUpdate(MessageUpdateEvent event, String messageContent, String messageWithContext, App.ServerNode config, String serverId) {
        
        for (String bannedWord : config.config.banned_words) {
            // Check if the word appears anywhere (isolated or not)
            // First check the current message alone
            boolean foundInCurrentMessage = BannedWordScanner.isBannedWordPresent(messageContent, bannedWord);
            
            // Only check context variations if:
            // 1. Diet mode is disabled
            // 2. Current message check was clean
            // 3. Message is short enough that we would have fetched context (<10 chars)
            boolean shouldCheckContext = !App.DIET_MODE && !foundInCurrentMessage && messageContent.length() < 10;
            
            boolean foundWithContext = false;
            boolean foundWithContextNoSpaces = false;
            
            if (shouldCheckContext) {
                // Only check context if current message is clean AND diet mode is disabled AND message is short
                foundWithContext = BannedWordScanner.isBannedWordPresent(messageWithContext, bannedWord);
                // Also check for split words with spaces removed
                String messageWithContextNoSpaces = messageWithContext.replaceAll("\\s+", "");
                foundWithContextNoSpaces = BannedWordScanner.isBannedWordPresent(messageWithContextNoSpaces, bannedWord);
            }
            
            if (foundInCurrentMessage || foundWithContext || foundWithContextNoSpaces) {
                String userId = event.getAuthor().getId();
                
                // Get the user's current infraction level to determine timeout duration
                long timeoutDurationSeconds = 0;
                int infractionLevel = 0;
                StatsManager.ServerStats serverStats = StatsManager.liveStats.get(serverId);
                if (serverStats != null) {
                    StatsManager.MemberStats memberStats = serverStats.members.get(userId);
                    if (memberStats != null) {
                        infractionLevel = memberStats.infractionLevel;
                    }
                }
                
                // Calculate timeout duration based on current infraction level (before increment)
                timeoutDurationSeconds = getTimeoutDurationForLevel(infractionLevel, config.config.timeout_mode, config.config.timeout_base_seconds);
                
                // Attempt to timeout the user if configured and timeout duration > 0
                boolean timeoutApplied = false;
                if (config.config.timeout_for_filtered_messages && timeoutDurationSeconds > 0) {
                    try {
                        java.time.Duration timeout = java.time.Duration.ofSeconds(timeoutDurationSeconds);
                        net.dv8tion.jda.api.entities.Member member = event.getMember();
                        if (member != null) {
                            member.timeoutFor(timeout).queue();
                            timeoutApplied = true;
                            if (App.DEBUG_MODE) {
                                System.out.println("[DEBUG] Timed out user " + event.getAuthor().getName() + " (level " + infractionLevel + ") for " + formatDuration(timeoutDurationSeconds));
                            }
                        } else {
                            if (App.DEBUG_MODE) {
                                System.out.println("[DEBUG] Could not find member to timeout: " + event.getAuthor().getName());
                            }
                        }
                    } catch (Exception e) {
                        if (App.DEBUG_MODE) {
                            System.out.println("[DEBUG] Failed to timeout user: " + e.getMessage());
                        }
                    }
                }
                
                // Send warning for any detection (include full message content and timeout status)
                String reason = "Used or attempted to use banned word: " + bannedWord;
                sendWarning(event.getJDA(), serverId, userId, reason, messageContent, timeoutApplied, timeoutDurationSeconds);
                
                // Delete the message AFTER sending the warning (send is async, so this queues the delete after)
                if (config.config.delete_filtered_messages) {
                    // Queue delete with a 100ms delay to ensure the warning is processed first
                    event.getMessage().delete().queueAfter(100, java.util.concurrent.TimeUnit.MILLISECONDS, 
                        success -> {
                            if (App.DEBUG_MODE) {
                                System.out.println("[DEBUG] Deleted edited message containing banned word: " + bannedWord);
                            }
                        }
                    );
                }
                
                // Check if it's isolated (word boundaries) - only then punish
                String isolatedPattern = "\\b" + bannedWord + "\\b";
                if (getPattern(isolatedPattern, Pattern.CASE_INSENSITIVE).matcher(messageContent).find()) {
                    try {
                        PunishmentManager.invokePunishment(event.getAuthor(), event.getGuild(), "Used banned word: " + bannedWord);
                        if (App.DEBUG_MODE) {
                            System.out.println("[DEBUG] Punishment invoked for user " + event.getAuthor().getName() + " (edit)");
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to invoke punishment on edited message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                return true; // Stop after first infraction
            }
        }
        return false; // No banned words found
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
                // Check OCR result for offensive content
                checkOCRForOffensiveContent(event, ocrResult);
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
        
        // Ensure final heat is never negative
        heat = Math.max(0, heat);
        
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

        heat = Math.max(0, heat);

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
        String normalizedMessage = BannedWordScanner.normalizeForKeywordCheck(message);

        if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] Normalized message for keyword check: \"" + normalizedMessage + "\"");
        }

        // Check for standard keywords
        for (Keyword keyword : KeywordManager.keywords) {
            // Use word boundaries to match whole words
            String pattern = "\\b" + keyword.word + "\\b";
            if (getPattern(pattern, 0).matcher(normalizedMessage).find()) {
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
        
        StatsManager.ChannelStats channelStats = serverStats.channels.get(channelId);
        
        if (channelStats == null) {
            return;
        }
        
        // Track recent heat for sustained heat detection
        String heatKey = serverId + ":" + channelId;
        addToHeatHistory(heatKey, channelStats.averageHeatIndex);
        
        // Check if channel has abnormally high average heat AND sustained high heat
        double HEAT_ALERT_THRESHOLD = 0.35;
        if (channelStats.averageHeatIndex > HEAT_ALERT_THRESHOLD && isSustainedHighHeat(heatKey)) {
            if (App.DEBUG_MODE) {
                LinkedHashMap<Long, Double> history = recentChannelHeats.get(heatKey);
                double historyAverage = history.values().stream().mapToDouble(Double::doubleValue).sum() / history.size();
                System.out.println("[ALERT] Sustained high heat detected in #" + channelName + 
                        " | Channel avg: " + String.format("%.3f", channelStats.averageHeatIndex) + 
                        " | History size: " + history.size() + 
                        " | History avg: " + String.format("%.3f", historyAverage));
            }
            
            // Check if alert cooldown has expired
            Long lastAlert = lastAlertTime.get(heatKey);
            long currentTime = System.currentTimeMillis();
            long cooldownMs = serverConfig.config.alert_cooldown_seconds * 1000; // Convert seconds to ms
            
            if (lastAlert == null || (currentTime - lastAlert) >= cooldownMs) {
                sendAlert(event.getJDA(), serverId, channelName, channelStats.averageHeatIndex, messageHeatIndex);
                lastAlertTime.put(heatKey, currentTime);
            } else if (App.DEBUG_MODE) {
                long remainingCooldown = cooldownMs - (currentTime - lastAlert);
                System.out.println("[ALERT] Alert suppressed by cooldown for #" + channelName + 
                        " | Remaining: " + (remainingCooldown / 1000) + " seconds");
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
                protected boolean removeEldestEntry(Map.Entry<Long, Double> eldest) {
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
                    .setTitle("⚠️ Channel Heat Alert", null)
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
     * Checks OCR results for potentially offensive content, specifically variants of the n-word.
     * If offensive content is detected, sends a warning to admins.
     * @param event The message event containing server, channel, and author context.
     * @param ocrResult The text extracted from the image via OCR.
     */
    private void checkOCRForOffensiveContent(MessageReceivedEvent event, String ocrResult) {
        // Pattern to catch n-word variants: nigger, nigga, n1gger, mgger, etc.
        // Catches multiple patterns for common substitutions and variations
        String[] offensivePatterns = {
            "(?i)\\bn[i1]?gg[e3a]?r[as]?\\b",  // nigger, nigga, n1gger, nigg3r, niggra, etc.
            "(?i)\\bm[i1]?gg[e3a]?r[as]?\\b",  // mgger, migga, m1gger, etc.
            "(?i)\\bneg?r[oa]\\b"               // negro, negra (additional offensive variants)
        };
        
        boolean found = false;
        String matchedWord = null;
        
        for (String pattern : offensivePatterns) {
            java.util.regex.Matcher matcher = getPattern(pattern, Pattern.CASE_INSENSITIVE).matcher(ocrResult);
            if (matcher.find()) {
                found = true;
                matchedWord = matcher.group();
                if (App.DEBUG_MODE) {
                    System.out.println("[DEBUG] Offensive content matched: '" + matchedWord + "' using pattern: " + pattern);
                }
                break;
            }
        }
        
        if (found) {
            String serverId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            String reason = "Posted image containing potentially offensive content (OCR match: '" + matchedWord + "')";
            
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Offensive content detected in OCR result for user " + event.getAuthor().getName());
            }
            
            sendWarning(event.getJDA(), serverId, userId, reason);
        } else if (App.DEBUG_MODE) {
            System.out.println("[DEBUG] No offensive content detected in OCR result");
        }
    }

    /**
     * Sends a warning to the configured warning channel about a user's offense.
     * @param jda The JDA instance for sending messages.
     * @param serverId The ID of the server.
     * @param userId The ID of the user to warn.
     * @param reason The reason for the warning.
     * @param messageContent The full content of the offending message for auditing.
     * @param timeoutApplied Whether the timeout was successfully applied.
     * @param timeoutDurationSeconds The timeout duration in seconds.
     */
    private void sendWarning(net.dv8tion.jda.api.JDA jda, String serverId, String userId, String reason, String messageContent, boolean timeoutApplied, long timeoutDurationSeconds) {
        App.ServerNode serverConfig = App.guildConfigs.get(serverId);
        if (serverConfig == null || serverConfig.config.warning_channel_id == null || serverConfig.config.warning_channel_id.isEmpty()) {
            return; // No warning channel configured
        }
        
        try {
            TextChannel warningChannel = jda.getTextChannelById(serverConfig.config.warning_channel_id);
            if (warningChannel == null) {
                System.err.println("Warning channel not found for server ID: " + serverId);
                return;
            }
            
            // Use the user ID directly (we already have it, no need to fetch user info in callback thread)
            String userName = "<@" + userId + ">";
            
            // Truncate message content if too long for embed field (max 1024 chars per field)
            String displayMessage = messageContent.length() > 1000 
                ? messageContent.substring(0, 1000) + "..." 
                : messageContent;
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(255, 165, 0)) // Orange for warning
                    .setTitle("🚨 User Warning", null)
                    .setDescription("A user has posted potentially offensive content.")
                    .addField("User", userName, false)
                    .addField("Reason", reason, false)
                    .addField("Message Content", "```\n" + displayMessage + "\n```", false)
                    .addField("Server", serverConfig.name, true)
                    .addField("User ID", userId, true);
            
            // Add timeout status field (only if timeoutDurationSeconds > 0, meaning timeout is configured)
            if (timeoutDurationSeconds > 0) {
                if (!timeoutApplied && serverConfig.config.timeout_for_filtered_messages) {
                    embed.addField("⚠️ Timeout Status", "❌ Timeout was NOT applied (bot may lack MODERATE_MEMBERS permission)\nDuration: " + formatDuration(timeoutDurationSeconds), true);
                } else if (timeoutApplied) {
                    embed.addField("✅ Timeout Status", "User was timed out for " + formatDuration(timeoutDurationSeconds), true);
                }
            }
            
            embed.setFooter("Selah Moderation System", null)
                    .setTimestamp(java.time.Instant.now());
            
            warningChannel.sendMessageEmbeds(embed.build()).queue();
            if (App.DEBUG_MODE) {
                System.out.println("[DEBUG] Warning sent for user " + userName + " | Reason: " + reason + " | Timeout applied: " + timeoutApplied);
            }
        } catch (Exception e) {
            System.err.println("Failed to send warning: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a warning to the configured warning channel about a user's offense.
     * @param jda The JDA instance for sending messages.
     * @param serverId The ID of the server.
     * @param userId The ID of the user to warn.
     * @param reason The reason for the warning.
     * @param messageContent The full content of the offending message for auditing.
     * @param timeoutApplied Whether the timeout was successfully applied.
     */
    private void sendWarning(net.dv8tion.jda.api.JDA jda, String serverId, String userId, String reason, String messageContent, boolean timeoutApplied) {
        sendWarning(jda, serverId, userId, reason, messageContent, timeoutApplied, 0);
    }

    /**
     * Sends a warning to the configured warning channel about a user's offense.
     * Overloaded sendWarning method without message content (for OCR warnings).
     * @param jda The JDA instance for sending messages.
     * @param serverId The ID of the server.
     * @param userId The ID of the user to warn.
     * @param reason The reason for the warning.
     */
    private void sendWarning(net.dv8tion.jda.api.JDA jda, String serverId, String userId, String reason) {
        sendWarning(jda, serverId, userId, reason, "", false, 0);
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
