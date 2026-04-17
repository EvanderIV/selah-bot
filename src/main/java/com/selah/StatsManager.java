package com.selah;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import java.util.stream.Collectors;
import java.util.Set;

public class StatsManager {

    // A memory map holding the live statistics for every active server
    public static final Map<String, ServerStats> liveStats = new HashMap<>();

    // --- 1. The Data Structures ---
    
    public static class ServerStats {
        public String serverName;
        public String serverId;
        
        // Aggregate Statistics
        public int totalMessagesAnalyzed = 0;
        public int totalInfractions = 0;
        
        // Map of individual channels (keyed by channelId for O(1) lookups)
        public Map<String, ChannelStats> channels = new HashMap<>();
        
        // Map of individual members (keyed by memberId for O(1) lookups)
        public Map<String, MemberStats> members = new HashMap<>();

        public ServerStats(String serverName, String serverId) {
            this.serverName = serverName;
            this.serverId = serverId;
        }
    }

    public static class ChannelStats {
        public String channelName;
        public String channelId;
        
        // Advanced Metrics
        public double messagesPerMinute = 0.0;
        public double averageHeatIndex = 0.0;
        public int peakHour = 0; // 0-23 representing the hour of the day
        public double userInputDiversity = 0.0; // Ratio of unique users to total messages

        public ChannelStats(String channelName, String channelId) {
            this.channelName = channelName;
            this.channelId = channelId;
        }
    }

    public static class MemberStats {
        public String memberName;
        public String memberId;
        public double averageHeatLevel = 0.0;
        public int messageCount = 0; // Track total messages for weighted averaging
        public double recordHighHeat = 0.0;

        public int moderateHeatMessages = 0;
        public double moderateHeatAverage = 0.0;

        public int highHeatMessages = 0;
        public double highHeatAverage = 0.0;

        public int extremeHeatMessages = 0;

        public int infractionLevel = 0;
        public int totalInfractions = 0;
        public String lastInfractionDate = null;
        public String infractionLevelReductionDate = null;

        public List<MemberRelation> relations = new ArrayList<>(); // Added to track user relations

        public MemberStats(String memberName, String memberId) {
            this.memberName = memberName;
            this.memberId = memberId;
        }
    }

    public static class MemberRelation {
        public String targetMemberId;
        public double affection;

        public MemberRelation(String targetMemberId) {
            this.targetMemberId = targetMemberId;
            this.affection = 0.0;
        }
    }

    // --- 2. The File Loading/Writing Logic ---

