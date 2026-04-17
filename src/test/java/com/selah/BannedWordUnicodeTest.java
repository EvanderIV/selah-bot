package com.selah;

import java.util.Arrays;
import java.util.List;

public class BannedWordUnicodeTest {
    public static void main(String[] args) {
        // Test cases for fancy Unicode versions of banned words
        List<String> testCases = Arrays.asList(
                // Decorated Text with Fancy Symbols
                "♥ﮩ٨ـﮩﮩ٨ـﮩﮩ ηιggєя ﮩﮩـ٨ﮩﮩـ٨ﮩ♥",
                "❂𐂷 ƝƖƓƓƸⱤ 𐂷❂",
                "ⲯ﹍︿﹍︿﹍ 𝚗𝚒𝚐𝚐𝚎𝚛 ﹍ⲯ﹍ⲯ﹍︿﹍☼",
                "❝𝐧𝐢𝐠𝐠𝐞𝐫❞",
                "ꓚ⌊⌋ 𝚗𝚒𝚐𝚐𝚎𝚛 ⌊⌋ꓛ",
                "▄︻デռɨɢɢɛʀ══━一",
                "╏╠══[𝍖𝍖𝍖 𝚗𝚒𝚐𝚐𝚎𝚛 𝍖𝍖𝍖]      💦",
                
                // Cherokee/Squiggle Text
                "ᑎIGGEᖇ",
                "𐌍𐌉ᏵᏵ𐌄𐌓",
                "ᏁᎥᎶᎶᏋᏒ",
                "ɳιɠɠҽɾ",
                "ռɨɢɢɛʀ",
                
                // Classic decorated with bars
                "▁▂▄▅▆▇█ ПIGGΣЯ █▇▆▅▄▂▁",
                "▞▞▞▞▞▖🅽🅸🅶🅶🅴🆁▝▞▞▞▞▞",
                "█▔╖ 𝚗𝚒𝚐𝚐𝚎𝚛 ╖▔█",
                
                // Fancy Script Fonts
                "꧁༺ 𝓷𝓲𝓰𝓰𝓮𝓻 ༻꧂",
                "─╤╦︻ ƝƖƓƓƸⱤ ︻╦╤─",
                
                // More Unicode variants
                "ꪀ꠸ᧁᧁꫀ᥅",
                "🌸ꗥ～ꗥ🌸 𝐧𝐢𝐠𝐠𝐞𝐫 🌸ꗥ～ꗥ🌸",
                
                // Mathematical/Stylish variants
                "𝓆𝓇𝒾𝓊𝓁𝓎 𝒾𝓏𝑒𝓀𝑜𝓋𝓈𝓀𝓲𝓉𝒽𝓃𝒾𝑔𝑔𝑒𝓇",
                "★彡( ₦ł₲₲ɆⱤ )彡★",
                "◤✞ 𝖓𝖎𝖌𝖌𝖊𝖗 ✞◥",
                "𒆜ռɨɢɢɛʀ 𒆜",
                "ᕚ( ภเﻮﻮєг )ᕘ",
                "𓂀 𝓃𝒾𝑔𝑔𝑒𝓇 𓂀",
                "𝐧𝐢𝐠𝐠𝐞𝐫🌈™",
                "🇺🇸 ɳιɠɠҽɾ 🇺🇸",
                "☆꧁✬◦°˚°◦. ռɨɢɢɛʀ .◦°˚°◦✬꧂☆",
                "꧁༺nigger ༻꧂",
                
                // Mathematical alphanumeric (already tested but included)
                "██▓▒­░⡷⠂𝚗𝚒𝚐𝚐𝚎𝚛⠐⢾░▒▓██",
                "一═デ︻ ηιggєя ︻デ═一",
                "🄝🄘🄖🄖🄔🄡",
                
                // Cool Stylish Fonts
                "𝐧𝐢𝐠𝐠𝐞𝐫",
                "𝗻𝗶𝗴𝗴𝗲𝗿",
                "𝘯𝘪𝘨𝘨𝘦𝘳",
                "𝑛𝑖𝑔𝑔𝑒𝑟",
                "𝒏𝒊𝒈𝒈𝒆𝒓",
                "𝙣𝙞𝙜𝙜𝙚𝙧",
                "𝚗𝚒𝚐𝚐𝚎𝚛",
                "ПIGGΣЯ",
                "ηιggєя",
                "𝕟𝕚𝕘𝕘𝕖𝕣",
                "𝔫𝔦𝔤𝔤𝔢𝔯",
                "𝖓𝖎𝖌𝖌𝖊𝖗",
                "𝓃𝒾𝑔𝑔𝑒𝓇",
                "𝓷𝓲𝓰𝓰𝓮𝓻",
                "ｎｉｇｇｅｒ",
                
                // Lunicode Text
                "ɴɪɢɢᴇʀ",
                "nıɓɓǝɹ",
                "ⓝⓘⓖⓖⓔⓡ",
                "🅝🅘🅖🅖🅔🅡",
                "n⃣ i⃣ g⃣ g⃣ e⃣ r⃣",
                "🄽🄸🄶🄶🄴🅁",
                "🅽🅸🅶🅶🅴🆁",
                
                // Squiggle Text (various)
                "ภเﻮﻮєг",
                
                // Symbolic Text
                "ñïggêr",
                "₦ł₲₲ɆⱤ",
                "几丨ᎶᎶ乇尺",
                "刀ﾉムム乇尺",
                "ᘉᓰᘜᘜᘿᖇ",
                "𐍀𐍊𐌾𐌾𐌴𐍂",
                
                // Freaky Text with combining marks
                "n҉i҉g҉g҉e҉r҉",
                "n̾i̾g̾g̾e̾r̾",
                "n͎i͎g͎g͎e͎r͎",
                "n͓̽i͓̽g͓̽g͓̽e͓̽r͓̽",
                
                // Underline and Strikethrough
                "n̶i̶g̶g̶e̶r̶",
                "n̵i̵g̵g̵e̵r̵",
                "n̴i̴g̴g̴e̴r̴",
                "n̷i̷g̷g̷e̷r̷",
                "𝚗̷𝚒̷𝚐̷𝚐̷𝚎̷𝚛̷",
                "n̲i̲g̲g̲e̲r̲",
                "n̳i̳g̳g̳e̳r̳",
                
                // Decorated variations
                "˜\"*°•.˜\"*°• nigger •°*\"˜.•°*\"˜",
                "▁ ▂ ▄ ▅ ▆ ▇ █ nigger █ ▇ ▆ ▅ ▄ ▂ ▁",
                
                // Joiner Text
                "〜n∿i∿g∿g∿e∿r〜",
                "░n░i░g░g░e░r░",
                "n♥i♥g♥g♥e♥r",
                "n⊶i⊶g⊶g⊶e⊶r",
                "n⋆i⋆g⋆g⋆e⋆r",
                "n⨳i⨳g⨳g⨳e⨳r",
                
                // Heart Decorated
                "ミ💖 nigger 💖彡",
                "❤♡❤♡❤♡❤♡❤♡❤♡❤♡ nigger ♡❤♡❤♡❤♡❤♡❤♡❤♡❤",
                "◦•●❤♡ nigger ♡❤●•◦",
                
                // Star Decorated
                "ミ★ nigger ★彡",
                "╰•★★ nigger ★★•╯",
                "°•.•°¤*✬.•°°• nigger °•°•.✬*¤°•.•°"
        );
        
        String bannedWord = "nigger";
        int passCount = 0;
        int failCount = 0;
        
        System.out.println("Testing " + testCases.size() + " fancy text variants...\n");
        
        for (String testCase : testCases) {
            boolean detected = BannedWordScanner.isBannedWordPresent(testCase, bannedWord);
            String status = detected ? "✓ PASS" : "✗ FAIL";
            
            if (detected) {
                passCount++;
            } else {
                failCount++;
            }
            
            System.out.println(status + ": " + testCase);
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Results: " + passCount + " passed, " + failCount + " failed out of " + testCases.size());
        System.out.println("Pass rate: " + String.format("%.1f%%", (100.0 * passCount / testCases.size())));
    }
}
