package com.selah;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import com.google.gson.Gson;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.Color;

public class App {

    public static boolean DEBUG_MODE = false;
    public static boolean DIET_MODE = false; // Set to true for faster checks (current message only, no context)
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
        public boolean word_filter;
        public boolean delete_filtered_messages;
        public boolean timeout_for_filtered_messages;
        public boolean slowmode_alerts;
        public String log_channel_id;
        public String alert_channel_id;
        public String warning_channel_id;
        public long alert_cooldown_seconds = 60; // Default: 60 seconds
        public int save_interval = 5;
        public String timeout_mode = "flat"; // Options: "flat", "factorial", "exponential"
        public long timeout_base_seconds = 60; // Base timeout in seconds (1 minute default)
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

        // Check for diet mode flag
        if (args.length > 0 && ("--diet".equalsIgnoreCase(args[0]) || "-diet".equalsIgnoreCase(args[0]))) {
            DIET_MODE = true;
            System.out.println("--- DIET MODE ENABLED: Checking current message only (no context) ---");
        }

        // Check for test mode flag
        if (args.length > 0 && ("--benchmark".equalsIgnoreCase(args[0]) || "-bm".equalsIgnoreCase(args[0]))) {
            try {
                KeywordManager.loadKeywords(); // Load keywords from JSON
                BenchmarkTestMode.runTestMode();
            } catch (Exception e) {
                System.err.println("ERROR in benchmark test mode:");
                e.printStackTrace();
                System.exit(1);
            }
            return;
        }

