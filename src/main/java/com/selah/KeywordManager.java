package com.selah;

import com.google.gson.Gson;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KeywordManager {

    public static List<Keyword> keywords = new ArrayList<>();
    public static List<Keyword> safeWords = new ArrayList<>();

    // Helper class for Gson parsing
    private static class KeywordFile {
        List<Keyword> keywords;
        List<Keyword> safeWords;
    }

    /**
     * Loads the keywords and safe words from the external JSON file.
     * First checks the current working directory, then falls back to App.WORKING_DIRECTORY.
     */
    public static void loadKeywords() {
        // Try current working directory first
        Path keywordPath = Path.of(System.getProperty("user.dir") + "/keywords.json");
        
        // Fall back to App.WORKING_DIRECTORY if not found
        if (!Files.exists(keywordPath)) {
            keywordPath = Path.of(App.WORKING_DIRECTORY + "keywords.json");
        }

        if (!Files.exists(keywordPath)) {
            System.err.println("CRITICAL: keywords.json not found. The bot will run, but no keywords will be loaded.");
            System.err.println("(Could not find at: \"" + App.WORKING_DIRECTORY + "keywords.json\" or \"" + System.getProperty("user.dir") + "/keywords.json\")");
            return;
        }

        try (Reader reader = Files.newBufferedReader(keywordPath)) {
            Gson gson = new Gson();
            KeywordFile keywordFile = gson.fromJson(reader, KeywordFile.class);

            if (keywordFile != null) {
                if (keywordFile.keywords != null) {
                    keywords = keywordFile.keywords;
                    System.out.println("Successfully loaded " + keywords.size() + " keywords from: " + keywordPath);
                }
                if (keywordFile.safeWords != null) {
                    safeWords = keywordFile.safeWords;
                    System.out.println("Successfully loaded " + safeWords.size() + " safe words.");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse keywords.json. Please check the file format.");
            e.printStackTrace();
        }
    }
}
