package com.selah;

import java.io.Reader;
import java.io.InputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import com.google.gson.Gson;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.Color;

import java.nio.file.StandardCopyOption;
import net.sourceforge.tess4j.Tesseract;
import java.util.Locale;
import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import java.awt.image.BufferedImage;
import java.io.File;

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

    private static final String SERVER_CONFIGS_FOLDER = "server_configs";

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
        public String alert_channel_id;
        public String warning_channel_id;
        public int save_interval = 5;
        public List<String> banned_words;
    }

    // --- 2. The Global Memory Store ---
    // A highly efficient map to store configs by Server ID
    public static final Map<String, ServerNode> guildConfigs = new HashMap<>();

    public static void main(String[] args) {

        // Check for debug flag
        if (args.length > 0 && ("--debug".equalsIgnoreCase(args[0]) || "-d".equalsIgnoreCase(args[0]))) {
            DEBUG_MODE = true;
            System.out.println("--- DEBUG MODE ENABLED ---");
        }

        if (args.length > 1 && ("--check".equalsIgnoreCase(args[0]) || "-c".equalsIgnoreCase(args[0]))) {
            DEBUG_MODE = true;
            KeywordManager.loadKeywords(); // Load keywords from JSON
            System.out.println("--- OFFLINE: THIS SESSION IS NOT RECORDED ---");
            ModerationListener.getHeatIndex(args[1]);
            return;
        }

        if (args.length > 0 && ("--relations".equalsIgnoreCase(args[0]) || "-r".equalsIgnoreCase(args[0]))) {
            System.out.println("--- OFFLINE: RELATIONSHIP EVALUATION MODE ---");

            // Load configurations before loading server stats
            loadConfigurations();

            // Load server stats
            StatsManager.loadServerStats();

            // Debug log to verify server stats loading
            if (DEBUG_MODE) {
                System.out.println("Loaded server stats for evaluation: " + StatsManager.liveStats.size() + " servers.");
            }

            // If a server ID is provided, filter to that server only
            String filterServerId = args.length > 1 ? args[1] : null;

            App.guildConfigs.forEach((serverId, serverNode) -> {
                if (filterServerId != null && !filterServerId.equals(serverId)) {
                    return; // Skip servers that don't match the filter
                }

                // Retrieve server stats for the current server
                StatsManager.ServerStats stats = StatsManager.liveStats.get(serverId);
                if (stats == null) {
                    if (DEBUG_MODE) {
                        System.out.println("No stats found for server: " + serverNode.name);
                    }
                    return; // Skip if no stats are available
                }

                System.out.println("\n--- BEGINNING RELATIONSHIP EVALUATION ---");

                if (DEBUG_MODE) {
                    System.out.println("Evaluating relationships for server: " + serverNode.name + " (ID: " + serverId + ")");
                    System.out.println("Members in server: " + stats.members.size()); // Debug log for members
                }

                for (StatsManager.MemberStats member : stats.members) {
                    if (member.relations == null || member.relations.isEmpty()) {
                        if (DEBUG_MODE) {
                            System.out.println("No relations for member: " + member.memberName); // Debug log for empty relations
                        }
                        continue;
                    }

                    for (StatsManager.MemberRelation relation : member.relations) {
                        StatsManager.MemberStats target = stats.members.stream()
                                .filter(m -> m.memberId.equals(relation.targetMemberId))
                                .findFirst()
                                .orElse(null);

                        if (target == null) {
                            if (DEBUG_MODE) {
                                System.out.println("Target not found for relation: " + relation.targetMemberId); // Debug log for missing target
                            }
                            continue;
                        }

                        double mutualAffection = target.relations.stream()
                                .filter(r -> r.targetMemberId.equals(member.memberId))
                                .map(r -> r.affection)
                                .findFirst()
                                .orElse(0.0);

                        if (DEBUG_MODE) {
                            System.out.println("Evaluating: " + member.memberName + " -> " + target.memberName + ", Affection: " + relation.affection + ", Mutual: " + mutualAffection); // Debug log for evaluation
                        }

                        if (relation.affection > 5 && mutualAffection > 5) {
                            System.out.println("High mutual affection: " + member.memberName + " <-> " + target.memberName);
                        } else if (relation.affection > 5 && mutualAffection <= 1) {
                            System.out.println("One-sided affection (parasocial): " + member.memberName + " -> " + target.memberName);
                        } else if (relation.affection < -5 || mutualAffection < -5) {
                            System.out.println("Negative affection: " + member.memberName + " <-> " + target.memberName);
                        }
                    }
                }
            });

            return;
        }
        
        if (args.length > 1 && ("--ocr".equalsIgnoreCase(args[0]) || "-o".equalsIgnoreCase(args[0]))) {
            System.out.println("--- OFFLINE: OCR MODE ---");

            String imageUrl = args[1];
            OCRProcessor ocrProcessor = new OCRProcessor();
            try {
                String extractedText = ocrProcessor.processImageFromUrl(imageUrl);
                System.out.println("Extracted Text:\n" + extractedText);
            } catch (BadImageException e) {
                System.err.println("Failed to process image: " + e.getMessage());
            }
            

            return;
        }

        if (args.length > 1 && ("--test-alerts".equalsIgnoreCase(args[0]) || "-t".equalsIgnoreCase(args[0]))) {
            System.out.println("--- TEST MODE: ALERT AND WARNING SYSTEM ---");
            handleTestMode(args);
            return;
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
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new ModerationListener()) 
                    .build()
                    .awaitReady();

            System.out.println("Selah Online. Logged in as " + jda.getSelfUser().getName());

            // Ensure server configs are up-to-date
            ensureServerConfigs(jda);

            if (args.length > 1 && ("archive".equalsIgnoreCase(args[0]) || "-a".equalsIgnoreCase(args[0]))) {
                String channelId = args[1];
                ArchiveManager.archiveChannel(jda, channelId);
                System.out.println("Lifecycle complete. Shutting down...");
                // Quit program
                System.out.println("Shutting down JDA...");
                jda.shutdown();
                System.out.println("Shutdown complete. Exiting.");
                return;
            }

            // 0. Synchronize channels on startup
            StatsManager.synchronizeChannels(jda);

            // 1. Kick off the background save timers
            StatsManager.startSaveTimers();

            // Monitor mode: displays live channel or user statistics
            if (args.length > 2 && ("--monitor".equalsIgnoreCase(args[0]) || "-m".equalsIgnoreCase(args[0]))) {
                String monitorType = args[1].toLowerCase();
                String serverId = args[2];

                if (!monitorType.equals("channels") && !monitorType.equals("users") && !monitorType.equals("unified")) {
                    System.err.println("ERROR: Monitor type must be 'channels', 'users', or 'unified'");
                    jda.shutdown();
                    return;
                }

                if (!guildConfigs.containsKey(serverId)) {
                    System.err.println("ERROR: No configuration found for server ID: " + serverId);
                    jda.shutdown();
                    return;
                }

                System.out.println("Starting monitor mode for server ID '" + serverId + "' and type '" + monitorType + "'...");
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
            
            // 2. Add a shutdown hook to save data if the bot is killed
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                StatsManager.shutdownGracefully();
            }));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads all JSON files in the server_configs folder and populates the guildConfigs HashMap.
     */
    private static void loadConfigurations() {
        Path configsFolder = Path.of(WORKING_DIRECTORY, SERVER_CONFIGS_FOLDER);

        // Ensure the server_configs folder exists
        if (!Files.exists(configsFolder)) {
            System.err.println("ERROR: server_configs folder does not exist. Please create it and add server configuration files.");
            return;
        }

        try {
            // Iterate through all JSON files in the server_configs folder
            Files.list(configsFolder)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(configFile -> {
                    try (Reader reader = Files.newBufferedReader(configFile)) {
                        Gson gson = new Gson();
                        ServerNode server = gson.fromJson(reader, ServerNode.class);

                        if (server != null && server.id != null) {
                            guildConfigs.put(server.id, server);
                            System.out.println("Loaded configuration for server: " + server.name);
                        } else {
                            System.err.println("Invalid configuration in file: " + configFile.getFileName());
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to load configuration from file: " + configFile.getFileName());
                        e.printStackTrace();
                    }
                });

            System.out.println("Successfully loaded configurations for " + guildConfigs.size() + " servers.");
        } catch (Exception e) {
            System.err.println("Failed to read server configurations from folder: " + configsFolder.toAbsolutePath());
            e.printStackTrace();
        }
    }

    private static void ensureServerConfigs(JDA jda) {
        try {
            // Create the server_configs folder if it doesn't exist
            Path configsFolder = Path.of(WORKING_DIRECTORY, SERVER_CONFIGS_FOLDER);
            if (!Files.exists(configsFolder)) {
                Files.createDirectories(configsFolder);
                System.out.println("Created server_configs folder at: " + configsFolder.toAbsolutePath());
            }

            // Iterate through all servers the bot is in
            jda.getGuilds().forEach(guild -> {
                Path serverConfigPath = configsFolder.resolve(guild.getId() + ".json");

                // If a config file for the server doesn't exist, generate one
                if (!Files.exists(serverConfigPath)) {
                    try {
                        String exampleConfig = "{\n" +
                                "  \"name\": \"" + guild.getName() + "\",\n" +
                                "  \"id\": \"" + guild.getId() + "\",\n" +
                                "  \"config\": {\n" +
                                "    \"save_interval\": 5,\n" +
                                "    \"enable_alerts\": true,\n" +
                                "    \"enable_warnings\": true,\n" +
                                "    \"slowmode_alerts\": false,\n" +
                                "    \"log_channel_id\": \"\",\n" +
                                "    \"banned_words\": [\n" +
                                "      \"nigger\",\n" +
                                "      \"nigga\",\n" +
                                "      \"negga\",\n" +
                                "      \"faggot\",\n" +
                                "      \"fag\"\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}";
                        Files.writeString(serverConfigPath, exampleConfig);
                        System.out.println("Generated config for server: " + guild.getName() + " at " + serverConfigPath.toAbsolutePath());
                    } catch (Exception e) {
                        System.err.println("Failed to generate config for server: " + guild.getName());
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to ensure server configs.");
            e.printStackTrace();
        }
    }

    /**
     * Test mode for alert and warning system.
     * Sends test alerts and warnings to configured channels, then exits.
     * Usage: java -jar bot.jar --test-alerts [serverId] [alertChannelId] [warningChannelId]
     * 
     * @param args Command-line arguments. args[1] is serverId, args[2] is alertChannelId, args[3] is warningChannelId
     */
    private static void handleTestMode(String[] args) {
        // Load configurations and keywords
        loadConfigurations();
        KeywordManager.loadKeywords();

        String token = System.getenv("SELAH_DISCORD_TOKEN");
        if (token == null) {
            System.err.println("ERROR: Please set the SELAH_DISCORD_TOKEN environment variable.");
            return;
        }

        try {
            // Initialize JDA for sending messages
            System.out.println("Connecting to Discord...");
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .build()
                    .awaitReady();

            System.out.println("Connected as " + jda.getSelfUser().getName());

            // Get server ID from arguments or use first available server
            String serverId = args.length > 1 ? args[1] : guildConfigs.keySet().stream().findFirst().orElse(null);
            
            if (serverId == null) {
                System.err.println("ERROR: No server ID provided and no servers configured.");
                jda.shutdown();
                return;
            }

            ServerNode serverConfig = guildConfigs.get(serverId);
            if (serverConfig == null) {
                System.err.println("ERROR: Server configuration not found for ID: " + serverId);
                jda.shutdown();
                return;
            }

            System.out.println("\n=== TESTING ALERT AND WARNING SYSTEM ===");
            System.out.println("Server: " + serverConfig.name + " (ID: " + serverId + ")");

            // Test Alert Channel
            if (serverConfig.config.alert_channel_id != null && !serverConfig.config.alert_channel_id.isEmpty()) {
                try {
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel alertChannel = 
                        jda.getTextChannelById(serverConfig.config.alert_channel_id);
                    
                    if (alertChannel != null) {
                        System.out.println("\nSending test alert to: #" + alertChannel.getName());
                        
                        EmbedBuilder alertEmbed = new EmbedBuilder()
                                .setColor(new Color(0, 255, 136)) // Green for test success
                                .setTitle("✅ [TEST] Alert System Verification", null)
                                .setDescription("This is a test message to verify the alert channel is configured correctly.")
                                .addField("Status", "Alert channel is properly configured!", true)
                                .addField("Timestamp", java.time.LocalDateTime.now().toString(), true)
                                .setFooter("Selah Test Mode", null)
                                .setTimestamp(java.time.Instant.now());
                        
                        alertChannel.sendMessageEmbeds(alertEmbed.build()).queue();
                        System.out.println("✓ Test alert sent successfully!");
                    } else {
                        System.err.println("✗ Alert channel ID is invalid or bot lacks permission: " + serverConfig.config.alert_channel_id);
                    }
                } catch (Exception e) {
                    System.err.println("✗ Failed to send test alert: " + e.getMessage());
                }
            } else {
                System.out.println("⚠ Alert channel is not configured for this server.");
            }

            // Test Warning Channel
            if (serverConfig.config.warning_channel_id != null && !serverConfig.config.warning_channel_id.isEmpty()) {
                try {
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel warningChannel = 
                        jda.getTextChannelById(serverConfig.config.warning_channel_id);
                    
                    if (warningChannel != null) {
                        System.out.println("\nSending test warning to: #" + warningChannel.getName());
                        
                        EmbedBuilder warningEmbed = new EmbedBuilder()
                                .setColor(new Color(0, 255, 136)) // Green for test success
                                .setTitle("✅ [TEST] Warning System Verification", null)
                                .setDescription("This is a test message to verify the warning channel is configured correctly.")
                                .addField("Status", "Warning channel is properly configured!", true)
                                .addField("Timestamp", java.time.LocalDateTime.now().toString(), true)
                                .setFooter("Selah Test Mode", null)
                                .setTimestamp(java.time.Instant.now());
                        
                        warningChannel.sendMessageEmbeds(warningEmbed.build()).queue();
                        System.out.println("✓ Test warning sent successfully!");
                    } else {
                        System.err.println("✗ Warning channel ID is invalid or bot lacks permission: " + serverConfig.config.warning_channel_id);
                    }
                } catch (Exception e) {
                    System.err.println("✗ Failed to send test warning: " + e.getMessage());
                }
            } else {
                System.out.println("⚠ Warning channel is not configured for this server.");
            }

            System.out.println("\n=== TEST COMPLETE ===");
            System.out.println("Shutting down...");
            
            // Allow time for messages to queue before shutdown
            Thread.sleep(2000);
            jda.shutdown();
            System.out.println("Shutdown complete.");

        } catch (Exception e) {
            System.err.println("ERROR during test mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
}