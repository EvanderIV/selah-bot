import json
import sys

# Ensure a file name is provided as a command-line argument
if len(sys.argv) < 2:
    print("Usage: python generate_readable_stats.py <json_file_name>")
    sys.exit(1)

# Get the JSON file name from the first command-line argument
json_file_name = sys.argv[1]

# 1. Load your original database file explicitly using utf-8
with open(json_file_name, 'r', encoding='utf-8') as f:
    data = json.load(f)

# 2. Create a mapping dictionary of memberId -> memberName
id_to_name = {member['memberId']: member['memberName'] for member in data['members']}

# 3. Iterate through all members and update their relations
for member in data['members']:
    for relation in member.get('relations', []):
        target_id = relation['targetMemberId']
        
        # If the ID exists in our mapping, replace it with the name
        if target_id in id_to_name:
            relation['targetMemberName'] = id_to_name[target_id]
        else:
            # For placeholder IDs that aren't in the member list
            relation['targetMemberName'] = f"Unknown_User_{target_id}"

        # Remove the old targetMemberId key
        del relation['targetMemberId']

# 4. Save the updated data to a new file explicitly using utf-8
with open('updated_database.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2)

print("Database successfully updated with usernames!")