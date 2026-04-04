package com.selah;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader; // <-- Added this import
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
        
        // Array of individual channels
        public List<ChannelStats> channels = new ArrayList<>();
        
        // Array of individual members
        public List<MemberStats> members = new ArrayList<>();

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
        public double recordHighHeat = 0.0;

        public int moderateHeatMessages = 0;
        public double moderateHeatAverage = 0.0;

        public int highHeatMessages = 0;
        public double highHeatAverage = 0.0;

        public int extremeHeatMessages = 0;

        public MemberStats(String memberName, String memberId) {
            this.memberName = memberName;
            this.memberId = memberId;
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
        if (!Files.exists(statsDir)) {
            try {
                Files.createDirectories(statsDir);
                System.out.println("INFO: 'server_stats' directory not found. Creating it now.");
            } catch (IOException e) {
                System.err.println("CRITICAL: Failed to create the 'server_stats' directory. Statistics will not be saved.");
                e.printStackTrace();
                return; // Exit if we can't create the directory
            }
        }
        
        Gson gson = new Gson();
        
        for (App.ServerNode serverConfig : App.guildConfigs.values()) {
            String serverId = serverConfig.id;
            Path filePath = Path.of(App.WORKING_DIRECTORY + "server_stats/" + serverId + ".json");
            
            if (Files.exists(filePath)) {
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    ServerStats stats = gson.fromJson(reader, ServerStats.class);
                    
                    // Failsafe: If JSON was manually edited and channels array was deleted, prevent NullPointerExceptions
                    if (stats.channels == null) {
                        stats.channels = new ArrayList<>();
                    }
                    if (stats.members == null) {
                        stats.members = new ArrayList<>();
                    }
                    
                    liveStats.put(serverId, stats);
                    System.out.println("Loaded existing historical stats for server: " + serverConfig.name);
                } catch (IOException | com.google.gson.JsonSyntaxException e) {
                    System.err.println("Failed to read stats file for " + serverConfig.name + ". Starting fresh.");
                    liveStats.put(serverId, new ServerStats(serverConfig.name, serverId));
                }
            } else {
                // Initialize fresh if no file exists (e.g., brand new server)
                liveStats.put(serverId, new ServerStats(serverConfig.name, serverId));
                System.out.println("No existing stats found for " + serverConfig.name + ". Starting fresh.");
            }
        }
    }

    /**
     * Saves a specific server's statistics to its own JSON file.
     * Output format: ../stats_1234567890.json
     */
    public static void saveServerStats(String serverId) {
        ServerStats stats = liveStats.get(serverId);
        if (stats == null) return; // Nothing to save

        // Using setPrettyPrinting makes the JSON readable in a text editor
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path filePath = Path.of(App.WORKING_DIRECTORY + "server_stats/" + serverId + ".json");

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(stats, writer);
        } catch (IOException e) {
            System.err.println("Failed to write stats file for server " + serverId);
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

        MemberStats memberStats = serverStats.members.stream()
                .filter(m -> m.memberId.equals(memberId))
                .findFirst()
                .orElse(null);

        if (memberStats == null) {
            memberStats = new MemberStats(memberName, memberId);
            serverStats.members.add(memberStats);
        }

        // Calculate a simple running average
        if (memberStats.averageHeatLevel == 0) {
            memberStats.averageHeatLevel = heatLevel;
        } else {
            memberStats.averageHeatLevel = (memberStats.averageHeatLevel + heatLevel) / 2.0;
        }

        if (heatLevel > memberStats.recordHighHeat) {
            memberStats.recordHighHeat = heatLevel;
        }

        if (heatLevel > 1.4) {
            memberStats.extremeHeatMessages++;
        } else if (heatLevel > 0.8) {
            memberStats.highHeatMessages++;
            if (memberStats.highHeatAverage == 0) {
                memberStats.highHeatAverage = heatLevel;
            } else {
                if (heatLevel > memberStats.highHeatAverage) {
                    memberStats.highHeatAverage = (0.75 * heatLevel) + (0.25 * memberStats.highHeatAverage);
                } else {
                    memberStats.highHeatAverage = (memberStats.highHeatAverage + heatLevel) / 2.0;
                }
            }
        } else if (heatLevel > 0.3) {
            memberStats.moderateHeatMessages++;
            if (memberStats.moderateHeatAverage == 0) {
                memberStats.moderateHeatAverage = heatLevel;
            } else {
                if (heatLevel > memberStats.moderateHeatAverage) {
                    memberStats.moderateHeatAverage = (0.75 * heatLevel) + (0.25 * memberStats.moderateHeatAverage);
                } else {
                    memberStats.moderateHeatAverage = (memberStats.moderateHeatAverage + heatLevel) / 2.0;
                }
            }
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
            boolean removed = stats.channels.removeIf(channel -> !existingChannelIds.contains(channel.channelId));
            
            if (removed) {
                int newSize = stats.channels.size();
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

        ChannelStats channelStats = serverStats.channels.stream()
                .filter(c -> c.channelId.equals(channelId))
                .findFirst()
                .orElse(null);

        if (channelStats == null) {
            channelStats = new ChannelStats(channelName, channelId);
            serverStats.channels.add(channelStats);
        }

        // --- Update Metrics ---
        serverStats.totalMessagesAnalyzed++;
        
        // Recalculate average heat
        if (channelStats.averageHeatIndex == 0) {
            channelStats.averageHeatIndex = heatIndex;
        } else {
            channelStats.averageHeatIndex = (channelStats.averageHeatIndex + heatIndex) / 2.0;
        }
    }
}