package com.selah;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class App {

    public static boolean DEBUG_MODE = false;
    public static final String WORKING_DIRECTORY = getWorkingDirectory();

    private static String getWorkingDirectory() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            if ("evanos".equalsIgnoreCase(hostname)) {
                return "C:/Users/evanm/Documents/selah-bot/";
            }
        } catch (java.net.UnknownHostException e) {
            System.err.println("Could not determine hostname, defaulting to Linux path.");
        }
        return "/home/evanm/Bots/selah-bot/";
    }

    // --- 1. Java Object Mappings for Gson ---
    // These classes strictly mirror the structure of servers.json
    public static class ConfigData {
        public List<ServerNode> servers;
    }

    public static class ServerNode {
        public String name;
        public String id;
        public ModSettings config;
    }

    public static class ModSettings {
        public boolean bad_word_filter;
        public boolean slowmode_alerts;
        public String log_channel_id;
        public int save_interval = 5;
    }

    // --- 2. The Global Memory Store ---
    // A highly efficient map to store configs by Server ID
    public static final Map<String, ServerNode> guildConfigs = new HashMap<>();

    public static void main(String[] args) {

        if (args.length > 1 && ("--check".equalsIgnoreCase(args[0]) || "-c".equalsIgnoreCase(args[0]))) {
            DEBUG_MODE = true;
            KeywordManager.loadKeywords(); // Load keywords from JSON
            System.out.println("--- OFFLINE: THIS SESSION IS NOT RECORDED ---");
            ModerationListener.getHeatIndex(args[1]);
            return;
        }

        if (args.length > 1 && ("--get".equalsIgnoreCase(args[0]) || "-g".equalsIgnoreCase(args[0]))) {
            DEBUG_MODE = true;
            KeywordManager.loadKeywords(); // Load keywords from JSON
            System.out.println("--- OFFLINE: THIS SESSION IS NOT RECORDED ---");
            ModerationListener.getHeatIndex(args[1]);
            return;
        }

        // Check for debug flag
        if (args.length > 0 && ("--debug".equalsIgnoreCase(args[0]) || "-d".equalsIgnoreCase(args[0]))) {
            DEBUG_MODE = true;
            System.out.println("--- DEBUG MODE ENABLED ---");
        }
        
        // --- 3. Load and Parse the JSON ---
        loadConfigurations();
        KeywordManager.loadKeywords(); // Load keywords from JSON
        StatsManager.loadServerStats(); // Get historical stats from disk into memory

        // --- 4. Initialize the Bot ---
        String token = System.getenv("SELAH_DISCORD_TOKEN");
        if (token == null) {
            System.err.println("ERROR: Please set the SELAH_DISCORD_TOKEN environment variable.");
            return;
        }

        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new ModerationListener()) 
                    .build()
                    .awaitReady();

            System.out.println("Selah Online. Logged in as " + jda.getSelfUser().getName());

            if (args.length > 1 && "archive".equalsIgnoreCase(args[0])) {
                String channelId = args[1];
                ArchiveManager.archiveChannel(jda, channelId);
                System.out.println("Lifecycle complete. Shutting down...");
                // Quit program
                System.out.println("Shutting down JDA...");
                jda.shutdown();
                System.out.println("Shutdown complete. Exiting.");
                return;
            }

            // Monitor mode: displays live channel or user statistics
            if (args.length > 1 && ("--monitor".equalsIgnoreCase(args[0]) || "-m".equalsIgnoreCase(args[0]))) {
                String monitorType = args[1].toLowerCase();
                if (!monitorType.equals("channels") && !monitorType.equals("users")) {
                    System.err.println("ERROR: Monitor type must be 'channels' or 'users'");
                    jda.shutdown();
                    return;
                }
                
                // Find the first server to monitor (you can later extend this to accept server ID as arg)
                if (guildConfigs.isEmpty()) {
                    System.err.println("ERROR: No servers configured to monitor");
                    jda.shutdown();
                    return;
                }
                
                String serverId = guildConfigs.keySet().iterator().next();
                System.out.println("Starting monitor mode for '" + monitorType + "'...");
                System.out.println("Waiting for messages to be processed...");
                
                // Start the monitor in a background thread
                MonitorManager.startMonitor(serverId, monitorType);
                
                // Add shutdown hook for monitor
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\nStopping monitor...");
                    MonitorManager.stopMonitor();
                    StatsManager.shutdownGracefully();
                }));
                
                return; // Keep JDA running in the background
            }
            
            // 0. Synchronize channels on startup
            StatsManager.synchronizeChannels(jda);

            // 1. Kick off the background save timers
            StatsManager.startSaveTimers();

            // 2. Add a shutdown hook to save data if the bot is killed
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                StatsManager.shutdownGracefully();
            }));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads servers.json and populates the guildConfigs HashMap.
     * First checks the current working directory, then falls back to WORKING_DIRECTORY.
     */
    private static void loadConfigurations() {
        // Try current working directory first
        Path configPath = Path.of(System.getProperty("user.dir") + "/servers.json");
        
        // Fall back to WORKING_DIRECTORY if not found
        if (!Files.exists(configPath)) {
            configPath = Path.of(WORKING_DIRECTORY + "servers.json");
        }
        
        if (!Files.exists(configPath)) {
            System.err.println("WARNING: servers.json not found. (\"" + WORKING_DIRECTORY + " servers.json\") The bot will run, but with no active configs.");
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Gson gson = new Gson();
            ConfigData data = gson.fromJson(reader, ConfigData.class);

            if (data != null && data.servers != null) {
                for (ServerNode server : data.servers) {
                    // Map the Server ID directly to the Server object
                    guildConfigs.put(server.id, server);
                }
                System.out.println("Successfully loaded configuration for " + guildConfigs.size() + " servers.");
            }
        } catch (Exception e) {
            System.err.println("Failed to parse servers.json. Ensure the formatting is correct.");
            e.printStackTrace();
        }
    }
}