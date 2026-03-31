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

    public static final String WORKING_DIRECTORY = "/home/evanm/Bots/selah-bot/";

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
        // --- 3. Load and Parse the JSON ---
        loadConfigurations();
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
                    // .addEventListeners(new ModerationListener()) 
                    .build()
                    .awaitReady();

            System.out.println("Selah Online. Logged in as " + jda.getSelfUser().getName());
            
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
     */
    private static void loadConfigurations() {
        Path configPath = Path.of(WORKING_DIRECTORY + "servers.json");
        
        if (!Files.exists(configPath)) {
            System.err.println("WARNING: servers.json not found. The bot will run, but with no active configs.");
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