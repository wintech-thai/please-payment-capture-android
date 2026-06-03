#!/bin/bash
# Remove test credentials from documentation and replace with placeholders

REPO_PATH="/Users/linus/AndroidStudioProjects/notificationagent"
cd "$REPO_PATH"

# Files to clean
FILES=(
  "ACTION_GUIDE.md"
  "scripts/QUICK_REFERENCE.md"
  "scripts/WEBHOOK_COMPLIANCE_REPORT.md"
  "scripts/WEBHOOK_TESTING_GUIDE.md"
  "scripts/FIX_TEST_ERROR_GUIDE.md"
  "scripts/WEBHOOK_TEST_FIX_SUMMARY.md"
  "scripts/test_webhook.py"
  "scripts/BEFORE_AFTER_COMPARISON.md"
)

# Replace credenti credentials
for file in "${FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Cleaning: $file"
    # Replace demo-hook.rocketlabth.com with placeholder
    sed -i '' 's#https://demo-hook.rocketlabth.com/webhook#https://your-webhook-endpoint.com/webhook#g' "$file"
    sed -i '' 's#demo-hook.rocketlabth.com#your-webhook-endpoint.com#g' "$file"

    # Replace gabxcloud with placeholder
    sed -i '' 's/gabxcloud/YOUR_BEARER_TOKEN/g' "$file"
  fi
done

# Verify changes
echo ""
echo "✅ Verifying credentials removed:"
grep -r "gabxcloud\|demo-hook.rocketlabth.com" . --include="*.md" --include="*.py" 2>/dev/null || echo "No credentials found - all cleaned!"

echo ""
echo "✅ Cleanup complete!"

