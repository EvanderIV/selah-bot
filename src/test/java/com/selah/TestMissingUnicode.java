package com.selah;

public class TestMissingUnicode {
    public static void main(String[] args) {
        // Test the characters that weren't being caught
        String test1 = "𝓝𝕴𝒢𝔤𝙀𝕣"; // First variant  
        String test2 = "𝓝i𝒢𝔤𝙀𝕣"; // Second variant
        
        System.out.println("Test 1: " + test1);
        String normalized1 = BannedWordScanner.normalizeForKeywordCheck(test1);
        System.out.println("Normalized: " + normalized1);
        System.out.println("Length: " + normalized1.length());
        System.out.println("Chars: " + normalized1.chars().mapToObj(c -> String.format("%c", c)).reduce((a,b) -> a + " " + b).orElse(""));
        System.out.println();
        
        System.out.println("Test 2: " + test2);
        String normalized2 = BannedWordScanner.normalizeForKeywordCheck(test2);
        System.out.println("Normalized: " + normalized2);
        System.out.println("Length: " + normalized2.length());
        System.out.println("Chars: " + normalized2.chars().mapToObj(c -> String.format("%c", c)).reduce((a,b) -> a + " " + b).orElse(""));
        System.out.println();
        
        // Test against the actual banned words in the system
        String[] bannedWords = {"nigger", "nigga", "niggerlicious", "niggalicious", "niggeraura", "niggaaura", "niggar"};
        
        System.out.println("Testing against banned words:");
        for (String bannedWord : bannedWords) {
            boolean found1 = BannedWordScanner.isBannedWordPresent(test1, bannedWord);
            boolean found2 = BannedWordScanner.isBannedWordPresent(test2, bannedWord);
            System.out.println("Word '" + bannedWord + "' - Test1: " + found1 + ", Test2: " + found2);
        }
    }
}