        if (args.length > 0 && ("--check".equalsIgnoreCase(args[0]) || "-c".equalsIgnoreCase(args[0]))) {
            if (args.length < 2) {
                System.err.println("ERROR: -c requires an input string. Usage: -c <quoted string>");
                return;
            }
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

            // Ensure server_stats directory exists
            ensureServerStats();

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

                for (StatsManager.MemberStats member : stats.members.values()) {
                    if (member.relations == null || member.relations.isEmpty()) {
                        if (DEBUG_MODE) {
                            System.out.println("No relations for member: " + member.memberName); // Debug log for empty relations
                        }
                        continue;
                    }

                    for (StatsManager.MemberRelation relation : member.relations) {
                        StatsManager.MemberStats target = stats.members.get(relation.targetMemberId);

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
        
        if (args.length > 0 && ("--ocr".equalsIgnoreCase(args[0]) || "-o".equalsIgnoreCase(args[0]))) {
            if (args.length < 2) {
                System.err.println("ERROR: -o requires an image URL. Usage: -o <image_url>");
                return;
            }
            System.out.println("--- OFFLINE: OCR MODE ---");

            String imageUrl = args[1];
            try {
                String extractedText = OCRProcessor.processImageFromUrl(imageUrl);
                System.out.println("Extracted Text:\n" + extractedText);
            } catch (BadImageException e) {
                System.err.println("Failed to process image: " + e.getMessage());
            }
            

            return;
        }

        if (args.length > 0 && ("--test-alerts".equalsIgnoreCase(args[0]) || "-t".equalsIgnoreCase(args[0]))) {
            System.out.println("--- TEST MODE: ALERT AND WARNING SYSTEM ---");
            handleTestMode(args);
            return;
        }

        if (args.length > 0 && ("--analyze-archive".equalsIgnoreCase(args[0]) || "-aa".equalsIgnoreCase(args[0]))) {
            if (args.length < 2) {
                System.err.println("ERROR: -aa requires an archive file path. Usage: -aa <archive_file_path>");
                return;
            }
            System.out.println("--- ARCHIVE ANALYSIS MODE ---");
            DEBUG_MODE = true;
            KeywordManager.loadKeywords();
            handleArchiveAnalysis(args);
            return;
        }

        // --- 3. Load and Parse the JSON ---
        loadConfigurations();
        KeywordManager.loadKeywords(); // Load keywords from JSON
        ensureServerStats(); // Ensure server_stats directory exists
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

            if (args.length > 0 && ("--archive".equalsIgnoreCase(args[0]) || "-a".equalsIgnoreCase(args[0]))) {
                if (args.length < 2) {
                    System.err.println("ERROR: -a requires a channel ID. Usage: -a <channel_id>");
                    jda.shutdown();
                    return;
                }
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
            if (args.length > 0 && ("--monitor".equalsIgnoreCase(args[0]) || "-m".equalsIgnoreCase(args[0]))) {
                if (args.length < 2) {
                    System.err.println("ERROR: -m requires a server ID and optional monitor type.");
                    System.err.println("Usage: -m <server_id> [monitor_type]");
                    System.err.println("Monitor types: 'channels', 'users', 'unified' (default)");
                    jda.shutdown();
                    return;
                }

                String serverId = args[1];
                String monitorType = args.length > 2 ? args[2].toLowerCase() : "unified";

                if (!monitorType.equals("channels") && !monitorType.equals("users") && !monitorType.equals("unified")) {
                    System.err.println("ERROR: Monitor type must be 'channels', 'users', or 'unified'. Got: '" + monitorType + "'");
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
                                "    \"word_filter\": true,\n" +
                                "    \"delete_filtered_messages\": true,\n" +
                                "    \"timeout_for_filtered_messages\": true,\n" +
                                "    \"slowmode_alerts\": false,\n" +
                                "    \"log_channel_id\": \"\",\n" +
                                "    \"alert_channel_id\": \"\",\n" +
                                "    \"warning_channel_id\": \"\",\n" +
                                "    \"alert_cooldown_seconds\": 60,\n" +
                                "    \"save_interval\": 5,\n" +
                                "    \"timeout_mode\": \"flat\",\n" +
                                "    \"timeout_base_seconds\": 60,\n" +
                                "    \"banned_words\": [\n" +
                                "      \"niggerlicious\",\n" +
                                "      \"niggalicious\",\n" +
                                "      \"neggerlicious\",\n" +
                                "      \"neggalicious\",\n" +
                                "      \"niggeraura\",\n" +
                                "      \"neggeraura\",\n" +
                                "      \"niggaaura\",\n" +
                                "      \"neggaaura\",\n" +
                                "      \"nigger\",\n" +
                                "      \"negger\",\n" +
                                "      \"niggar\",\n" +
                                "      \"neggar\",\n" +
                                "      \"nigga\",\n" +
                                "      \"negga\",\n" +
                                "      \"faggot\",\n" +
                                "      \"fagot\",\n" +
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
     * Ensures the server_stats directory exists, creating it if necessary.
     */
    private static void ensureServerStats() {
        try {
            Path statsFolder = Path.of(WORKING_DIRECTORY, "server_stats");
            if (!Files.exists(statsFolder)) {
                Files.createDirectories(statsFolder);
                System.out.println("Created server_stats folder at: " + statsFolder.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to ensure server_stats directory exists.");
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
            System.out.println("Alert Cooldown: " + serverConfig.config.alert_cooldown_seconds + " seconds");

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
                System.out.println("⚠️ Alert channel is not configured for this server.");
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
                System.out.println("⚠️ Warning channel is not configured for this server.");
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

    /**
     * Analyzes an archive file by parsing messages, calculating heat, and simulating channel heat.
     * Outputs statistics and an ASCII line graph of simulated channel heat over time.
     * 
     * @param args Command-line arguments. args[1] should be the path to the archive file.
     */
    private static void handleArchiveAnalysis(String[] args) {
        if (args.length < 2) {
            System.err.println("ERROR: Archive file path required. Usage: java -jar bot.jar -aa <archive_file_path>");
            return;
        }

        Path archivePath = Path.of(args[1]);
        if (!Files.exists(archivePath)) {
            System.err.println("ERROR: Archive file not found: " + archivePath.toAbsolutePath());
            return;
        }

        try {
            List<String> lines = Files.readAllLines(archivePath);
            List<Double> heatValues = new ArrayList<>();
            double simulatedChannelHeat = 0.0;
            double maxHeat = 0.0;

            System.out.println("Analyzing archive: " + archivePath.getFileName());
            System.out.println("Total messages: " + lines.size());
            System.out.println("Processing...\n");

            // Parse each message and calculate heat
            for (String line : lines) {
                // Parse format: [YYYY-MM-DD HH:MM:SS] username: message
                if (!line.contains("] ") || !line.contains(": ")) {
                    continue;
                }

                int closeIdx = line.indexOf("] ");
                int colonIdx = line.indexOf(": ");
                if (closeIdx == -1 || colonIdx == -1 || colonIdx <= closeIdx) {
                    continue;
                }

                String message = line.substring(colonIdx + 2);
                double messageHeat = ModerationListener.getHeatIndex(message);

                // Simulate channel heat using the same rolling average logic
                if (simulatedChannelHeat == 0.0) {
                    simulatedChannelHeat = messageHeat;
                } else {
                    if (messageHeat > simulatedChannelHeat) {
                        simulatedChannelHeat = (0.75 * messageHeat) + (0.25 * simulatedChannelHeat);
                    } else {
                        simulatedChannelHeat = (0.50 * simulatedChannelHeat) + (0.50 * messageHeat);
                    }
                }

                heatValues.add(simulatedChannelHeat);
                if (simulatedChannelHeat > maxHeat) {
                    maxHeat = simulatedChannelHeat;
                }
            }

            if (heatValues.isEmpty()) {
                System.err.println("ERROR: No valid messages found in archive.");
                return;
            }

            // Calculate statistics
            double avgHeat = heatValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double medianHeat = calculateMedian(heatValues);
            double stdDev = calculateStdDev(heatValues, avgHeat);
            double lowerQuartile = calculateQuartile(heatValues, 0.25);
            double upperQuartile = calculateQuartile(heatValues, 0.75);

            // Output statistics
            System.out.println("\n=== ARCHIVE ANALYSIS STATISTICS ===");
            System.out.println("Total Messages Analyzed: " + heatValues.size());
            System.out.println(String.format("Average Heat: %.4f", avgHeat));
            System.out.println(String.format("Median Heat: %.4f", medianHeat));
            System.out.println(String.format("Standard Deviation: %.4f", stdDev));
            System.out.println(String.format("Lower Quartile (Q1): %.4f", lowerQuartile));
            System.out.println(String.format("Upper Quartile (Q3): %.4f", upperQuartile));
            System.out.println(String.format("Record High Heat: %.4f", maxHeat));
            System.out.println(String.format("Interquartile Range: %.4f\n", upperQuartile - lowerQuartile));

            // Generate and display ASCII line graph
            System.out.println("=== SIMULATED CHANNEL HEAT GRAPH ===");
            displayLineGraph(heatValues, maxHeat);

        } catch (Exception e) {
            System.err.println("ERROR analyzing archive: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Displays an ASCII line graph of heat values over message count.
     */
    private static void displayLineGraph(List<Double> heatValues, double maxHeat) {
        int graphHeight = 20;
        int graphWidth = Math.min(100, heatValues.size());
        
        // Sample data if too many messages
        List<Double> sampledHeat = new ArrayList<>();
        if (heatValues.size() <= graphWidth) {
            sampledHeat = new ArrayList<>(heatValues);
        } else {
            int step = heatValues.size() / graphWidth;
            for (int i = 0; i < heatValues.size(); i += step) {
                sampledHeat.add(heatValues.get(i));
            }
        }

        // Create graph matrix
        char[][] graph = new char[graphHeight][sampledHeat.size()];
        for (int i = 0; i < graphHeight; i++) {
            for (int j = 0; j < sampledHeat.size(); j++) {
                graph[i][j] = ' ';
            }
        }

        // Plot points
        for (int i = 0; i < sampledHeat.size(); i++) {
            int row = (int) ((1.0 - (sampledHeat.get(i) / maxHeat)) * (graphHeight - 1));
            row = Math.max(0, Math.min(graphHeight - 1, row));
            graph[row][i] = '●';
        }

        // Display graph with axis labels
        System.out.println("Heat  │");
        for (int i = 0; i < graphHeight; i++) {
            if (i % (Math.max(1, graphHeight / 5)) == 0) {
                double heatLabel = maxHeat * (1.0 - (double) i / graphHeight);
                System.out.printf("%5.2f │", heatLabel);
            } else {
                System.out.print("      │");
            }
            
            for (int j = 0; j < sampledHeat.size(); j++) {
                System.out.print(graph[i][j]);
            }
            System.out.println();
        }

        // X-axis
        System.out.print("      └");
        for (int i = 0; i < sampledHeat.size(); i++) {
            System.out.print("─");
        }
        System.out.println();
        System.out.println("       0" + String.format("%" + (sampledHeat.size() - 2) + "s", (sampledHeat.size() - 1)) + " (Message #)");
    }

    /**
     * Calculate median of a list of values.
     */
    private static double calculateMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        
        if (sorted.size() % 2 == 0) {
            return (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0;
        } else {
            return sorted.get(sorted.size() / 2);
        }
    }

    /**
     * Calculate standard deviation of a list of values.
     */
    private static double calculateStdDev(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    /**
     * Calculate quartile (Q1=0.25, Q3=0.75) of a list of values.
     */
    private static double calculateQuartile(List<Double> values, double quartile) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        
        int index = (int) (quartile * (sorted.size() - 1));
        return sorted.get(index);
    }
}