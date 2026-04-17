package com.selah;

/**
 * Debug test to check Unicode variant normalization
 */
public class UnicodeDebugTest {
    public static void main(String[] args) {
        String testString = "ȠĩǥĢẸℝ";
        System.out.println("Original string: " + testString);
        System.out.println("Hex codes:");
        for (char c : testString.toCharArray()) {
            System.out.println("  U+" + String.format("%04X", (int) c) + " -> " + c);
        }
        
        String normalized = BannedWordScanner.normalizeForKeywordCheck(testString);
        System.out.println("Normalized: " + normalized);
        System.out.println("Hex codes after normalization:");
        for (char c : normalized.toCharArray()) {
            System.out.println("  U+" + String.format("%04X", (int) c) + " -> " + c);
        }
        
        boolean isBanned = BannedWordScanner.isBannedWordPresent(testString, "nigger");
        System.out.println("Is banned (nigger): " + isBanned);
    }
}
