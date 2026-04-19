package com.selah;

import java.util.*;

/**
 * Aho-Corasick based heat keyword matcher.
 * Finds all keywords and their heat values in a single pass instead of checking each keyword individually.
 * Much faster than iterating through keywords when there are 80+ keywords to check.
 */
public class HeatKeywordMatcher {
    
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        TrieNode fail = null;
        List<Keyword> keywords = new ArrayList<>(); // Keywords that end at this node
    }
    
    private final TrieNode root;
    private final List<Keyword> allKeywords;
    
    /**
     * Create a new heat keyword matcher with the given keywords
     */
    public HeatKeywordMatcher(List<Keyword> keywords) {
        this.allKeywords = new ArrayList<>(keywords);
        this.root = new TrieNode();
        buildTrie();
        buildFailureLinks();
    }
    
    /**
     * Build the trie from all keywords
     */
    private void buildTrie() {
        for (Keyword keyword : allKeywords) {
            TrieNode current = root;
            for (char c : keyword.word.toCharArray()) {
                current = current.children.computeIfAbsent(c, k -> new TrieNode());
            }
            current.keywords.add(keyword);
        }
    }
    
    /**
     * Build failure links for the trie (KMP-style)
     */
    private void buildFailureLinks() {
        Queue<TrieNode> queue = new LinkedList<>();
        root.fail = root;
        
        // BFS to set failure links
        for (TrieNode node : root.children.values()) {
            node.fail = root;
            queue.add(node);
        }
        
        while (!queue.isEmpty()) {
            TrieNode current = queue.poll();
            
            for (Map.Entry<Character, TrieNode> entry : current.children.entrySet()) {
                char c = entry.getKey();
                TrieNode child = entry.getValue();
                queue.add(child);
                
                TrieNode fail = current.fail;
                while (fail != root && !fail.children.containsKey(c)) {
                    fail = fail.fail;
                }
                
                child.fail = fail.children.getOrDefault(c, root);
                
                // Inherit keywords from failure link
                child.keywords.addAll(child.fail.keywords);
            }
        }
    }
    
    /**
     * Find all keywords in the text (whole word matches)
     * @param text The text to search in
     * @return List of keywords found as whole words
     */
    public List<Keyword> findWholeWordMatches(String text) {
        List<Keyword> found = new ArrayList<>();
        TrieNode current = root;
        int textIndex = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Follow failure links until we find a match or reach root
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            
            current = current.children.getOrDefault(c, root);
            
            // Check if we have matches at this position
            for (Keyword kw : current.keywords) {
                // Check if it's a whole word match (word boundaries)
                int startIndex = i - kw.word.length() + 1;
                
                boolean isWordStart = startIndex == 0 || !Character.isLetterOrDigit(text.charAt(startIndex - 1));
                boolean isWordEnd = i == text.length() - 1 || !Character.isLetterOrDigit(text.charAt(i + 1));
                
                if (isWordStart && isWordEnd) {
                    found.add(kw);
                }
            }
        }
        
        return found;
    }
    
    /**
     * Find all keywords in the text (substring matches, not just whole words)
     * @param text The text to search in
     * @return List of keywords found (including partial matches)
     */
    public List<Keyword> findAllMatches(String text) {
        List<Keyword> found = new ArrayList<>();
        TrieNode current = root;
        
        for (char c : text.toCharArray()) {
            // Follow failure links until we find a match or reach root
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            
            current = current.children.getOrDefault(c, root);
            
            // Add all keywords that match at this position
            found.addAll(current.keywords);
        }
        
        // Deduplicate - keep only unique keywords
        return new ArrayList<>(new LinkedHashSet<>(found));
    }
    
    /**
     * Get total heat from all matched keywords
     */
    public double calculateHeatFromMatches(List<Keyword> matches) {
        double heat = 0;
        for (Keyword kw : matches) {
            heat += kw.heat;
        }
        return heat;
    }
}
