package com.selah;

/**
 * Test edge case separators that stubborn users might try
 */
public class EdgeCaseSeparatorTest {
    public static void main(String[] args) {
        String[] testCases = {
            "nig - ger",
            "nig - - ger",
            "nig.ger",
            "n:gger",
            "nig_ger",
            "nig/ger",
            "nig\\ger",
            "nig~ger",
            "nig;ger",
            "nig`ger",
            "n-i-g-g-e-r",
            "n.i.g.g.e.r",
            "n:i:g:g:e:r"
        };
        
        System.out.println("Testing edge case separators:\n");
        int passed = 0;
        int failed = 0;
        
        for (String testCase : testCases) {
            boolean isBanned = BannedWordScanner.isBannedWordPresent(testCase, "nigger");
            String status = isBanned ? "✓ CAUGHT" : "✗ MISSED";
            System.out.println(String.format("%-25s %s", "\"" + testCase + "\"", status));
            if (isBanned) {
                passed++;
            } else {
                failed++;
            }
        }
        
        System.out.println("\n" + passed + " passed, " + failed + " failed out of " + testCases.length);
        System.out.println("Pass rate: " + String.format("%.1f%%", (passed * 100.0 / testCases.length)));
    }
}
