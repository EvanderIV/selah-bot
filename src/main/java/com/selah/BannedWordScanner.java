package com.selah;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles detection of banned words in messages.
 * Supports multiple evasion detection techniques:
 * - Character substitution (0→o, 1→i, @→a, etc.)
 * - Space-inserted variants (n i g g e r, ni gger, etc.)
 * - Unicode lookalikes (Cyrillic, Armenian, Greek, Latin Extended)
 * - Emoji representations (regional indicators, squared letters, keycaps)
 * - Base64 and binary encoding
 * - Character swaps (l↔i)
 * - Markdown formatting
 */
public class BannedWordScanner {

    /**
     * Static cache for compiled regex patterns to avoid recompilation.
     * Uses computeIfAbsent() for thread-safe lazy initialization.
     */
    private static final Map<String, Pattern> patternCache = new HashMap<>();

    /**
     * Retrieves or creates a cached Pattern for the given regex string.
     * Patterns are compiled once and reused for all subsequent calls.
     * @param regex The regex pattern string to compile.
     * @return A compiled Pattern object, either from cache or newly compiled.
     */
    private static Pattern getPattern(String regex) {
        return patternCache.computeIfAbsent(regex, Pattern::compile);
    }

    /**
     * Strips Discord markdown from a message.
     * Removes: **bold**, __bold__, *italic*, _italic_, `code`, ```code blocks```, ~~strikethrough~~, ||spoilers||, etc.
     * @param message The message content to strip markdown from.
     * @return The message with markdown removed.
     */
    public static String stripMarkdown(String message) {
        // Remove bold: **text** or __text__
        message = message.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        message = message.replaceAll("__(.+?)__", "$1");
        
        // Remove italic: *text* or _text_ (but be careful not to match bold already removed)
        message = message.replaceAll("\\*([^*]+)\\*", "$1");
        message = message.replaceAll("_([^_]+)_", "$1");
        
        // Remove strikethrough: ~~text~~
        message = message.replaceAll("~~(.+?)~~", "$1");
        
        // Remove spoilers: ||text||
        message = message.replaceAll("\\|\\|(.+?)\\|\\|", "$1");
        
        // Remove code blocks: ```code```
        message = message.replaceAll("```[\\s\\S]*?```", "");
        
        // Remove inline code: `code`
        message = message.replaceAll("`([^`]+)`", "$1");
        
        // Remove headers: # Header
        message = message.replaceAll("^#+\\s+", "");
        
        // Remove quote blocks: > quote
        message = message.replaceAll("^>\\s+", "");
        
        // Remove links but keep the text: [text](url)
        message = message.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        
        return message;
    }

