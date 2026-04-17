package com.selah;

import java.util.ArrayList;
import java.util.List;

public class FalsePositiveTest {
    public static void main(String[] args) {
        // Test cases that should NOT trigger the filter (false positives to avoid)
        List<TestCase> negativeCases = new ArrayList<>();
        negativeCases.add(new TestCase("That's my trigger word", false));
        negativeCases.add(new TestCase("I love trigger warnings", false));
        negativeCases.add(new TestCase("Don't be such a trigger", false));
        negativeCases.add(new TestCase("He pulled the trigger", false));
        negativeCases.add(new TestCase("That's a bigger problem", false));
        negativeCases.add(new TestCase("The bigger the better", false));
        negativeCases.add(new TestCase("He's getting bigger", false));
        negativeCases.add(new TestCase("I like dagger wielding", false));
        negativeCases.add(new TestCase("He had a dagger", false));
        negativeCases.add(new TestCase("The dagger was sharp", false));
        negativeCases.add(new TestCase("I'm a logger", false));
        negativeCases.add(new TestCase("The logger cut down trees", false));
        negativeCases.add(new TestCase("Logging is important", false));
        negativeCases.add(new TestCase("I'm jogging today", false));
        negativeCases.add(new TestCase("She loves jogging", false));
        negativeCases.add(new TestCase("We're jogging together", false));
        negativeCases.add(new TestCase("He's a beggar", false));
        negativeCases.add(new TestCase("The beggar asked for money", false));
        negativeCases.add(new TestCase("Don't be a beggar", false));
        negativeCases.add(new TestCase("I found a nugget", false));
        negativeCases.add(new TestCase("gold nugget", false));
        negativeCases.add(new TestCase("chicken nuggets", false));
        negativeCases.add(new TestCase("That's rugged terrain", false));
        negativeCases.add(new TestCase("He looks rugged", false));
        negativeCases.add(new TestCase("Rugged design", false));
        negativeCases.add(new TestCase("The bag is sagging", false));
        negativeCases.add(new TestCase("His pants are sagging", false));
        negativeCases.add(new TestCase("Stop nagging me", false));
        negativeCases.add(new TestCase("She's nagging again", false));
        negativeCases.add(new TestCase("Don't be nagging", false));
        negativeCases.add(new TestCase("I'm tagging you", false));
        negativeCases.add(new TestCase("He's tagging along", false));
        negativeCases.add(new TestCase("The dog is wagging its tail", false));
        negativeCases.add(new TestCase("She's wagging her finger", false));
        negativeCases.add(new TestCase("He's lagging behind", false));
        negativeCases.add(new TestCase("The game is lagging", false));
        negativeCases.add(new TestCase("He's gagging on something", false));
        negativeCases.add(new TestCase("The smell made him gag", false));
        negativeCases.add(new TestCase("baggage", false));
        negativeCases.add(new TestCase("luggage", false));
        negativeCases.add(new TestCase("dagger", false));
        negativeCases.add(new TestCase("swagger", false));
        negativeCases.add(new TestCase("rigging a ship", false));
        negativeCases.add(new TestCase("bogging down", false));
        negativeCases.add(new TestCase("fogging up", false));
        negativeCases.add(new TestCase("jogging is healthy", false));
        negativeCases.add(new TestCase("dogging someone's steps", false));
        negativeCases.add(new TestCase("I went jogging with my friends and we ran for an hour", false));
        negativeCases.add(new TestCase("This trigger warning should help people prepare mentally", false));
        negativeCases.add(new TestCase("The bigger issue is that we need systemic change", false));
        negativeCases.add(new TestCase("He's carrying a dagger and sword", false));
        negativeCases.add(new TestCase("Eggerton is a nice place", false));
        negativeCases.add(new TestCase("I like Trigger (the horse from Roy Rogers)", false));
        // Note: "niggardly" is a real but controversial English word
        // It means stingy/miserly but sounds similar to the slur
        // For moderation purposes, we may want to include it in filters
        
        List<TestCase> positiveCases = new ArrayList<>();
        positiveCases.add(new TestCase("nigger", true));
        positiveCases.add(new TestCase("You're a nigger", true));
        positiveCases.add(new TestCase("n1gger", true));
        positiveCases.add(new TestCase("n!gger", true));
        positiveCases.add(new TestCase("NIGGER", true));
        positiveCases.add(new TestCase("niGGer", true));
        positiveCases.add(new TestCase("ηιggєя", true)); // Greek/Cyrillic mixed
        positiveCases.add(new TestCase("𝚗𝚒𝚐𝚐𝚎𝚛", true)); // Mathematical alphanumeric
        positiveCases.add(new TestCase("I hate niggers", true));
        positiveCases.add(new TestCase("that nigger over there", true));
        
        String bannedWord = "nigger";
        
        int negativePass = 0, negativeFail = 0;
        int positivePass = 0, positiveFail = 0;
        
        System.out.println("=".repeat(80));
        System.out.println("Testing FALSE POSITIVES (should NOT trigger):");
        System.out.println("=".repeat(80));
        
        for (TestCase testCase : negativeCases) {
            boolean detected = BannedWordScanner.isBannedWordPresent(testCase.text, bannedWord);
            boolean correct = (detected == testCase.shouldTrigger);
            
            if (correct) {
                System.out.println("✓ PASS: " + testCase.text);
                negativePass++;
            } else {
                System.out.println("✗ FAIL: " + testCase.text + " (incorrectly " + 
                        (detected ? "BLOCKED" : "ALLOWED") + ")");
                negativeFail++;
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Testing POSITIVES (should trigger):");
        System.out.println("=".repeat(80));
        
        for (TestCase testCase : positiveCases) {
            boolean detected = BannedWordScanner.isBannedWordPresent(testCase.text, bannedWord);
            boolean correct = (detected == testCase.shouldTrigger);
            
            if (correct) {
                System.out.println("✓ PASS: " + testCase.text);
                positivePass++;
            } else {
                System.out.println("✗ FAIL: " + testCase.text + " (should have been " + 
                        (testCase.shouldTrigger ? "BLOCKED" : "ALLOWED") + ")");
                positiveFail++;
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUMMARY:");
        System.out.println("False Positives (shouldn't block):  " + negativePass + " passed, " + 
                negativeFail + " failed out of " + negativeCases.size());
        System.out.println("True Positives (should block):     " + positivePass + " passed, " + 
                positiveFail + " failed out of " + positiveCases.size());
        
        int totalPass = negativePass + positivePass;
        int totalTests = negativeCases.size() + positiveCases.size();
        System.out.println("Overall:                           " + totalPass + " passed, " + 
                (totalTests - totalPass) + " failed out of " + totalTests);
        System.out.println("Pass rate: " + String.format("%.1f%%", (100.0 * totalPass / totalTests)));
        System.out.println("=".repeat(80));
    }
    
    static class TestCase {
        String text;
        boolean shouldTrigger;
        
        TestCase(String text, boolean shouldTrigger) {
            this.text = text;
            this.shouldTrigger = shouldTrigger;
        }
    }
}