    /**
     * Initializes the liveStats map by reading existing JSON files from the disk.
     * Should be called during the bot's boot sequence.
     */
    public static void loadServerStats() {
        // --- Create the directory if it doesn't exist ---
        Path statsDir = Path.of(App.WORKING_DIRECTORY + "server_stats");
        System.out.println("Loading server stats from: " + statsDir.toAbsolutePath()); // Debug log for directory

        if (!Files.exists(statsDir)) {
            System.out.println("Stats directory does not exist: " + statsDir.toAbsolutePath());
            return;
        }

        Gson gson = new Gson();

        for (App.ServerNode serverConfig : App.guildConfigs.values()) {
            String serverId = serverConfig.id;
            Path filePath = Path.of(App.WORKING_DIRECTORY + "server_stats/" + serverId + ".json");

            System.out.println("Attempting to load stats for server: " + serverConfig.name + " from file: " + filePath.toAbsolutePath()); // Debug log for file path

            if (Files.exists(filePath)) {
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    ServerStats stats = gson.fromJson(reader, ServerStats.class);

                    if (stats.channels == null) {
                        stats.channels = new HashMap<>();
                    }
                    if (stats.members == null) {
                        stats.members = new HashMap<>();
                    }
                    
                    // Initialize messageCount for members loaded from old stats files
                    // This ensures the weighted averaging formula works correctly
                    for (MemberStats member : stats.members.values()) {
                        if (member.messageCount == 0) {
                            // Calculate messageCount from message category counts
                            member.messageCount = member.extremeHeatMessages + member.highHeatMessages + member.moderateHeatMessages;
                            // If we have an averageHeatLevel but no message count, assume at least 1 message
                            if (member.messageCount == 0 && member.averageHeatLevel > 0) {
                                member.messageCount = 1;
                            }
                        }
                    }

                    liveStats.put(serverId, stats);
                    System.out.println("Loaded stats for server: " + serverConfig.name);
                } catch (IOException | com.google.gson.JsonSyntaxException e) {
                    System.err.println("Failed to read stats file for " + serverConfig.name + ". Starting fresh.");
                    e.printStackTrace();
                    liveStats.put(serverId, new ServerStats(serverConfig.name, serverId));
                }
            } else {
                System.out.println("No stats file found for server: " + serverConfig.name + ". Starting fresh.");
                liveStats.put(serverId, new ServerStats(serverConfig.name, serverId));
            }
        }
    }

    /**
     * Saves a specific server's statistics to its own JSON file.
     * Output format: ../stats_1234567890.json
     */
    public static void saveServerStats(String serverId) {
        ServerStats stats = liveStats.get(serverId);
        if (stats == null) {
            System.err.println("No stats found for server ID: " + serverId);
            return;
        }

        Path statsDir = Path.of(App.WORKING_DIRECTORY + "server_stats");
        Path filePath = Path.of(App.WORKING_DIRECTORY + "server_stats/" + serverId + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            // Ensure the server_stats directory exists
            if (!Files.exists(statsDir)) {
                Files.createDirectories(statsDir);
            }
            
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(stats, writer);
                System.out.println("Successfully saved stats for server ID: " + serverId);
            }
        } catch (IOException e) {
            System.err.println("Failed to save stats for server ID: " + serverId);
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to save all active servers at once.
     */
    public static void saveAllStats() {
        for (String serverId : liveStats.keySet()) {
            saveServerStats(serverId);
        }
    }

    /**
     * Updates a single member's heat level. If the member does not exist in the live stats, they will be created.
     * @param serverId The ID of the server the member is in.
     * @param memberId The ID of the member.
     * @param memberName The display name of the member.
     * @param heatLevel The heat level of the member's latest message.
     */
    public static void updateMemberHeatLevel(String serverId, String memberId, String memberName, double heatLevel) {
        ServerStats serverStats = liveStats.get(serverId);
        if (serverStats == null) return;

        MemberStats memberStats = serverStats.members.get(memberId);

        if (memberStats == null) {
            memberStats = new MemberStats(memberName, memberId);
            serverStats.members.put(memberId, memberStats);
        }

        // Calculate a proper weighted running average using message count
        memberStats.messageCount++;
        memberStats.averageHeatLevel = ((memberStats.averageHeatLevel * (memberStats.messageCount - 1)) + heatLevel) / memberStats.messageCount;

        if (heatLevel > memberStats.recordHighHeat) {
            memberStats.recordHighHeat = heatLevel;
        }

        if (heatLevel > 1.4) {
            memberStats.extremeHeatMessages++;
        } else if (heatLevel > 0.8) {
            memberStats.highHeatMessages++;
            // Use weighted average based on message count
            memberStats.highHeatAverage = ((memberStats.highHeatAverage * (memberStats.highHeatMessages - 1)) + heatLevel) / memberStats.highHeatMessages;
        } else if (heatLevel > 0.3) {
            memberStats.moderateHeatMessages++;
            // Use weighted average based on message count
            memberStats.moderateHeatAverage = ((memberStats.moderateHeatAverage * (memberStats.moderateHeatMessages - 1)) + heatLevel) / memberStats.moderateHeatMessages;
        }
    }

    // --- 3. Background Schedulers ---

    // A thread pool dedicated entirely to background saving tasks
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Removes channels from the statistics that no longer exist on the Discord server.
     * This should be called once on startup to ensure data consistency.
     * @param jda The JDA instance, used to fetch live channel data.
     */
    public static void synchronizeChannels(JDA jda) {
        System.out.println("Running channel synchronization...");
        for (String serverId : liveStats.keySet()) {
            Guild guild = jda.getGuildById(serverId);
            if (guild == null) {
                System.out.println("WARN: Could not find guild with ID " + serverId + " during channel sync. It might have been removed.");
                continue;
            }

            ServerStats stats = liveStats.get(serverId);
            if (stats.channels == null || stats.channels.isEmpty()) {
                continue; // No channels to sync for this server
            }

            // Get a set of current, valid channel IDs from the guild
            Set<String> existingChannelIds = guild.getChannels().stream()
                    .filter(c -> c instanceof TextChannel)
                    .map(c -> c.getId())
                    .collect(Collectors.toSet());

            // Remove any channels from our stats that are not in the valid set
            int originalSize = stats.channels.size();
            stats.channels.keySet().removeIf(channelId -> !existingChannelIds.contains(channelId));
            
            int newSize = stats.channels.size();
            if (originalSize > newSize) {
                System.out.println("Synced channels for " + guild.getName() + ". Removed " + (originalSize - newSize) + " stale channel(s).");
            }
        }
        System.out.println("Channel synchronization complete.");
    }

    /**
     * Loops through all loaded server configurations and assigns a custom timer 
     * to each one based on its specific save_interval setting.
     */
    public static void startSaveTimers() {
        for (App.ServerNode server : App.guildConfigs.values()) {
            int interval = server.config.save_interval;
            
            // Failsafe: Prevent someone from accidentally setting a 0 or negative interval
            if (interval <= 0) interval = 5; 

            // Schedule the repeating task
            scheduler.scheduleAtFixedRate(() -> {
                saveServerStats(server.id);
                if (App.DEBUG_MODE) System.out.println("Auto-saved stats for " + server.name + " at " + java.time.LocalTime.now());
            }, interval, interval, TimeUnit.MINUTES);
            
            System.out.println("Started save timer for " + server.name + " (Every " + interval + " mins)");
        }
    }

    /**
     * A crucial failsafe to run when the bot is shutting down.
     */
    public static void shutdownGracefully() {
        System.out.println("Shutting down! Forcing a final save of all live stats...");
        saveAllStats();
        scheduler.shutdown();
        System.out.println("Shutdown complete. Exiting.");
    }

    public static void updateChannelStats(String serverId, String channelId, String channelName, double heatIndex) {
        ServerStats serverStats = liveStats.get(serverId);
        if (serverStats == null) return;

        ChannelStats channelStats = serverStats.channels.get(channelId);

        if (channelStats == null) {
            channelStats = new ChannelStats(channelName, channelId);
            serverStats.channels.put(channelId, channelStats);
        }

        // --- Update Metrics ---
        serverStats.totalMessagesAnalyzed++;
        
        // Recalculate average heat
        if (channelStats.averageHeatIndex == 0) {
            channelStats.averageHeatIndex = heatIndex;
        } else {
            if (heatIndex > channelStats.averageHeatIndex) {
                channelStats.averageHeatIndex = (0.75 * heatIndex) + (0.25 * channelStats.averageHeatIndex);
            } else {
                channelStats.averageHeatIndex = (0.50 * channelStats.averageHeatIndex) + (0.50 * heatIndex);
            }
        }
    }

    public static void updateUserRelation(String serverId, String reactorId, String targetId, double affectionChange) {
        ServerStats serverStats = liveStats.get(serverId);
        if (serverStats == null) {
            System.err.println("Server stats not found for server ID: " + serverId);
            return;
        }

        MemberStats reactor = serverStats.members.get(reactorId);

        if (reactor == null) {
            if (App.DEBUG_MODE) {
                System.err.println("Reactor not found in server stats: " + reactorId);
            }
            return;
        }

        MemberStats target = serverStats.members.get(targetId);

        if (target == null) {
            if (App.DEBUG_MODE) {
                System.out.println("Target not found in server stats: " + targetId);
            }
            return;
        }

        // Update or add the relation
        if (reactor.relations == null) {
            reactor.relations = new ArrayList<>();
        }

        MemberRelation relation = reactor.relations.stream()
            .filter(r -> r.targetMemberId.equals(targetId))
            .findFirst()
            .orElseGet(() -> {
                MemberRelation newRelation = new MemberRelation(targetId);
                reactor.relations.add(newRelation);
                return newRelation;
            });

        relation.affection += affectionChange;
        if (App.DEBUG_MODE) {
            System.out.println("Updated affection between " + reactorId + " and " + targetId + ": " + relation.affection);
        }
    }

    /**
     * Adjusts the affection level between two members.
     * The incoming affection is scaled based on the current affection level.
     *
     * @param currentAffection The current affection level.
     * @param incomingAffection The incoming affection adjustment.
     * @return The updated affection level, capped at +/-10.0.
     */
    public static double adjustAffection(double currentAffection, double incomingAffection) {
        // Cap affection at +/- 10.0
        double maxAffection = 10.0;
        double minAffection = -10.0;

        // If current affection is 0, apply incoming affection as-is
        if (currentAffection == 0) {
            currentAffection += incomingAffection;
        } else {
            // Scale incoming affection by dividing by the absolute value of current affection
            currentAffection += incomingAffection / Math.abs(currentAffection);
        }

        // Ensure affection stays within bounds
        if (currentAffection > maxAffection) {
            currentAffection = maxAffection;
        } else if (currentAffection < minAffection) {
            currentAffection = minAffection;
        }

        // Apply a react bonus of 0.01 only if current affection is less than 4
        if (incomingAffection == 0.01 && currentAffection >= 4) {
            return currentAffection; // No bonus applied
        }

        return currentAffection;
    }
}