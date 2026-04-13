#!/usr/bin/env python3
import re
import json

# Load message file
archive_file = "./archive_1399942629759783033_deep-fryer.txt"
output_file = "./archive_1399942629759783033_deep-fryer_cleaned.txt"

# Debate pattern indicators
debate_keywords = [
    "i think", "i believe", "i argue", "i'd say", "i'd argue",
    "however", "therefore", "thus", "hence", "conversely",
    "on the other hand", "in contrast", "one could argue",
    "would you agree", "don't you think", "i'm right",
    "controversy", "controversial", "hot take", "actually",
    "objectively", "claim", "argument", "point", "evidence"
]

removed_count = 0
kept_count = 0
removed_messages = []

with open(archive_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

cleaned_lines = []

for line in lines:
    # Parse format: [YYYY-MM-DD HH:MM:SS] username: message
    if not line.strip():
        cleaned_lines.append(line)
        continue
    
    match = re.match(r'\[(.*?)\]\s+(.*?):\s+(.*?)$', line.rstrip())
    if not match:
        cleaned_lines.append(line)
        continue
    
    timestamp, username, message = match.groups()
    msg_lower = message.lower()
    
    # Count debate indicators
    debate_score = 0
    found_keywords = []
    
    # Check for debate keywords
    for keyword in debate_keywords:
        if keyword in msg_lower:
            debate_score += 1
            found_keywords.append(keyword)
    
    # Check for multiple questions (debate indicator)
    question_count = message.count('?')
    if question_count > 1:
        debate_score += question_count * 0.5
    
    # Check for complex sentence structure (multiple sentences with varied punctuation)
    sentence_endings = message.count('.') + message.count('!') + message.count('?')
    if sentence_endings > 2 and len(message) > 100:
        debate_score += 1
    
    # Check for conditional language
    conditionals = ["if ", " then ", " would ", " could ", " should ", " might "]
    conditional_count = sum(msg_lower.count(cond) for cond in conditionals)
    if conditional_count > 1:
        debate_score += conditional_count * 0.3
    
    # Check for quotes (citations)
    if message.count('"') >= 2:
        debate_score += 0.5
    
    # Remove messages with debate score > 1.5
    if debate_score > 1.5:
        removed_count += 1
        removed_messages.append({
            'message': message,
            'user': username,
            'score': debate_score,
            'keywords': found_keywords
        })
    else:
        kept_count += 1
        cleaned_lines.append(line)

# Write cleaned file
with open(output_file, 'w', encoding='utf-8') as f:
    f.writelines(cleaned_lines)

# Print report
print(f"\n=== ARCHIVE CLEANING REPORT ===")
print(f"Original messages: {len(lines)}")
print(f"Kept: {kept_count}")
print(f"Removed (debate-like): {removed_count}")
print(f"Removal percentage: {(removed_count/len(lines)*100):.1f}%")
print(f"\nCleaned archive saved to: {output_file}")

if removed_messages:
    print(f"\n=== SAMPLE REMOVED MESSAGES (first 10) ===")
    for i, msg in enumerate(removed_messages[:10]):
        print(f"\n{i+1}. [{msg['user']}] (score: {msg['score']:.2f})")
        print(f"   Message: {msg['message'][:80]}")
        if msg['keywords']:
            print(f"   Keywords found: {', '.join(set(msg['keywords']))}")
