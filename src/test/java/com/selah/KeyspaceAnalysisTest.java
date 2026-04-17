package com.selah;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstrates the theoretical "keyspace" of the BannedWordScanner.
 * Shows the total detection capacity when accounting for:
 * - Grammar variants (plural, possessive, gerund)
 * - Space insertion (2^n combinations)
 * - Multi-pass detection (5 different detection passes)
 * - Unicode character normalization (200+ character mappings)
 * 
 * Run with: java -cp "target/test-classes;target/classes" com.selah.KeyspaceAnalysisTest
 */
public class KeyspaceAnalysisTest {
    public static void main(String[] args) {
        // Example banned words to analyze
        List<String> bannedWords = Arrays.asList(
            "niggeraura",
            "niggaaura",
            "neggaaura",
            "nigger",
            "nigga",
            "negga",
            "faggot",
            "fagot",
            "fag"
        );
        
        System.out.println(BannedWordScanner.calculateKeyspace(bannedWords));
    }
}
