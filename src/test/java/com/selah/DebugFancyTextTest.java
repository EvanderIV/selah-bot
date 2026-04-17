package com.selah;

public class DebugFancyTextTest {
    public static void main(String[] args) {
        // Specific failing test cases to debug
        String[] failingCases = {
                "𐌍𐌉ᏵᏵ𐌄𐌓",                     // Gothic + Cherokee
                "ɳιɠɠҽɾ",                       // IPA + Greek mix
                "꧁༺ 𝓷𝓲𝓰𝓰𝓮𝓻 ༻꧂",              // Mathematical bold script with decorations
                "ꪀ꠸ᧁᧁꫀ᥅",                     // Exotic script
                "𝓆𝓇𝒾𝓊𝓁𝓎 𝒾𝓏𝑒𝓀𝑜𝓋𝓈𝓀𝒾𝓉𝒽𝓃𝒾𝑔𝑔𝑒𝓇",  // Long math fancy text
                "★彡( ₦ł₲₲ɆⱤ )彡★",             // With symbols
                "𝔫𝔦𝔤𝔤𝔢𝔯",                     // Fraktur
                "𝕟𝕚𝕘𝕘𝕖𝕣",                     // Double-struck
                "𝓃𝒾𝑔𝑔𝑒𝓇",                     // Mix of fonts
                "ｎｉｇｇｅｒ",                     // Fullwidth
                "🌸ꗥ～ꗥ🌸 𝐧𝐢𝐠𝐠𝐞𝐫 🌸ꗥ～ꗥ🌸",  // Emoji decoration
                "◦•●❤♡ nigger ♡❤●•◦",          // Heart symbols
                "〜n∿i∿g∿g∿e∿r〜",                 // With joining chars
                "░n░i░g░g░e░r░",                 // Box drawing
                "n♥i♥g♥g♥e♥r",                   // Heart separators
                "n⊶i⊶g⊶g⊶e⊶r",                   // Special separators
                "n⋆i⋆g⋆g⋆e⋆r",                   // Star separators
                "n⨳i⨳g⨳g⨳e⨳r",                   // Mathematical operator
        };

        String bannedWord = "nigger";
        
        for (String testCase : failingCases) {
            // Show character codes
            StringBuilder codes = new StringBuilder();
            for (int i = 0; i < testCase.length(); i++) {
                char c = testCase.charAt(i);
                codes.append(String.format("U+%04X ", (int) c));
            }
            
            boolean detected = BannedWordScanner.isBannedWordPresent(testCase, bannedWord);
            String status = detected ? "✓ PASS" : "✗ FAIL";
            
            System.out.println(status + ": " + testCase);
            System.out.println("        Codes: " + codes);
            
            // Show normalized version
            String normalized = BannedWordScanner.normalizeForKeywordCheck(testCase);
            System.out.println("        Normalized: " + normalized);
            System.out.println();
        }
    }
}
