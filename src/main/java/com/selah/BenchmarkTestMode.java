package com.selah;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
import com.google.gson.Gson;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import javax.security.auth.login.LoginException;

/**
 * Benchmark test mode for performance analysis of the banned word scanner.
 * Connects to Discord via JDA, waits for a message containing a random token,
 * then runs benchmark calculations with detailed timing information.
 */
public class BenchmarkTestMode {
    
    private static final Random RANDOM = new Random();
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static String testToken;
    private static JDA jda;
    
    public static void runTestMode() throws LoginException, InterruptedException {
        System.out.println("\n========== BENCHMARK TEST MODE ==========");
        System.out.println("Connecting to Discord...");
        System.out.println("Loading configurations and keywords...\n");
        
        // Load configurations just like normal bot
        System.out.println("[1/3] Loading server configurations...");
        loadConfigurations();
        System.out.println("[2/3] Loading banned words...");
        KeywordManager.loadKeywords();
        
        System.out.println("[3/3] Connecting to JDA...");
        if (KeywordManager.keywords.isEmpty()) {
            System.err.println("ERROR: No banned words were loaded!");
            System.exit(1);
        }
        
        // Generate token
        BenchmarkTestMode.testToken = generateToken();
        
        // Initialize JDA and register listener
        try {
            String discordToken = System.getenv("SELAH_DISCORD_TOKEN");
            if (discordToken == null) {
                System.err.println("ERROR: Please set the SELAH_DISCORD_TOKEN environment variable.");
                System.exit(1);
            }
            
            BenchmarkTestMode.jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new BenchmarkListener())
                    .build();
            
            // Wait for JDA to be ready
            BenchmarkTestMode.jda.awaitReady();
            
            System.out.println("\n✓ Connected to Discord as: " + BenchmarkTestMode.jda.getSelfUser().getName());
            System.out.println("✓ Keywords loaded: " + KeywordManager.keywords.size() + " words");
            System.out.println("✓ Ready to receive benchmark message.\n");
            
            System.out.println("========== WAITING FOR MESSAGE ==========");
            System.out.println("Generated token: " + BenchmarkTestMode.testToken);
            System.out.println("Send a Discord message containing: " + BenchmarkTestMode.testToken);
            System.out.println("Example: " + BenchmarkTestMode.testToken + " nigger");
            System.out.println("The bot will process only that message, then exit.\n");
            
        } catch (Exception e) {
            System.err.println("ERROR initializing JDA:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static class BenchmarkListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // Ignore bot messages
            if (event.getAuthor().isBot()) {
                return;
            }
            
            String content = event.getMessage().getContentRaw();
            
            // Check if message contains the token
            if (content.contains(BenchmarkTestMode.testToken)) {
                System.out.println("\n✓ Token message received from: " + event.getAuthor().getName());
                System.out.println("✓ Message content: " + content);
                System.out.println("✓ Running benchmark...\n");
                
                BenchmarkTestMode.runBenchmark(content, event.getGuild().getId());
                
                // Shutdown cleanly
                System.out.println("\nBenchmark complete. Shutting down...");
                BenchmarkTestMode.jda.shutdown();
                System.exit(0);
            }
        }
    }
    
    private static String generateToken() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
    
    private static void loadConfigurations() {
        String configFolder = "server_configs";
        Path configPath = Path.of(configFolder);
        
        if (!Files.exists(configPath)) {
            System.out.println("WARNING: " + configFolder + " directory not found.");
            return;
        }
        
        try {
            Files.list(configPath)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try (Reader reader = Files.newBufferedReader(p)) {
                            Gson gson = new Gson();
                            App.ServerNode server = gson.fromJson(reader, App.ServerNode.class);
                            if (server != null && server.id != null) {
                                App.guildConfigs.put(server.id, server);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to load config: " + p.getFileName());
                        }
                    });
            
            System.out.println("Loaded " + App.guildConfigs.size() + " server configurations.");
        } catch (Exception e) {
            System.err.println("Error loading configurations: " + e.getMessage());
        }
    }
    
    private static void runBenchmark(String message, String serverId) {
        System.out.println("\n========== BENCHMARK RESULTS ==========");
        System.out.println("Input message: " + message);
        System.out.println("Message length: " + message.length() + " characters");
        System.out.println("Diet mode: " + App.DIET_MODE + "\n");
        
        // Get banned words for this specific server (just like the normal bot does)
        App.ServerNode serverNode = App.guildConfigs.get(serverId);
        if (serverNode == null || serverNode.config.banned_words == null || serverNode.config.banned_words.isEmpty()) {
            System.out.println("ERROR: No banned words configured for this server!");
            return;
        }
        
        List<String> bannedWords = serverNode.config.banned_words;
        System.out.println("Testing against " + bannedWords.size() + " banned words configured for this server\n");
        
        // Replicate the exact logic from ModerationListener.checkBannedWordsInMessage
        // For messages >= 10 chars, skip context checks (major optimization)
        boolean shouldCheckContext = !App.DIET_MODE && message.length() < 10;
        
        System.out.println("========== CONFIGURATION ==========");
        System.out.println("Should check context: " + shouldCheckContext);
        System.out.println("(Context checks skipped for long messages to avoid excess API calls)\n");
        
        // BENCHMARK 1: Current method (individual checks)
        System.out.println("========== BENCHMARK 1: Individual Pattern Matching (CURRENT) ==========");
        long start = System.nanoTime();
        int matchCount1 = 0;
        List<String> matchedWords1 = new ArrayList<>();
        
        for (String bannedWord : bannedWords) {
            if (BannedWordScanner.isBannedWordPresent(message, bannedWord)) {
                matchCount1++;
                matchedWords1.add(bannedWord);
            }
        }
        long time1 = System.nanoTime() - start;
        System.out.printf("Time: %.4f ms%n", time1 / 1_000_000.0);
        System.out.printf("Average per word: %.4f ms%n", (time1 / 1_000_000.0) / bannedWords.size());
        System.out.println("Matches found: " + matchCount1);
        
        // BENCHMARK 2: Aho-Corasick algorithm (optimized)
        System.out.println("\n========== BENCHMARK 2: Aho-Corasick Algorithm (OPTIMIZED) ==========");
        start = System.nanoTime();
        AhoCorasickMatcher matcher = new AhoCorasickMatcher(bannedWords);
        long matcherBuildTime = System.nanoTime() - start;
        
        start = System.nanoTime();
        Set<String> matchedWords2 = matcher.findMatches(message);
        long matchTime = System.nanoTime() - start;
        long time2 = matcherBuildTime + matchTime;
        
        System.out.printf("Build time: %.4f ms%n", matcherBuildTime / 1_000_000.0);
        System.out.printf("Search time: %.4f ms%n", matchTime / 1_000_000.0);
        System.out.printf("Total time: %.4f ms%n", time2 / 1_000_000.0);
        System.out.println("Matches found: " + matchedWords2.size());
        
        // Performance comparison
        System.out.println("\n========== PERFORMANCE COMPARISON ==========");
        double speedup = (double) time1 / time2;
        System.out.printf("Speedup: %.2fx faster with Aho-Corasick%n", speedup);
        System.out.printf("Time saved: %.4f ms%n", (time1 - time2) / 1_000_000.0);
        
        // Results
        System.out.println("\n========== RESULTS ==========");
        System.out.println("Total matches found: " + matchCount1);
        if (matchCount1 > 0) {
            System.out.println("Matching words:");
            for (String word : matchedWords1) {
                System.out.println("  - " + word);
            }
        }
        
        System.out.println();
    }
}
