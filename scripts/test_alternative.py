#!/usr/bin/env python3
"""
Alternative notification testing approach using direct database writes
and service triggering
"""

import subprocess
import json
import sqlite3
import tempfile
from pathlib import Path

def run_adb(cmd):
    """Run adb shell command"""
    result = subprocess.run(f"adb shell {cmd}", shell=True, capture_output=True, text=True)
    return result.stdout.strip(), result.returncode

def test_via_test_app():
    """
    The problem: ADB notification commands don't trigger NotificationListenerService callbacks.

    Solution OPTION 1: Install LINE app (hard)
    Solution OPTION 2: Create a simple test app (medium)
    Solution OPTION 3: Test via database and explicit triggering (easy) ← We use this
    Solution OPTION 4: Use broadcast to trigger the service directly (tested below)
    """

    print("""
╔═════════════════════════════════════════════════════════════════════════╗
║  Testing Notification Capture - Alternative Approach                   ║
╚═════════════════════════════════════════════════════════════════════════╝

The issue: NotificationListenerService needs REAL system notifications.
Our ADB commands aren't generating proper system notifications.

Solution: Create a test by using broadcast intent to directly trigger
the app's webhook with test data.

This simulates what would happen IF a notification was captured.
""")

    # Test 1: Try to broadcast a test intent
    print("\n1. Testing via direct intent broadcast:")
    print("─" * 50)

    output, code = run_adb(
        "am broadcast -a com.example.notification_agent.TEST_NOTIFICATION "
        "--es title 'SCB Connect' "
        "--es text 'รายการเงินเข้า 1000.00 บาท เข้าบัญชี X-7985' "
        "--es package jp.naver.line.android "
        "com.example.notification_agent"
    )

    if code == 0:
        print("✓ Broadcast sent")
        print(f"  Output: {output[:100]}")
    else:
        print("✗ Broadcast failed")

    # Test 2: Check logs
    print("\n2. Checking app logs for the broadcast:")
    print("─" * 50)

    output, _ = run_adb("logcat -d | grep -i 'notification_agent\\|broadcast' | tail -5")
    if output:
        print("Recent logs:")
        for line in output.split('\n')[-5:]:
            if line:
                print(f"  {line}")

    # Test 3: Suggest the REAL solution
    print("\n" + "=" * 70)
    print("REAL SOLUTION: Install LINE App or Use Physical Testing")
    print("=" * 70)
    print("""
The Notification Agent app is designed to capture notifications from REAL apps.

For testing, you have 2 options:

OPTION A: Install LINE App (Recommended for emulator)
─────────────────────────────────────────────────────
1. Open Google Play Store on emulator
2. Search for "LINE"
3. Install LINE app
4. Keep LINE running in background
5. Our mock notification will now work properly!

OPTION B: Use a real Android device
───────────────────────────────────
1. Connect your phone to Mac via USB
2. All our scripts will work on real device
3. Install LINE app on phone
4. Run: adb -d shell ... (to target connected device)

OPTION C: Test the backend logic directly
─────────────────────────────────────────
Don't need real notifications to test webhook forwarding:

A. Manually insert test data into database:

   python3 << 'EOF'
   import sqlite3
   import json
   from datetime import datetime

   db_path = '/data/data/com.example.notification_agent/databases/notification_agent.db'

   # This would require root access
   # For now, test via webhook directly
   EOF

B. Test webhook endpoint directly using curl:

   curl -X POST http://your-webhook-url/webhook \\
     -H "Content-Type: application/json" \\
     -d '{
       "PaymentAmount": "1000.00",
       "SourceBankCode": "SCB",
       "SourceBankAccountNo": "X-7985"
     }'

════════════════════════════════════════════════════════════════════════════
""")

if __name__ == "__main__":
    test_via_test_app()

