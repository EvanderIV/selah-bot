package com.selah;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstrates the theoretical "keyspace" of the BannedWordScanner.
 * Shows the total detection capacity when accounting for:
 * - Grammar variants (plural, possessive, gerund)
 * - Space insertion (2^n combinations)
 * - Smart conditional detection (character/symbol/separator checks)
 * - Unicode character normalization (200+ character mappings)
 * 
 * OPTIMIZATIONS:
 * - Conditional pass skipping: Only checks variants if necessary characters present
 * - Message normalization caching: Normalizes once, reuses for all checks
 * - Short message context checking: Only fetches history for messages <10 chars
 * - Lazy Base64/Binary detection: Only checks suspicious encoding patterns
 * 
 * Run with: java -cp "target/test-classes;target/classes" com.selah.KeyspaceAnalysisTest
 */
public class KeyspaceAnalysisTest {
    public static void main(String[] args) {
        // Banned words for keyspace analysis
        List<String> bannedWords = Arrays.asList(
            "niggerlicious", "niggalicious", "neggerlicious", "neggalicious",
            "niggeraura", "neggeraura", "niggaaura", "neggaaura",
            "nigger", "negger", "niggar", "neggar",
            "nigga", "negga", "faggot", "fagot", "fag"
        );
        
        System.out.println(BannedWordScanner.calculateKeyspace(bannedWords));
        
        // Print performance notes
        System.out.println("\n========== PERFORMANCE OPTIMIZATIONS ==========");
        System.out.println("✓ Conditional Pass Skipping: Skips unnecessary character-swap passes");
        System.out.println("✓ Message Normalization Cache: Normalizes message once, reuses result");
        System.out.println("✓ Short Message Context: History API only for <10 char messages");
        System.out.println("✓ Lazy Encoding Checks: Base64/Binary only for suspicious patterns");
        System.out.println("\nExpected Performance:");
        System.out.println("- Diet Mode (500-word string): ~0.1-0.2 seconds");
        System.out.println("- Regular Mode (500-word string): ~0.3-0.5 seconds + API delay");
        System.out.println("- Short Messages (<10 chars): Historical context API enabled");
    }
}