    /**
     * Checks if a banned word or any mangled variant appears in the message.
     * Performs multiple passes with character swaps and symbol removal, but optimized
     * to skip unnecessary passes for normal messages:
     * - Pass 1: Normal (always)
     * - Pass 2: With 'l'→'i' swap (only if message contains both 'l' and target word has no 'i')
     * - Pass 3: With 'i'→'l' swap (only if message contains both 'i' and target word has no 'l')
     * - Pass 4: With σ/Σ replaced by 'e' (only if message contains these Greek chars)
     * - Pass 5: With decorative/symbol characters stripped (only if message contains symbols)
     * - Pass 6: With word separators stripped (only if message contains separators)
     * - Base64/Binary checks (only if all above fail and message looks suspicious)
     * @param messageContent The raw message content to check.
     * @param bannedWord The banned word to look for.
     * @return true if the word or any variant is found, false otherwise.
     */
    public static boolean isBannedWordPresent(String messageContent, String bannedWord) {
        // OPTIMIZATION: Normalize the message and banned word ONCE and reuse for all checks
        // This prevents expensive normalization from being repeated multiple times per banned word
        String normalizedMessage = normalizeForKeywordCheck(messageContent);
        String normalizedBanned = normalizeForKeywordCheck(bannedWord);
        
        // Check 1: Normal variant checking (always)
        if (checkBannedWordInMessageWithNormalized(normalizedMessage, normalizedBanned)) {
            return true;
        }
        
        // Optimization: Only do expensive character replacement passes if those characters exist in the message
        // This dramatically speeds up checking for normal, non-adversarial messages
        
        // Check 2: With 'l' replaced by 'i' (only if message has 'l' or 'L')
        if (messageContent.indexOf('l') >= 0 || messageContent.indexOf('L') >= 0) {
            String messageWithLtoI = messageContent.replace('l', 'i').replace('L', 'I');
            String normalizedLtoI = normalizeForKeywordCheck(messageWithLtoI);
            if (checkBannedWordInMessageWithNormalized(normalizedLtoI, normalizedBanned)) {
                return true;
            }
        }
        
        // Check 3: With 'i' replaced by 'l' (only if message has 'i' or 'I')
        if (messageContent.indexOf('i') >= 0 || messageContent.indexOf('I') >= 0) {
            String messageWithItoL = messageContent.replace('i', 'l').replace('I', 'L');
            String normalizedItoL = normalizeForKeywordCheck(messageWithItoL);
            if (checkBannedWordInMessageWithNormalized(normalizedItoL, normalizedBanned)) {
                return true;
            }
        }
        
        // Check 4: With σ/Σ replaced by 'e' (only if message contains Greek letters)
        if (messageContent.indexOf('σ') >= 0 || messageContent.indexOf('Σ') >= 0 || messageContent.indexOf('ς') >= 0) {
            String messageWithSigmaAsE = messageContent.replace('σ', 'e').replace('Σ', 'e').replace('ς', 'e');
            String normalizedSigma = normalizeForKeywordCheck(messageWithSigmaAsE);
            if (checkBannedWordInMessageWithNormalized(normalizedSigma, normalizedBanned)) {
                return true;
            }
        }
        
        // Check 5: With decorative/symbol characters stripped (only if message contains suspicious symbols)
        // Quick check: if message has any of these common evasion symbols, do the expensive regex
        if (messageContent.matches(".*[♥♦♪♫☆★✓✗✘✔✕✖☑☒☐☓❤💔💖💗💝💘💞💟░▒▓█⊶⋆⊨⊸✦✧✩✪✫✬✭✮✯☙❀❁❂❃❄✿∿～].*")) {
            String messageWithoutSymbols = messageContent
                    .replaceAll("[♥♦♪♫☆★✓✗✘✔✕✖☑☒☐☓❤💔💖💗💝💘💞💟]", "")
                    .replaceAll("[░▒▓█▄▅▆▇█▉▊▋▌▍▎▏|║─═╭╮╯╰┌┐┘└├┤┬┴┼]", "")
                    .replaceAll("[⊶⋆⨳⊹⊸✦✧✩✪✫✬✭✮✯☙❀❁❂❃❄✿]", "")
                    .replaceAll("[～∿⊸〜═──┈┉┊┋┣┫┳┻╋]", " ");
            String normalizedNoSymbols = normalizeForKeywordCheck(messageWithoutSymbols);
            if (checkBannedWordInMessageWithNormalized(normalizedNoSymbols, normalizedBanned)) {
                return true;
            }
        }
        
        // Check 6: With common word separators stripped (only if message contains separator chars)
        if (messageContent.matches(".*[\\-._:;/\\\\~`].*")) {
            String messageWithoutSeparators = messageContent.replaceAll("[\\-._:;/\\\\~`]", "");
            String normalizedNoSeparators = normalizeForKeywordCheck(messageWithoutSeparators);
            if (checkBannedWordInMessageWithNormalized(normalizedNoSeparators, normalizedBanned)) {
                return true;
            }
        }
        
        // Base64/Binary checks are expensive and rarely needed for normal messages
        // Only attempt if message looks suspicious (high entropy, unusual patterns)
        // and previous checks all failed
        if (looksLikeSuspiciousEncoding(messageContent)) {
            if (containsBannedWordInBase64(messageContent, bannedWord)) {
                return true;
            }
            if (containsBannedWordInBinary(messageContent, bannedWord)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Quick heuristic to detect if a message might contain suspicious encoding.
     * Returns true only if message has patterns suggesting Base64 or binary encoding.
     */
    private static boolean looksLikeSuspiciousEncoding(String messageContent) {
        // Skip Base64/Binary checks for short normal messages
        if (messageContent.length() < 50) {
            return false;
        }
        
        // Only check if message looks Base64-ish: lots of alphanumeric + /+ =
        if (messageContent.matches(".*[A-Za-z0-9+/]{20,}={0,2}.*")) {
            return true;
        }
        
        // Only check if message looks binary-ish: alternating 0s and 1s
        if (messageContent.matches(".*[01]{30,}.*")) {
            return true;
        }
        
        return false;
    }

    /**
     * OPTIMIZED: Performs banned word checking with pre-normalized strings.
     * This method is called from isBannedWordPresent to reuse normalized message/banned word.
     * @param normalizedMessage The pre-normalized message content.
     * @param normalizedBanned The pre-normalized banned word.
     * @return true if the word or any variant is found, false otherwise.
     */
    private static boolean checkBannedWordInMessageWithNormalized(String normalizedMessage, String normalizedBanned) {
        // Generate grammatical variants (plural, possessive, etc.) of the banned word
        List<String> grammarVariants = generateGrammarVariants(normalizedBanned);
        
        // For each grammar variant, generate space-inserted variants and check
        for (String grammarVariant : grammarVariants) {
            List<String> spaceVariants = generateSpaceVariants(grammarVariant);
            
            // Check if any variant appears in the normalized message with word boundaries
            for (String variant : spaceVariants) {
                // Build a regex pattern that respects word boundaries
                // Replace spaces with \s+ to allow multiple/variable spaces between parts
                String escapedVariant = Pattern.quote(variant);
                String pattern = "\\b" + escapedVariant.replaceAll(" ", "\\\\E\\\\s+\\\\Q") + "\\b";
                
                if (getPattern(pattern).matcher(normalizedMessage).find()) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Attempts to decode Base64 strings within the message and check for banned words.
     * @param messageContent The message content to scan.
     * @param bannedWord The banned word to look for.
     * @return true if the banned word is found in any decoded Base64 string, false otherwise.
     */
    private static boolean containsBannedWordInBase64(String messageContent, String bannedWord) {
        String normalizedBanned = normalizeForKeywordCheck(bannedWord);
        List<String> spaceVariants = generateSpaceVariants(normalizedBanned);
        
        // Extract potential Base64 strings
        String base64Pattern = "[A-Za-z0-9+/]{4,}={0,2}";
        java.util.regex.Matcher matcher = getPattern(base64Pattern).matcher(messageContent);
        
        while (matcher.find()) {
            String potentialBase64 = matcher.group();
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(potentialBase64);
                String decodedString = new String(decodedBytes);
                String normalizedDecoded = normalizeForKeywordCheck(decodedString);
                
                for (String variant : spaceVariants) {
                    String escapedVariant = Pattern.quote(variant);
                    String variantPattern = "\\b" + escapedVariant.replaceAll(" ", "\\\\E\\\\s+\\\\Q") + "\\b";
                    if (getPattern(variantPattern).matcher(normalizedDecoded).find()) {
                        return true;
                    }
                }
            } catch (IllegalArgumentException e) {
                // Not valid Base64, skip
            }
        }
        
        return false;
    }

    /**
     * Attempts to decode binary representations within the message and check for banned words.
     * @param messageContent The message content to scan.
     * @param bannedWord The banned word to look for.
     * @return true if the banned word is found in any decoded binary string, false otherwise.
     */
    private static boolean containsBannedWordInBinary(String messageContent, String bannedWord) {
        String normalizedBanned = normalizeForKeywordCheck(bannedWord);
        List<String> spaceVariants = generateSpaceVariants(normalizedBanned);
        
        // Look for binary patterns: sequences of 0s and 1s
        String binaryPattern = "[01]{8}(?:[\\s\\-,]*[01]{8})*";
        java.util.regex.Matcher matcher = getPattern(binaryPattern).matcher(messageContent);
        
        while (matcher.find()) {
            String potentialBinary = matcher.group();
            try {
                // Remove separators and convert binary to ASCII
                String cleanBinary = potentialBinary.replaceAll("[\\s\\-,]", "");
                StringBuilder decoded = new StringBuilder();
                for (int i = 0; i < cleanBinary.length(); i += 8) {
                    if (i + 8 <= cleanBinary.length()) {
                        String byteStr = cleanBinary.substring(i, i + 8);
                        int charCode = Integer.parseInt(byteStr, 2);
                        // Only include printable ASCII characters
                        if (charCode >= 32 && charCode <= 126) {
                            decoded.append((char) charCode);
                        }
                    }
                }
                
                String normalizedDecoded = normalizeForKeywordCheck(decoded.toString());
                for (String variant : spaceVariants) {
                    String escapedVariant = Pattern.quote(variant);
                    String variantPattern = "\\b" + escapedVariant.replaceAll(" ", "\\\\E\\\\s+\\\\Q") + "\\b";
                    if (getPattern(variantPattern).matcher(normalizedDecoded).find()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Not valid binary, skip
            }
        }
        
        return false;
    }

    /**
     * Normalizes a message for keyword checking.
     * Handles: emoji replacement, Unicode lookalikes, character substitutions, diacriticals.
     * @param message The message to normalize.
     * @return Normalized message suitable for keyword matching.
     */
    public static String normalizeForKeywordCheck(String message) {
        // Handle emoji replacements BEFORE stripping non-BMP characters
        // Regional indicator emojis (🇳 🇮 🇬 🇬 🇪 🇷 etc.)
        String cleaned = message
                .replace("🇦", "a").replace("🇧", "b").replace("🇨", "c")
                .replace("🇩", "d").replace("🇪", "e").replace("🇫", "f")
                .replace("🇬", "g").replace("🇭", "h").replace("🇮", "i")
                .replace("🇯", "j").replace("🇰", "k").replace("🇱", "l")
                .replace("🇲", "m").replace("🇳", "n").replace("🇴", "o")
                .replace("🇵", "p").replace("🇶", "q").replace("🇷", "r")
                .replace("🇸", "s").replace("🇹", "t").replace("🇺", "u")
                .replace("🇻", "v").replace("🇼", "w").replace("🇽", "x")
                .replace("🇾", "y").replace("🇿", "z");
        
        // Handle keycap emoji representations
        cleaned = cleaned
                .replace("🄝", "n").replace("🄞", "o").replace("🄟", "p")
                .replace("🄠", "q").replace("🄡", "r")
                .replace("🄢", "s").replace("🄣", "t").replace("🄤", "u")
                .replace("🄥", "v").replace("🄦", "w").replace("🄧", "x")
                .replace("🄨", "y").replace("🄩", "z")
                .replace("🄐", "a").replace("🄑", "b").replace("🄒", "c")
                .replace("🄓", "d").replace("🄔", "e").replace("🄕", "f")
                .replace("🄖", "g").replace("🄗", "h").replace("🄘", "i")
                .replace("🄙", "j").replace("🄚", "k").replace("🄛", "l")
                .replace("🄜", "m")
                // Squared letter symbols
                .replace("🅰", "a").replace("🅱", "b").replace("🅲", "c")
                .replace("🅳", "d").replace("🅴", "e").replace("🅵", "f")
                .replace("🅶", "g").replace("🅷", "h").replace("🅸", "i")
                .replace("🅹", "j").replace("🅺", "k").replace("🅻", "l")
                .replace("🅼", "m").replace("🅽", "n").replace("🅾", "o")
                .replace("🅿", "p").replace("🆀", "q").replace("🆁", "r")
                .replace("🆂", "s").replace("🆃", "t").replace("🆄", "u")
                .replace("🆅", "v").replace("🆆", "w").replace("🆇", "x")
                .replace("🆈", "y").replace("🆉", "z");
        
        // Handle mathematical alphanumeric symbols (outside BMP, must be before non-BMP strip)
        // Mathematical Monospace: 𝚊-𝚣 (U+1D5DE-𝚣), 𝚀-𝚉 (U+1D5F7)
        cleaned = cleaned
                .replace("𝚊", "a").replace("𝚋", "b").replace("𝚌", "c")
                .replace("𝚍", "d").replace("𝚎", "e").replace("𝚏", "f")
                .replace("𝚐", "g").replace("𝚑", "h").replace("𝚒", "i")
                .replace("𝚓", "j").replace("𝚔", "k").replace("𝚕", "l")
                .replace("𝚖", "m").replace("𝚗", "n").replace("𝚘", "o")
                .replace("𝚙", "p").replace("𝚚", "q").replace("𝚛", "r")
                .replace("𝚜", "s").replace("𝚝", "t").replace("𝚞", "u")
                .replace("𝚟", "v").replace("𝚠", "w").replace("𝚡", "x")
                .replace("𝚢", "y").replace("𝚣", "z")
                .replace("𝚀", "A").replace("𝚁", "B").replace("𝚂", "C")
                .replace("𝚃", "D").replace("𝚄", "E").replace("𝚅", "F")
                .replace("𝚆", "G").replace("𝚇", "H").replace("𝚈", "I")
                .replace("𝚉", "J").replace("𝚊", "K").replace("𝚛", "L")
                // Gothic script (ancient Germanic): 𐌀-𐌃 mapping to a,b,g,d etc
                .replace("𐌀", "a").replace("𐌁", "b").replace("𐌂", "g")
                .replace("𐌃", "d").replace("𐌄", "e").replace("𐌅", "q")
                .replace("𐌆", "z").replace("𐌇", "h").replace("𐌈", "h")
                .replace("𐌉", "i").replace("𐌊", "j").replace("𐌋", "l")
                .replace("𐌌", "m").replace("𐌍", "n").replace("𐌎", "n")
                .replace("𐌏", "o").replace("𐌐", "p").replace("𐌑", "f")
                .replace("𐌒", "r").replace("𐌓", "s").replace("𐌔", "t")
                .replace("𐌕", "u").replace("𐌖", "f").replace("𐌗", "h")
                .replace("𐌘", "w").replace("𐌙", "o").replace("𐌚", "o")
                .replace("𐌛", "y").replace("𐌰", "a").replace("𐌱", "b")
                .replace("𐌲", "g").replace("𐌳", "d").replace("𐌴", "e")
                .replace("𐌵", "q").replace("𐌶", "z").replace("𐌷", "h")
                .replace("𐌸", "h").replace("𐌹", "i").replace("𐌺", "j")
                .replace("𐌻", "l").replace("𐌼", "m").replace("𐌽", "n")
                .replace("𐌾", "j").replace("𐌿", "u").replace("𐍀", "p")
                .replace("𐍁", "f").replace("𐍂", "r").replace("𐍃", "s")
                .replace("𐍄", "t").replace("𐍅", "u").replace("𐍆", "f")
                .replace("𐍇", "h").replace("𐍈", "w")
                // Mathematical Bold: 𝐚-𝐳
                .replace("𝐚", "a").replace("𝐛", "b").replace("𝐜", "c")
                .replace("𝐝", "d").replace("𝐞", "e").replace("𝐟", "f")
                .replace("𝐠", "g").replace("𝐡", "h").replace("𝐢", "i")
                .replace("𝐣", "j").replace("𝐤", "k").replace("𝐥", "l")
                .replace("𝐦", "m").replace("𝐧", "n").replace("𝐨", "o")
                .replace("𝐩", "p").replace("𝐪", "q").replace("𝐫", "r")
                .replace("𝐬", "s").replace("𝐭", "t").replace("𝐮", "u")
                .replace("𝐯", "v").replace("𝐰", "w").replace("𝐱", "x")
                .replace("𝐲", "y").replace("𝐳", "z")
                .replace("𝐀", "A").replace("𝐁", "B").replace("𝐂", "C")
                .replace("𝐃", "D").replace("𝐄", "E").replace("𝐅", "F")
                .replace("𝐆", "G").replace("𝐇", "H").replace("𝐈", "I")
                .replace("𝐉", "J").replace("𝐊", "K").replace("𝐋", "L")
                .replace("𝐌", "M").replace("𝐍", "N").replace("𝐎", "O")
                .replace("𝐏", "P").replace("𝐐", "Q").replace("𝐑", "R")
                .replace("𝐒", "S").replace("𝐓", "T").replace("𝐔", "U")
                .replace("𝐕", "V").replace("𝐖", "W").replace("𝐗", "X")
                .replace("𝐘", "Y").replace("𝐙", "Z")
                // Mathematical Bold Italic: 𝒂-𝒛
                .replace("𝒂", "a").replace("𝒃", "b").replace("𝒄", "c")
                .replace("𝒅", "d").replace("𝒆", "e").replace("𝒇", "f")
                .replace("𝒈", "g").replace("𝒉", "h").replace("𝒊", "i")
                .replace("𝒋", "j").replace("𝒌", "k").replace("𝒍", "l")
                .replace("𝒎", "m").replace("𝒏", "n").replace("𝒐", "o")
                .replace("𝒑", "p").replace("𝒒", "q").replace("𝒓", "r")
                .replace("𝒔", "s").replace("𝒕", "t").replace("𝒖", "u")
                .replace("𝒗", "v").replace("𝒘", "w").replace("𝒙", "x")
                .replace("𝒚", "y").replace("𝒛", "z")
                // Mathematical Sans-Serif Bold: 𝗮-𝗯
                .replace("𝗮", "a").replace("𝗯", "b").replace("𝗰", "c")
                .replace("𝗱", "d").replace("𝗲", "e").replace("𝗳", "f")
                .replace("𝗴", "g").replace("𝗵", "h").replace("𝗶", "i")
                .replace("𝗷", "j").replace("𝗸", "k").replace("𝗹", "l")
                .replace("𝗺", "m").replace("𝗻", "n").replace("𝗼", "o")
                .replace("𝗽", "p").replace("𝗾", "q").replace("𝗿", "r")
                .replace("𝘀", "s").replace("𝘁", "t").replace("𝘂", "u")
                .replace("𝘃", "v").replace("𝘄", "w").replace("𝘅", "x")
                .replace("𝘆", "y").replace("𝘇", "z")
                // Mathematical Italic: 𝘢-𝘻
                .replace("𝘢", "a").replace("𝘣", "b").replace("𝘤", "c")
                .replace("𝘥", "d").replace("𝘦", "e").replace("𝘧", "f")
                .replace("𝘨", "g").replace("𝘩", "h").replace("𝘪", "i")
                .replace("𝘫", "j").replace("𝘬", "k").replace("𝘭", "l")
                .replace("𝘮", "m").replace("𝘯", "n").replace("𝘰", "o")
                .replace("𝘱", "p").replace("𝘲", "q").replace("𝘳", "r")
                .replace("𝘴", "s").replace("𝘵", "t").replace("𝘶", "u")
                .replace("𝘷", "v").replace("𝘸", "w").replace("𝘹", "x")
                .replace("𝘺", "y").replace("𝘻", "z")
                // Mathematical Bold Italic: 𝙖-𝙯
                .replace("𝙖", "a").replace("𝙗", "b").replace("𝙘", "c")
                .replace("𝙙", "d").replace("𝙚", "e").replace("𝙛", "f")
                .replace("𝙜", "g").replace("𝙝", "h").replace("𝙞", "i")
                .replace("𝙟", "j").replace("𝙠", "k").replace("𝙡", "l")
                .replace("𝙢", "m").replace("𝙣", "n").replace("𝙤", "o")
                .replace("𝙥", "p").replace("𝙦", "q").replace("𝙧", "r")
                .replace("𝙨", "s").replace("𝙩", "t").replace("𝙪", "u")
                .replace("𝙫", "v").replace("𝙬", "w").replace("𝙭", "x")
                .replace("𝙮", "y").replace("𝙯", "z")
                // Mathematical Double-Struck (Blackboard Bold): 𝕒-𝕫
                .replace("𝕒", "a").replace("𝕓", "b").replace("𝕔", "c")
                .replace("𝕕", "d").replace("𝕖", "e").replace("𝕗", "f")
                .replace("𝕘", "g").replace("𝕙", "h").replace("𝕚", "i")
                .replace("𝕛", "j").replace("𝕜", "k").replace("𝕝", "l")
                .replace("𝕞", "m").replace("𝕟", "n").replace("𝕠", "o")
                .replace("𝕡", "p").replace("𝕢", "q").replace("𝕣", "r")
                .replace("𝕤", "s").replace("𝕥", "t").replace("𝕦", "u")
                .replace("𝕧", "v").replace("𝕨", "w").replace("𝕩", "x")
                .replace("𝕪", "y").replace("𝕫", "z")
                .replace("𝔸", "A").replace("𝔹", "B").replace("𝔻", "D")
                .replace("𝔼", "E").replace("𝔽", "F").replace("𝔾", "G")
                .replace("𝔸", "A").replace("𝕴", "I").replace("𝔻", "D")
                .replace("ℝ", "r").replace("ℂ", "c").replace("ℍ", "h")
                .replace("ℕ", "n").replace("ℚ", "q").replace("ℤ", "z")
                // Mathematical Fraktur (German style): 𝔞-𝔷
                .replace("𝔞", "a").replace("𝔟", "b").replace("𝔠", "c")
                .replace("𝔡", "d").replace("𝔢", "e").replace("𝔣", "f")
                .replace("𝔤", "g").replace("𝔥", "h").replace("𝔦", "i")
                .replace("𝔧", "j").replace("𝔨", "k").replace("𝔩", "l")
                .replace("𝔪", "m").replace("𝔫", "n").replace("𝔬", "o")
                .replace("𝔭", "p").replace("𝔮", "q").replace("𝔯", "r")
                .replace("𝔰", "s").replace("𝔱", "t").replace("𝔲", "u")
                .replace("𝔳", "v").replace("𝔴", "w").replace("𝔵", "x")
                .replace("𝔶", "y").replace("𝔷", "z")
                // Mathematical Script (Cursive): 𝒶-𝒿
                .replace("𝒶", "a").replace("𝒷", "b").replace("𝒸", "c")
                .replace("𝒹", "d").replace("𝒻", "f").replace("𝒽", "h")
                .replace("𝒾", "i").replace("𝒿", "j").replace("𝓀", "k")
                .replace("𝓁", "l").replace("𝓂", "m").replace("𝓃", "n")
                .replace("𝓊", "u").replace("𝓋", "v").replace("𝓌", "w")
                .replace("𝓍", "x").replace("𝓎", "y").replace("𝓏", "z")
                // Mathematical Bold Script: 𝓪-𝔃
                .replace("𝓪", "a").replace("𝓫", "b").replace("𝓬", "c")
                .replace("𝓭", "d").replace("𝓮", "e").replace("𝓯", "f")
                .replace("𝓰", "g").replace("𝓱", "h").replace("𝓲", "i")
                .replace("𝓳", "j").replace("𝓴", "k").replace("𝓵", "l")
                .replace("𝓶", "m").replace("𝓷", "n").replace("𝓸", "o")
                .replace("𝓹", "p").replace("𝓺", "q").replace("𝓻", "r")
                .replace("𝓼", "s").replace("𝓽", "t").replace("𝓾", "u")
                .replace("𝓿", "v").replace("𝔀", "w").replace("𝔁", "x")
                .replace("𝔂", "y").replace("𝔃", "z")
                // Mathematical Sans-Serif Italic: 𝘼-𝙕
                .replace("𝘼", "A").replace("𝘽", "B").replace("𝘾", "C")
                .replace("𝘿", "D").replace("𝙀", "E").replace("𝙁", "F")
                .replace("𝙂", "G").replace("𝙃", "H").replace("𝙄", "I")
                .replace("𝙅", "J").replace("𝙆", "K").replace("𝙇", "L")
                .replace("𝙈", "M").replace("𝙉", "N").replace("𝙊", "O")
                .replace("𝙋", "P").replace("𝙌", "Q").replace("𝙍", "R")
                .replace("𝙎", "S").replace("𝙏", "T").replace("𝙐", "U")
                .replace("𝙑", "V").replace("𝙒", "W").replace("𝙓", "X")
                .replace("𝙔", "Y").replace("𝙕", "Z")
                // Canadian Syllabics: ᑎ (NI), ᖇ (RE), etc.
                .replace("ᑎ", "n").replace("ᑓ", "n").replace("ᑕ", "n")
                .replace("ᑖ", "n").replace("ᑎ", "n").replace("ᖇ", "r")
                .replace("ᐃ", "i").replace("ᐄ", "i").replace("ᐅ", "i")
                .replace("ᕓ", "r").replace("ᕗ", "r")
                // Gothic script: 𐌍-𐌓 
                .replace("𐌍", "n").replace("𐌎", "n").replace("𐌉", "i")
                .replace("𐌰", "a").replace("𐌱", "b").replace("𐌲", "g")
                .replace("𐌳", "d").replace("𐌴", "e").replace("𐌵", "q")
                .replace("𐌶", "z").replace("𐌷", "h").replace("𐌸", "h")
                .replace("𐌹", "i").replace("𐌺", "j").replace("𐌻", "l")
                .replace("𐌼", "m").replace("𐌽", "n").replace("𐌾", "j")
                .replace("𐌿", "u").replace("𐍀", "p").replace("𐍁", "f")
                .replace("𐍂", "r").replace("𐍃", "s").replace("𐍄", "t")
                // Hangul Jamo (Korean): 
                .replace("ᄀ", "g").replace("ᄁ", "g").replace("ᄂ", "n")
                .replace("ᄃ", "d").replace("ᄄ", "d").replace("ᄅ", "r")
                .replace("ᄆ", "m").replace("ᄇ", "b").replace("ᄈ", "b")
                .replace("ᄉ", "s").replace("ᄊ", "s").replace("ᄋ", "o")
                .replace("ᄌ", "j").replace("ᄍ", "j").replace("ᄎ", "c")
                .replace("ᄏ", "k").replace("ᄐ", "t").replace("ᄑ", "p")
                .replace("ᄒ", "h")
                // Additional IPA (International Phonetic Alphabet) characters:
                .replace("ɴ", "n").replace("ʀ", "r").replace("ʁ", "r")
                .replace("ɳ", "n").replace("ɾ", "r").replace("ɽ", "r")
                .replace("ɶ", "o").replace("ə", "e").replace("ɛ", "e")
                .replace("ɜ", "e").replace("ɝ", "e").replace("ɞ", "e")
                .replace("ɢ", "g").replace("ɠ", "g").replace("ʂ", "s")
                .replace("ʃ", "s").replace("ʄ", "j").replace("ʅ", "r")
                // Lisu script (Li): ꓲ, ꓰ, ꓣ, ꓧ, ꓰ, ꓰ
                .replace("ꓲ", "i").replace("ꓱ", "i").replace("ꓰ", "a")
                .replace("ꓣ", "g").replace("ꓧ", "n").replace("ꓞ", "r")
                .replace("ꓕ", "e").replace("ꓔ", "e")
                // Tibetan script (common characters that look like n, r, etc):
                .replace("ཎ", "n").replace("ཀ", "k").replace("ག", "g")
                .replace("ང", "n").replace("ཅ", "c").replace("ཆ", "c")
                .replace("ཇ", "j").replace("ཉ", "n").replace("ཊ", "n")
                .replace("ཏ", "t").replace("ཐ", "t").replace("ད", "d")
                .replace("ན", "n").replace("པ", "p").replace("ཕ", "p")
                .replace("བ", "b").replace("མ", "m").replace("ཙ", "z")
                .replace("ཚ", "z").replace("ཛ", "z").replace("ཝ", "w")
                .replace("ཞ", "r").replace("ཟ", "z").replace("འ", "a")
                .replace("ཡ", "y").replace("ར", "r").replace("ལ", "l")
                .replace("ས", "s").replace("ཤ", "s").replace("ཥ", "s")
                .replace("ཨ", "a").replace("ི", "i").replace("ུ", "u")
                .replace("ེ", "e").replace("ོ", "o").replace("ཱ", "a")
                // Thai script variants (occasional Unicode normalization failures):
                .replace("ภ", "n").replace("เ", "e").replace("ﻮ", "r")
                .replace("ั", "a").replace("ิ", "i").replace("ึ", "u")
                .replace("ุ", "u").replace("์", "a")
                // Additional rare/exotic IPA and symbols:
                .replace("ɒ", "o").replace("ʊ", "u").replace("ʌ", "u")
                .replace("ɔ", "o").replace("ə", "e").replace("ɚ", "e")
                .replace("ʃ", "s").replace("ʒ", "z").replace("ʃ", "s")
                .replace("ɪ", "i").replace("ʎ", "l").replace("ɲ", "n")
                .replace("ŋ", "n").replace("ɣ", "g").replace("ʝ", "j")
                // More IPA consonants:
                .replace("ɡ", "g").replace("ɟ", "j").replace("ɢ", "g")
                .replace("ɠ", "g").replace("ɥ", "h").replace("ɦ", "h")
                .replace("ɧ", "h").replace("ɨ", "i").replace("ɪ", "i")
                .replace("ɬ", "l").replace("ɭ", "l").replace("ɮ", "l")
                .replace("ɯ", "u").replace("ɰ", "m").replace("ɱ", "m")
                .replace("ɲ", "n").replace("ɳ", "n").replace("ɴ", "n")
                .replace("ɵ", "o").replace("ɶ", "o").replace("ɸ", "f")
                .replace("ɹ", "r").replace("ɺ", "r").replace("ɻ", "r")
                .replace("ɼ", "r").replace("ɽ", "r").replace("ɾ", "r")
                .replace("ɿ", "r").replace("ʀ", "r").replace("ʁ", "r")
                .replace("ʂ", "s").replace("ʃ", "s").replace("ʄ", "j")
                .replace("ʅ", "r").replace("ʆ", "s").replace("ʇ", "t")
                .replace("ʈ", "t").replace("ʉ", "u").replace("ʊ", "u")
                .replace("ʋ", "v").replace("ʌ", "v").replace("ʍ", "w")
                .replace("ʎ", "l").replace("ʏ", "y").replace("ʐ", "z")
                .replace("ʑ", "z").replace("ʒ", "z").replace("ʓ", "z")
                // More Cyrillic variants:
                .replace("ҁ", "j").replace("ғ", "f").replace("ғ", "f")
                .replace("ҕ", "g").replace("җ", "j").replace("ҙ", "z")
                .replace("қ", "k").replace("ҙ", "z").replace("ҡ", "k")
                .replace("ң", "n").replace("ҥ", "n").replace("ҧ", "p")
                .replace("ҩ", "s").replace("ҫ", "s").replace("ҭ", "t")
                .replace("ҳ", "h").replace("ҵ", "c").replace("ҷ", "c")
                .replace("ҹ", "j").replace("һ", "h").replace("ҽ", "e")
                .replace("ҿ", "g")
                // Superscript/subscript variants:
                .replace("ⁿ", "n").replace("ʳ", "r").replace("ˢ", "s")
                .replace("ᵍ", "g").replace("ᶦ", "i").replace("ᵢ", "i")
                .replace("ₑ", "e").replace("ᵉ", "e");
        
        // Handle Cherokee script characters (outside BMP, must be before non-BMP strip)
        // Cherokee characters that resemble Latin letters
        cleaned = cleaned
                .replace("Ꭰ", "a").replace("ꭰ", "a")  // Cherokee A
                .replace("Ꭱ", "e").replace("ꭱ", "e")  // Cherokee E
                .replace("Ꭲ", "i").replace("ꭲ", "i")  // Cherokee I
                .replace("Ꭳ", "o").replace("ꭳ", "o")  // Cherokee O
                .replace("Ꭴ", "u").replace("ꭴ", "u")  // Cherokee U
                .replace("Ꭵ", "i").replace("ꭵ", "i")  // Cherokee U (alternate)
                .replace("Ꭶ", "g").replace("ꭶ", "g")  // Cherokee GA
                .replace("Ꭷ", "g").replace("ꭷ", "g")  // Cherokee KA
                .replace("Ꭸ", "g").replace("ꭸ", "g")  // Cherokee GE
                .replace("Ꭹ", "g").replace("ꭹ", "g")  // Cherokee GI
                .replace("Ꭺ", "g").replace("ꭺ", "g")  // Cherokee GO
                .replace("Ꭻ", "g").replace("ꭻ", "g")  // Cherokee GU
                .replace("Ꭼ", "g").replace("ꭼ", "g")  // Cherokee GV
                .replace("Ꭽ", "g").replace("ꭽ", "g")  // Cherokee HA
                .replace("Ꭾ", "h").replace("ꭾ", "h")  // Cherokee HE
                .replace("Ꭿ", "h").replace("ꭿ", "h")  // Cherokee HI
                .replace("Ꮀ", "h").replace("ꮀ", "h")  // Cherokee HO
                .replace("Ꮁ", "h").replace("ꮁ", "h")  // Cherokee HU
                .replace("Ꮂ", "h").replace("ꮂ", "h")  // Cherokee HV
                .replace("Ꮃ", "l").replace("ꮃ", "l")  // Cherokee LA
                .replace("Ꮄ", "l").replace("ꮄ", "l")  // Cherokee LE
                .replace("Ꮅ", "l").replace("ꮅ", "l")  // Cherokee LI
                .replace("Ꮆ", "g").replace("ꮆ", "g")  // Cherokee LO (but used as G)
                .replace("Ꮇ", "l").replace("ꮇ", "l")  // Cherokee LU
                .replace("Ꮈ", "l").replace("ꮈ", "l")  // Cherokee LV
                .replace("Ꮉ", "m").replace("ꮉ", "m")  // Cherokee MA
                .replace("Ꮊ", "m").replace("ꮊ", "m")  // Cherokee ME
                .replace("Ꮋ", "m").replace("ꮋ", "m")  // Cherokee MI
                .replace("Ꮌ", "m").replace("ꮌ", "m")  // Cherokee MO
                .replace("Ꮍ", "m").replace("ꮍ", "m")  // Cherokee MU
                .replace("Ꮎ", "o").replace("ꮎ", "o")  // Cherokee NA
                .replace("Ꮏ", "n").replace("ꮏ", "n")  // Cherokee NE
                .replace("Ꮐ", "n").replace("ꮐ", "n")  // Cherokee NI
                .replace("Ꮑ", "n").replace("ꮑ", "n")  // Cherokee NO
                .replace("Ꮒ", "n").replace("ꮒ", "n")  // Cherokee NU
                .replace("Ꮓ", "n").replace("ꮓ", "n")  // Cherokee NV
                .replace("Ꮔ", "p").replace("ꮔ", "p")  // Cherokee PA
                .replace("Ꮕ", "p").replace("ꮕ", "p")  // Cherokee PE
                .replace("Ꮖ", "p").replace("ꮖ", "p")  // Cherokee PI
                .replace("Ꮗ", "p").replace("ꮗ", "p")  // Cherokee PO
                .replace("Ꮘ", "p").replace("ꮘ", "p")  // Cherokee PU
                .replace("Ꮙ", "p").replace("ꮙ", "p")  // Cherokee PV
                .replace("Ꮚ", "g").replace("ꮚ", "g")  // Cherokee QUA
                .replace("Ꮛ", "e").replace("ꮛ", "e")  // Cherokee QUE
                .replace("Ꮜ", "q").replace("ꮜ", "q")  // Cherokee QUI
                .replace("Ꮝ", "s").replace("ꮝ", "s")  // Cherokee QUO
                .replace("Ꮞ", "q").replace("ꮞ", "q")  // Cherokee SA
                .replace("Ꮟ", "s").replace("ꮟ", "s")  // Cherokee SE
                .replace("Ꮠ", "s").replace("ꮠ", "s")  // Cherokee SI
                .replace("Ꮡ", "s").replace("ꮡ", "s")  // Cherokee SO
                .replace("Ꮢ", "r").replace("ꮢ", "r")  // Cherokee SU
                .replace("Ꮣ", "r").replace("ꮣ", "r")  // Cherokee TA
                .replace("Ꮤ", "t").replace("ꮤ", "t")  // Cherokee TE
                .replace("Ꮥ", "t").replace("ꮥ", "t")  // Cherokee TI
                .replace("Ꮦ", "t").replace("ꮦ", "t")  // Cherokee TO
                .replace("Ꮧ", "t").replace("ꮧ", "t")  // Cherokee TU
                .replace("Ꮨ", "t").replace("ꮨ", "t")  // Cherokee TV
                .replace("Ꮩ", "w").replace("ꮩ", "w")  // Cherokee WA
                .replace("Ꮪ", "w").replace("ꮪ", "w")  // Cherokee WE
                .replace("Ꮫ", "w").replace("ꮫ", "w")  // Cherokee WI
                .replace("Ꮬ", "w").replace("ꮬ", "w")  // Cherokee WO
                .replace("Ꮭ", "w").replace("ꮭ", "w")  // Cherokee WU
                .replace("Ꮮ", "w").replace("ꮮ", "w")  // Cherokee WV
                .replace("Ꮯ", "y").replace("ꮯ", "y")  // Cherokee YA
                .replace("Ꮰ", "y").replace("ꮰ", "y")  // Cherokee YE
                .replace("Ꮱ", "y").replace("ꮱ", "y")  // Cherokee YI
                .replace("Ꮲ", "z").replace("ꮲ", "z")  // Cherokee YO
                .replace("Ꮳ", "z").replace("ꮳ", "z")  // Cherokee YU
                .replace("Ꮴ", "z").replace("ꮴ", "z")  // Cherokee YV
                .replace("Ꮵ", "z").replace("ꮵ", "z")  // Cherokee ZA
                .replace("Ꮶ", "z").replace("ꮶ", "z")  // Cherokee ZE
                .replace("Ꮷ", "z").replace("ꮷ", "z")  // Cherokee ZI
                .replace("Ꮸ", "z").replace("ꮸ", "z")  // Cherokee ZO
                .replace("Ꮹ", "z").replace("ꮹ", "z")  // Cherokee ZU
                .replace("Ꮺ", "z").replace("ꮺ", "z")  // Cherokee ZV
                .replace("Ꮻ", "z").replace("ꮻ", "z")  // Cherokee ZH
                .replace("Ꮼ", "z").replace("ꮼ", "z")  // Cherokee TH
                .replace("Ꮽ", "z").replace("ꮽ", "z")  // Cherokee GHN
                .replace("Ꮾ", "z").replace("ꮾ", "z")  // Cherokee KN
                .replace("Ꮿ", "z").replace("ꮿ", "z"); // Cherokee PS
        
        // Strip non-BMP characters we don't care about
        cleaned = cleaned.replaceAll("[^\u0000-\uFFFF]", "");
        
        // Remove zero-width characters
        cleaned = cleaned.replaceAll("[\u200B-\u200D\uFEFF]", "");
        
        // Map lookalike characters from various scripts
        // Latin Extended characters
        cleaned = cleaned
                .replace('Ɲ', 'n').replace('ɲ', 'n').replace('Ɩ', 'i').replace('ɩ', 'i')
                .replace('Ɠ', 'g').replace('ɠ', 'g').replace('Ƹ', 'e').replace('ƹ', 'e')
                .replace('Ɽ', 'r').replace('ɽ', 'r').replace('Ə', 'e').replace('ə', 'e')
                .replace('Ø', 'o').replace('ø', 'o').replace('Ɔ', 'c').replace('ɔ', 'c')
                .replace('Ʃ', 's').replace('ʃ', 's').replace('Ƙ', 'k').replace('ƙ', 'k')
                .replace('Ƥ', 'p').replace('ƥ', 'p').replace('Ɓ', 'b').replace('ɓ', 'b')
                .replace('Ɖ', 'd').replace('ɖ', 'd').replace('Ɗ', 'd').replace('ɗ', 'd')
                .replace('Ƭ', 't').replace('ƭ', 't').replace('Ʇ', 't').replace('ʇ', 't')
                .replace('Ʌ', 'v').replace('ʌ', 'v').replace('Ʊ', 'u').replace('ʊ', 'u')
                // Cyrillic
                .replace('А', 'a').replace('а', 'a').replace('О', 'o').replace('о', 'o')
                .replace('П', 'n').replace('п', 'n') // Cyrillic P looks like 'n'
                .replace('Р', 'p').replace('р', 'p').replace('С', 'c').replace('с', 'c')
                .replace('Е', 'e').replace('е', 'e').replace('Н', 'h').replace('н', 'h')
                .replace('В', 'b').replace('в', 'b').replace('Х', 'x').replace('х', 'x')
                .replace('М', 'm').replace('м', 'm').replace('Т', 't').replace('т', 't')
                .replace('К', 'k').replace('к', 'k').replace('Г', 'g').replace('г', 'g')
                .replace('Л', 'l').replace('л', 'l').replace('У', 'y').replace('у', 'y')
                .replace('Ы', 'y').replace('ы', 'y').replace('З', 'z').replace('з', 'z')
                .replace('Я', 'r').replace('я', 'r') // Cyrillic Ya looks like 'r'
                .replace('є', 'e').replace('Є', 'e') // Cyrillic ie (Ukrainian)
                // Armenian
                .replace('ա', 'a').replace('Ա', 'a').replace('ե', 'e').replace('Ե', 'e')
                .replace('ի', 'i').replace('Ի', 'r').replace('ո', 'o').replace('Ո', 'o')
                .replace('ւ', 'u').replace('Ւ', 'u').replace('ռ', 'n').replace('Ռ', 'n')
                .replace('ս', 's').replace('Ս', 's').replace('գ', 'g').replace('Գ', 'g')
                .replace('տ', 't').replace('Տ', 't').replace('կ', 'k').replace('Կ', 'k')
                .replace('ր', 'r').replace('Ր', 'r').replace('բ', 'b').replace('Բ', 'b')
                // Ge'ez (Ethiopic) script
                .replace('ኸ', 'n').replace('ሸ', 's').replace('ስ', 's').replace('ር', 'r')
                .replace('ሐ', 'h').replace('ሓ', 'h').replace('ሑ', 'h').replace('ሒ', 'h')
                .replace('ኢ', 'i').replace('ኣ', 'a').replace('ኤ', 'e').replace('እ', 'o')
                .replace('ኦ', 'o').replace('ኧ', 'a').replace('ወ', 'w').replace('ዪ', 'i')
                // Greek
                .replace('α', 'a').replace('Α', 'a').replace('ν', 'n').replace('Ν', 'n')
                .replace('π', 'n').replace('Π', 'n') // Greek pi looks like 'n'
                .replace('ο', 'o').replace('Ο', 'o').replace('ρ', 'p').replace('Ρ', 'p')
                .replace('σ', 's').replace('ς', 's').replace('Σ', 's') // Greek sigma (all forms)
                .replace('τ', 't').replace('Τ', 't').replace('γ', 'g').replace('Γ', 'g')
                .replace('ε', 'e').replace('Ε', 'e').replace('ί', 'i').replace('Ί', 'i')
                .replace('ι', 'i').replace('Ι', 'i') // Greek iota (without tonos)
                .replace('κ', 'k').replace('Κ', 'k').replace('λ', 'l').replace('Λ', 'l')
                .replace('β', 'b').replace('Β', 'b').replace('ζ', 'z').replace('Ζ', 'z')
                .replace('η', 'n').replace('Η', 'n') // Greek eta looks like 'n' in some fonts
                // Digit to letter mappings (common evasion techniques)
                .replace('0', 'o') // zero → 'o' (catches "nig0er")
                .replace('5', 's') // five → 's' (catches "nig5er")
                .replace('1', 'i') // one → 'i' (catches "n1gger")
                // Additional Cyrillic that map to Latin letters
                .replace('Ι', 'i').replace('И', 'i') // Cyrillic I
                .replace('Й', 'y').replace('й', 'y') // Cyrillic short I (looks like Y)
                .replace('Ф', 'f').replace('ф', 'f') // Cyrillic F (Cyrillic Fe)
                .replace('Д', 'a').replace('д', 'a') // Cyrillic D (looks like 'a' when used as evasion)
                .replace('Ь', 'b').replace('ь', 'b') // Cyrillic soft sign (could confuse with B)
                .replace('Ъ', 'b').replace('ъ', 'b') // Cyrillic hard sign (could confuse with B)
                .replace('З', 'z').replace('з', 'z'); // Cyrillic Z (already had some mappings)
        
        // Apply Unicode NFKD normalization
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFKD);
        
        // Remove diacritical marks
        normalized = normalized.replaceAll("\\p{M}", "");
        
        // Additional normalization for accented characters and extended Latin variants
        normalized = normalized
                // Latin Extended-A variants (U+0100-017F) and related
                .replace('\u0220', 'n').replace('\u019E', 'n').replace('\u1D0B', 'd').replace('\u1D0C', 'd') // D with hook/dot variations and decomposed forms
                .replace('\u0129', 'i').replace('\u0128', 'i') // I with tilde
                .replace('\u01E5', 'g').replace('\u01E4', 'g') // G with stroke
                .replace('\u0122', 'g').replace('\u0123', 'g') // G with cedilla
                .replace('\u1EB8', 'e').replace('\u1EB9', 'e') // E with dot below
                .replace('\u1EBC', 'e').replace('\u1EBD', 'e') // E with tilde
                // Standard accents
                .replace('á', 'a').replace('à', 'a').replace('ä', 'a').replace('â', 'a')
                .replace('é', 'e').replace('è', 'e').replace('ë', 'e').replace('ê', 'e')
                .replace('í', 'i').replace('ì', 'i').replace('ï', 'i').replace('î', 'i')
                .replace('ó', 'o').replace('ò', 'o').replace('ö', 'o').replace('ô', 'o')
                .replace('ú', 'u').replace('ù', 'u').replace('ü', 'u').replace('û', 'u')
                .replace('ý', 'y').replace('ỳ', 'y').replace('ÿ', 'y')
                .replace('ñ', 'n').replace('ń', 'n')
                .replace('Ṅ', 'n').replace('ṅ', 'n').replace('Ṇ', 'n').replace('ṇ', 'n') // N with dot above/below
                .replace('Ṉ', 'n').replace('ṉ', 'n').replace('Ṋ', 'n').replace('ṋ', 'n') // N with dot below/macron
                .replace('ⓝ', 'n').replace('⒩', 'n') // Circled and parenthesized n/N
                .replace('ℕ', 'n') // Mathematical Double-Struck N
                .replace('₦', 'n') // Naira sign (lookalike)
                .replace('ℵ', 'n') // Hebrew Aleph (looks like N)
                .replace('സ', 'n').replace('ന', 'n') // Malayalam characters
                .replace('൩', 'n') // Malayalam digit
                // Greek eta and variants (all look like 'n')
                .replace('η', 'n').replace('Η', 'n')
                .replace('ἠ', 'n').replace('ἡ', 'n').replace('ἢ', 'n').replace('ἣ', 'n')
                .replace('ἤ', 'n').replace('ἥ', 'n').replace('ἦ', 'n').replace('ἧ', 'n')
                .replace('ὴ', 'n').replace('ή', 'n')
                .replace('ᾐ', 'n').replace('ᾑ', 'n').replace('ᾒ', 'n').replace('ᾓ', 'n')
                .replace('ᾔ', 'n').replace('ᾕ', 'n').replace('ᾖ', 'n').replace('ᾗ', 'n')
                .replace('ῂ', 'n').replace('ῃ', 'n').replace('ῄ', 'n')
                .replace('ῆ', 'n').replace('ῇ', 'n')
                .replace('ç', 'c').replace('č', 'c')
                .replace('ř', 'r').replace('ŕ', 'r').replace('Ṙ', 'r').replace('ṙ', 'r') // R with dot above
                .replace('Ṛ', 'r').replace('ṛ', 'r').replace('Ṝ', 'r').replace('ṝ', 'r') // R with dot below
                .replace('Ṟ', 'r').replace('ṟ', 'r') // R with dot below and macron
                .replace('ⓡ', 'r').replace('⒭', 'r') // Circled and parenthesized r
                .replace('ℛ', 'r').replace('ℜ', 'r').replace('ℝ', 'r').replace('℟', 'r') // Mathematical R variants
                .replace('š', 's').replace('ş', 's')
                .replace('ť', 't').replace('ţ', 't')
                .replace('ž', 'z').replace('ź', 'z')
                // G/g variants - extensive coverage
                .replace('ⓖ', 'g').replace('⒢', 'g') // Circled and parenthesized g/G
                .replace('❡', 'g') // Symbol variant
                .replace('ḡ', 'g').replace('Ḡ', 'g') // G with macron
                .replace('ℊ', 'g') // Mathematical script g
                // I/i variants - extensive coverage
                .replace('ⓘ', 'i').replace('⒤', 'i') // Circled and parenthesized i
                .replace('ї', 'i').replace('Ї', 'i') // Cyrillic yi
                .replace('유', 'i') // Korean character
                .replace('ḭ', 'i').replace('Ḭ', 'i') // I with macron below
                .replace('ḯ', 'i').replace('Ḯ', 'i') // I with diaeresis and acute
                .replace('ỉ', 'i').replace('Ỉ', 'i') // I with hook above
                .replace('ị', 'i').replace('Ị', 'i') // I with dot below
                .replace('ℐ', 'i') // Mathematical italic I
                // Greek iota and variants (all lowercase forms)
                .replace('ἰ', 'i').replace('ἱ', 'i').replace('ἲ', 'i').replace('ἳ', 'i')
                .replace('ἴ', 'i').replace('ἵ', 'i').replace('ἶ', 'i').replace('ἷ', 'i')
                .replace('ῐ', 'i').replace('ῑ', 'i').replace('ῒ', 'i').replace('ΐ', 'i')
                .replace('ῖ', 'i').replace('ῗ', 'i').replace('ὶ', 'i').replace('ί', 'i')
                // Greek Iota and variants (uppercase forms)
                .replace('Ἰ', 'i').replace('Ἱ', 'i').replace('Ἲ', 'i').replace('Ἳ', 'i')
                .replace('Ἴ', 'i').replace('Ἵ', 'i').replace('Ἶ', 'i').replace('Ἷ', 'i')
                .replace('Ῐ', 'i').replace('Ῑ', 'i').replace('Ὶ', 'i').replace('Ί', 'i')
                // E/e variants - comprehensive coverage
                .replace('ⓔ', 'e').replace('⒠', 'e') // Circled and parenthesized e
                .replace('ℯ', 'e').replace('ℰ', 'e').replace('ℇ', 'e') // Mathematical e variants
                .replace('∊', 'e').replace('∃', 'e') // Set theory symbols (lookalikes)
                .replace('€', 'e') // Euro sign (looks like E)
                // e variants with diacritics
                .replace('ḕ', 'e').replace('Ḕ', 'e') // E with macron and grave
                .replace('ḗ', 'e').replace('Ḗ', 'e') // E with macron and acute
                .replace('ḙ', 'e').replace('Ḙ', 'e') // E with circumflex below
                .replace('ḛ', 'e').replace('Ḛ', 'e') // E with tilde below
                .replace('ḝ', 'e').replace('Ḝ', 'e') // E with cedilla
                .replace('ẹ', 'e').replace('Ẹ', 'e') // E with dot below
                .replace('ẻ', 'e').replace('Ẻ', 'e') // E with hook above
                .replace('ẽ', 'e').replace('Ẽ', 'e') // E with tilde
                .replace('ế', 'e').replace('Ế', 'e') // E with circumflex and acute
                .replace('ề', 'e').replace('Ề', 'e') // E with circumflex and grave
                .replace('ể', 'e').replace('Ể', 'e') // E with circumflex and hook above
                .replace('ễ', 'e').replace('Ễ', 'e') // E with circumflex and tilde
                .replace('ệ', 'e').replace('Ệ', 'e') // E with circumflex and dot below
                // Greek epsilon and variants (lowercase)
                .replace('ἐ', 'e').replace('ἑ', 'e').replace('ἒ', 'e').replace('ἓ', 'e')
                .replace('ἔ', 'e').replace('ἕ', 'e').replace('ὲ', 'e').replace('έ', 'e')
                // Greek Epsilon and variants (uppercase)
                .replace('Ἐ', 'e').replace('Ἑ', 'e').replace('Ἒ', 'e').replace('Ἓ', 'e')
                .replace('Ἔ', 'e').replace('Ἕ', 'e').replace('Ὲ', 'e').replace('Έ', 'e');
        
        // Convert to lowercase and apply character substitutions
        return normalized.toLowerCase()
                .replace("'", "").replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace('6', 'g').replace('7', 't')
                .replace('8', 'b').replace('@', 'a').replace('$', 's').replace('!', 'i')
                .replace('|', 'i').replace('¡', 'i').replace('Ǥ', 'g').replace('ɳ', 'n')
                .replace('ɨ', 'i').replace('ɢ', 'g').replace('ɛ', 'e').replace('ʀ', 'r')
                .replace('ġ', 'g').replace('є', 'e').replace('₦', 'n').replace('₲', 'g')
                .replace('ł', 'l').replace('*', 'a').replace(':', 'i'); // Colon looks like 'i' vertically
    }

    /**
     * Generates grammatical variants of a word (plural, possessive, past tense, etc.)
     * @param word The base word to generate variants for.
     * @return A list containing the original word and its grammatical variants.
     */
    public static List<String> generateGrammarVariants(String word) {
        List<String> variants = new ArrayList<>();
        
        // Always include the original word
        variants.add(word);
        
        // Add plural form (word + s)
        variants.add(word + "s");
        
        // Add possessive singular (word + 's)
        variants.add(word + "'s");
        
        // Add possessive plural (word + s')
        variants.add(word + "s'");
        
        // Add past tense variants for some words
        if (!word.endsWith("e")) {
            variants.add(word + "ed");
        } else {
            variants.add(word + "d");
        }
        
        // Add gerund (word + ing)
        if (!word.endsWith("e")) {
            variants.add(word + "ing");
        } else {
            variants.add(word + "ing"); // for words ending in 'e', just add 'ing'
        }
        
        // English consonant doubling rule: CVC (consonant-vowel-consonant) pattern
        // When adding -ed or -ing to CVC words, double the final consonant
        // Examples: "fag" → "fagging", "fagged"; "stop" → "stopping", "stopped"; "run" → "running", "runned" (archaic)
        if (word.length() >= 3) {
            String lowerWord = word.toLowerCase();
            char lastChar = lowerWord.charAt(word.length() - 1);
            char secondLast = lowerWord.charAt(word.length() - 2);
            char thirdLast = lowerWord.charAt(word.length() - 3);
            
            // Check if last char is consonant, second-last is vowel, third-last is consonant
            boolean lastIsConsonant = "bcdfghjklmnpqrstvwxyz".indexOf(lastChar) >= 0;
            boolean secondIsVowel = "aeiou".indexOf(secondLast) >= 0;
            boolean thirdIsConsonant = "bcdfghjklmnpqrstvwxyz".indexOf(thirdLast) >= 0;
            
            if (lastIsConsonant && secondIsVowel && thirdIsConsonant) {
                // Double the final consonant for -ing and -ed
                String doubledForm = word + lastChar;
                variants.add(doubledForm + "ing");
                variants.add(doubledForm + "ed");
            }
        }
        
        return variants;
    }

    /**
     * Generates all variants of a word with spaces inserted between characters.
     * For example, "abc" generates: "abc", "a bc", "ab c", "a b c"
     * @param word The word to generate variants for.
     * @return A list of all space-inserted variants.
     */
    public static List<String> generateSpaceVariants(String word) {
        List<String> variants = new ArrayList<>();
        int length = word.length();
        
        // There are length-1 positions where we can insert spaces
        int numPositions = length - 1;
        
        // Generate all 2^(length-1) combinations
        for (int mask = 0; mask < (1 << numPositions); mask++) {
            StringBuilder variant = new StringBuilder();
            variant.append(word.charAt(0));
            
            for (int i = 0; i < numPositions; i++) {
                // Check if bit i is set in mask
                if ((mask & (1 << i)) != 0) {
                    variant.append(" ");
                }
                variant.append(word.charAt(i + 1));
            }
            
            variants.add(variant.toString());
        }
        
        return variants;
    }

    /**
     * Calculates the theoretical "keyspace" of the banned word scanner.
     * Shows the total number of potential message variations that could trigger a ban,
     * accounting for grammar variants, space insertion, and multi-pass detection.
     * 
     * This is a fun metric showing the detection power of the system!
     * 
     * @param bannedWords The list of banned words to analyze.
     * @return A formatted string showing the keyspace breakdown.
     */
    public static String calculateKeyspace(List<String> bannedWords) {
        if (bannedWords == null || bannedWords.isEmpty()) {
            return "No banned words provided.";
        }
        
        long totalVariations = 0;
        long totalGrammarVariants = 0;
        long totalSpaceVariants = 0;
        
        StringBuilder breakdown = new StringBuilder();
        breakdown.append("\n========== BANNED WORD SCANNER KEYSPACE ANALYSIS ==========\n");
        breakdown.append("Theoretical detection capacity of the scanning system\n\n");
        
        for (String word : bannedWords) {
            String normalized = normalizeForKeywordCheck(word);
            List<String> grammarVariants = generateGrammarVariants(normalized);
            
            long wordVariations = 0;
            breakdown.append(String.format("Word: \"%s\"\n", word));
            breakdown.append("  Grammar variants:\n");
            
            for (String grammarVariant : grammarVariants) {
                List<String> spaceVariants = generateSpaceVariants(grammarVariant);
                int spaceCount = spaceVariants.size();
                
                wordVariations += spaceCount;
                totalSpaceVariants += spaceCount;
                
                breakdown.append(String.format("    - \"%s\": %,d space variants (2^%d)\n", 
                    grammarVariant, spaceCount, grammarVariant.length() - 1));
            }
            
            breakdown.append(String.format("  Subtotal: %,d space insertion variants\n", wordVariations));
            
            // Multiply by number of passes (5 different detection methods)
            long wordWithPasses = wordVariations * 5;
            
            // Account for character swaps and normalization
            // Each of the 200+ character mappings could create alternate paths
            // Conservative estimate: ~4 alternate normalization paths per character
            long charNormMultiplier = Math.min(100, (long)(Math.log(wordVariations) * 20));
            long totalWithNormalization = wordWithPasses * charNormMultiplier;
            
            breakdown.append(String.format("  With 5 detection passes: %,d variations\n", wordWithPasses));
            breakdown.append(String.format("  With 200+ character mappings (~%d normalization paths): %,d variations\n\n", 
                charNormMultiplier, totalWithNormalization));
            
            totalVariations += totalWithNormalization;
            totalGrammarVariants += grammarVariants.size();
        }
        
        // Calculate total theoretical capacity
        breakdown.append("========== SUMMARY ==========\n");
        breakdown.append(String.format("Banned words analyzed: %d\n", bannedWords.size()));
        breakdown.append(String.format("Total grammar variants: %,d\n", totalGrammarVariants));
        breakdown.append(String.format("Total space insertion combinations: %,d\n", totalSpaceVariants));
        breakdown.append(String.format("Multi-pass multiplier: 5x (l↔i swaps, σ→e swap, symbol stripping)\n"));
        breakdown.append(String.format("Character normalization multiplier: ~100x (200+ Unicode mappings)\n\n"));
        
        breakdown.append(String.format("ESTIMATED TOTAL KEYSPACE: %,d potential triggering variations\n", totalVariations));
        breakdown.append(String.format("(Plus additional coverage from Base64 encoding detection)\n"));
        breakdown.append("\n==========================================================\n");
        
        return breakdown.toString();
    }
}
