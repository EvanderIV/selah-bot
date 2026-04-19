package com.selah;

import java.util.*;

/**
 * Aho-Corasick string matching algorithm implementation.
 * Efficiently finds multiple patterns in a text in a single pass.
 * Time complexity: O(n + m + z) where n=text length, m=total pattern length, z=matches found
 * Much faster than checking each pattern individually which is O(n*p) where p=number of patterns
 */
public class AhoCorasickMatcher {
    
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        TrieNode fail = null;
        List<String> matches = new ArrayList<>(); // Patterns that end at this node
    }
    
    private final TrieNode root;
    private final List<String> patterns;
    
    /**
     * Create a new Aho-Corasick matcher with the given patterns
     */
    public AhoCorasickMatcher(List<String> patterns) {
        this.patterns = new ArrayList<>(patterns);
        this.root = new TrieNode();
        buildTrie();
        buildFailureLinks();
    }
    
    /**
     * Build the trie from all patterns
     */
    private void buildTrie() {
        for (String pattern : patterns) {
            TrieNode current = root;
            for (char c : pattern.toCharArray()) {
                current = current.children.computeIfAbsent(c, k -> new TrieNode());
            }
            current.matches.add(pattern);
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
                
                // Inherit matches from failure link
                child.matches.addAll(child.fail.matches);
            }
        }
    }
    
    /**
     * Find all pattern matches in the text
     * @param text The text to search in
     * @return Set of patterns found in the text
     */
    public Set<String> findMatches(String text) {
        Set<String> found = new LinkedHashSet<>();
        TrieNode current = root;
        
        for (char c : text.toCharArray()) {
            // Follow failure links until we find a match or reach root
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            
            current = current.children.getOrDefault(c, root);
            
            // Add all patterns that match at this position
            found.addAll(current.matches);
        }
        
        return found;
    }
    
    /**
     * Check if any pattern exists in the text
     * @param text The text to search in
     * @return true if any pattern is found
     */
    public boolean hasMatch(String text) {
        TrieNode current = root;
        
        for (char c : text.toCharArray()) {
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            
            current = current.children.getOrDefault(c, root);
            
            if (!current.matches.isEmpty()) {
                return true;
            }
        }
        
        return false;
    }
}
