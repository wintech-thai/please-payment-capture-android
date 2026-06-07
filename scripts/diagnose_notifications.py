#!/usr/bin/env python3
"""
Diagnostic script to check Notification Agent setup and database
"""

import subprocess
import sys

def run_adb_command(cmd):
    """Run an ADB command and return output"""
    try:
        result = subprocess.run(f"adb shell {cmd}", shell=True, capture_output=True, text=True)
        return result.stdout.strip()
    except Exception as e:
        return f"ERROR: {e}"

def main():
    print("╔════════════════════════════════════════════════════════════════╗")
    print("║  Notification Agent - Diagnostic Check                         ║")
    print("╚════════════════════════════════════════════════════════════════╝\n")

    # 1. Check if listener is enabled
    print("1. Checking if Notification Listener is Enabled:")
    print("────────────────────────────────────────────────")
    listeners = run_adb_command("settings get secure enabled_notification_listeners")
    if "com.example.notification_agent" in listeners:
        print("✓ Notification Agent is registered as listener")
        print(f"  Value: {listeners[:80]}...")
    else:
        print("✗ Notification Agent NOT registered!")
        print(f"  Value: {listeners}")
        print("\n  FIX: Run this command:")
        print("  adb shell settings put secure enabled_notification_listeners \\")
        print('    "com.example.notification_agent/.service.NotificationCaptureService"')
    print()

    # 2. Check if app is installed
    print("2. Checking App Installation:")
    print("────────────────────────────")
    app_info = run_adb_command("pm list packages | grep notification_agent")
    if app_info:
        print(f"✓ App installed: {app_info}")
    else:
        print("✗ App NOT installed!")
        print("  FIX: Build and install the app")
    print()

    # 3. Check app permissions
    print("3. Checking App Permissions:")
    print("────────────────────────────")
    post_notif = run_adb_command("pm list permissions com.example.notification_agent | grep POST_NOTIFICATIONS")
    print(f"POST_NOTIFICATIONS: {'✓ Granted' if post_notif else '✗ Not granted'}")
    print()

    # 4. Check if LINE app is needed
    print("4. Required Apps:")
    print("─────────────────")
    line_app = run_adb_command("pm list packages | grep 'line.android'")
    print(f"LINE app installed: {'✓ Yes' if line_app else '✗ No (optional - we simulate it)'}")
    print()

    # 5. Check database
    print("5. Checking Notification Database:")
    print("──────────────────────────────────")
    db_check = run_adb_command("ls -la /data/data/com.example.notification_agent/databases/ 2>/dev/null | wc -l")
    print(f"Database files exist: {'✓ Yes' if db_check != '0' else '✗ No'}")

    # Try to query the database
    print("\n   Captured Messages in Database:")
    result = run_adb_command(
        "sqlite3 /data/data/com.example.notification_agent/databases/notification_agent.db "
        "'SELECT COUNT(*) as count FROM messages;' 2>/dev/null"
    )
    if result and result.isdigit():
        count = int(result)
        print(f"   ✓ Total messages: {count}")
        if count == 0:
            print("   → No messages captured yet")
    else:
        print(f"   Status: {result}")
    print()

    # 6. Check if app is running
    print("6. App Process Status:")
    print("─────────────────────")
    ps_result = run_adb_command("ps -e | grep notification_agent")
    if ps_result:
        print("✓ App process is running")
        print(f"  {ps_result}")
    else:
        print("✗ App process NOT running")
        print("  FIX: Open the app on emulator first!")
    print()

    # 7. Timestamp check
    print("7. Filter Rules:")
    print("────────────────")
    filter_result = run_adb_command(
        "sqlite3 /data/data/com.example.notification_agent/databases/notification_agent.db "
        "'SELECT sourceKey, enabled FROM filter_rules;' 2>/dev/null"
    )
    print(f"Filter status:\n{filter_result}")
    print()

    # Summary
    print("╔════════════════════════════════════════════════════════════════╗")
    print("║  Summary & Next Steps                                          ║")
    print("╚════════════════════════════════════════════════════════════════╝\n")

    if "com.example.notification_agent" not in listeners:
        print("CRITICAL: Notification listener is not enabled!")
        print("RUN: adb shell settings put secure enabled_notification_listeners \\")
        print('     "com.example.notification_agent/.service.NotificationCaptureService"')
        return 1

    if not ps_result or "notification_agent" not in ps_result:
        print("CRITICAL: App is not running!")
        print("ACTION: Open the Notification Agent app on your emulator")
        print("        Let it run for at least 5 seconds")
        return 1

    print("✓ System appears configured correctly!")
    print("\nNext Steps:")
    print("1. Make sure Notification Agent app is open on emulator")
    print("2. Run the mock notification script:")
    print("   python3 scripts/mock_scb_line_notification.py --amount 1000 --verbose")
    print("3. Immediately check app's Messages tab")
    print("4. If still not working, check: adb logcat | grep NotificationAgent")

    return 0

if __name__ == "__main__":
    sys.exit(main())

