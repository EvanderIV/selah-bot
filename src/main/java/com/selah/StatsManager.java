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

    // --- 2. The File Loading/Writing Logic ---

    /**
     * Initializes the liveStats map by reading existing JSON files from the disk.
     * Should be called during the bot's boot sequence.
     */
    public static void loadServerStats() {
        Gson gson = new Gson();
        
        for (App.ServerNode serverConfig : App.guildConfigs.values()) {
            String serverId = serverConfig.id;
            Path filePath = Path.of("../stats_" + serverId + ".json");
            
            if (Files.exists(filePath)) {
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    ServerStats stats = gson.fromJson(reader, ServerStats.class);
                    
                    // Failsafe: If JSON was manually edited and channels array was deleted, prevent NullPointerExceptions
                    if (stats.channels == null) {
                        stats.channels = new ArrayList<>();
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
        Path filePath = Path.of("../stats_" + serverId + ".json");

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

    // --- 3. Background Schedulers ---

    // A thread pool dedicated entirely to background saving tasks
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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
    }
}