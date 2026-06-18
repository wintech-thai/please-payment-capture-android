#!/usr/bin/env python3
"""Mock SCB / KTB LINE bank notifications and send to Android device via ADB.

This script generates realistic Thai banking notifications and posts them to the
Notification Agent app via Android's notification system.

Usage:
    python3 mock_scb_line_notification.py --amount 1000 --bank SCB --account X-7985
    python3 mock_scb_line_notification.py --amount 50000.50 --bank KTB --account XX7157
    python3 mock_scb_line_notification.py --amount 5555 --bank SCB --verbose
"""

import argparse
import json
import subprocess
import sys
import uuid
from datetime import datetime
from typing import Optional

# LINE package name (hardcoded as per the app's parser)
LINE_PACKAGE_NAME = "jp.naver.line.android"

# Supported banks and their LINE notification titles
BANKS = {
    "SCB": {
        "title": "SCB Connect",
        "keywords": ["SCB Connect", "SCB"]
    },
    "KTB": {
        "title": "Krungthai Connext",
        "keywords": ["Krungthai Connext", "Krungthai"]
    }
}


def format_thb_amount(amount: float) -> str:
    """Format amount as Thai baht with proper formatting.

    Examples:
        100.00 -> "100.00"
        1000.00 -> "1,000.00"
        50000.50 -> "50,000.50"
    """
    return "{:,.2f}".format(amount)


def generate_line_notification_text(
    amount: float,
    account: Optional[str] = None,
    timestamp: Optional[str] = None
) -> str:
    """Generate realistic LINE notification text for SCB/KTB payment.

    Format: "รายการเงินเข้า <AMOUNT> บาท เข้าบัญชี <ACCOUNT> วันที่ <TIMESTAMP>"
    """
    if timestamp is None:
        now = datetime.now()
        timestamp = now.strftime("%d/%m/%Y %H:%M")

    formatted_amount = format_thb_amount(amount)
    text = f"รายการเงินเข้า {formatted_amount} บาท"

    if account:
        text += f" เข้าบัญชี {account}"

    text += f" วันที่ {timestamp}"

    return text


def check_adb_device() -> bool:
    """Check if ADB device is connected and available."""
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=5
        )
        # Check if any device is listed (not just "List of attached devices:")
        lines = result.stdout.strip().split('\n')
        devices = [line for line in lines[1:] if line.strip() and "device" in line]
        return len(devices) > 0
    except FileNotFoundError:
        print("ERROR: adb not found. Make sure Android SDK is installed and adb is in PATH.")
        return False
    except Exception as e:
        print(f"ERROR: Failed to check ADB devices: {e}")
        return False


def get_adb_device_id() -> Optional[str]:
    """Get the ID of the first connected ADB device."""
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=5
        )
        lines = result.stdout.strip().split('\n')
        for line in lines[1:]:
            if line.strip() and "device" in line:
                device_id = line.split()[0]
                return device_id
        return None
    except Exception as e:
        print(f"ERROR: Failed to get ADB device: {e}")
        return None


def post_notification_via_adb(
    device_id: str,
    title: str,
    text: str,
    package_name: str = LINE_PACKAGE_NAME,
    verbose: bool = False
) -> bool:
    """Post a notification to Android device via ADB.

    This uses ADB to directly insert a notification into the system's notification queue.
    """
    try:
        # Create notification ID (use timestamp-based ID)
        notification_id = int(datetime.now().timestamp() * 1000) % 2147483647

        # Build the ADB command to post notification
        # We'll use a shell command that posts the notification directly
        adb_cmd = [
            "adb", "-s", device_id, "shell",
            f"am broadcast -a com.example.notification_agent.MOCK_NOTIFICATION "
            f"--es title '{title}' "
            f"--es text '{text}' "
            f"--es package '{package_name}' "
            f"--ei id {notification_id}"
        ]

        if verbose:
            print(f"[ADB] Executing: {' '.join(adb_cmd)}")

        result = subprocess.run(
            adb_cmd,
            capture_output=True,
            text=True,
            timeout=10
        )

        if result.returncode != 0:
            # Try alternative method: use notification API via adb shell
            return post_notification_via_shell(
                device_id, title, text, package_name, verbose
            )

        if verbose:
            print(f"[ADB] Notification posted successfully")
            if result.stdout:
                print(f"[ADB] Output: {result.stdout}")

        return True

    except Exception as e:
        print(f"ERROR: Failed to post notification via ADB: {e}")
        return False


def post_notification_via_shell(
    device_id: str,
    title: str,
    text: str,
    package_name: str = LINE_PACKAGE_NAME,
    verbose: bool = False
) -> bool:
    """Alternative method to post notification via adb shell."""
    try:
        # Create a simple JavaScript-like command to post to notification service
        # For Android 11+, we can use NotificationCompat via a helper

        # Alternative: Use service call to NotificationManager
        adb_cmd = [
            "adb", "-s", device_id, "shell",
            "service call notification 1 s16",
            f"'{package_name}'"
        ]

        if verbose:
            print(f"[ADB Shell] Alternative notification method")

        result = subprocess.run(
            adb_cmd,
            capture_output=True,
            text=True,
            timeout=10
        )

        return result.returncode == 0

    except Exception as e:
        if verbose:
            print(f"DEBUG: Shell method also failed: {e}")
        return False


def simulate_notification_to_app(
    device_id: str,
    title: str,
    text: str,
    package_name: str = LINE_PACKAGE_NAME,
    verbose: bool = False
) -> bool:
    """Simulate notification by triggering the NotificationCaptureService directly.

    This method uses ADB to artificially trigger the service with mock data.
    """
    try:
        # Create JSON payload that mimics what NotificationCaptureService receives
        notification_data = {
            "packageName": package_name,
            "title": title,
            "text": text,
            "timestamp": int(datetime.now().timestamp() * 1000)
        }

        # Encode as JSON and send via am to trigger the app
        payload_json = json.dumps(notification_data).replace('"', '\\"')

        # Use intent to trigger app with notification simulation
        adb_cmd = [
            "adb", "-s", device_id, "shell",
            "am startservice",
            "-a com.example.notification_agent.MOCK_NOTIFICATION",
            f"-e notification '{payload_json}'",
            "com.example.notification_agent/.service.NotificationCaptureService"
        ]

        if verbose:
            print(f"[TRIGGER] Sending mock notification to app:")
            print(f"  Title: {title}")
            print(f"  Text: {text}")
            print(f"  Package: {package_name}")

        result = subprocess.run(
            adb_cmd,
            capture_output=True,
            text=True,
            timeout=10
        )

        # Also try posting visible notification
        post_visible_notification(device_id, title, text, verbose)

        return result.returncode == 0

    except Exception as e:
        if verbose:
            print(f"DEBUG: Service trigger failed: {e}")
        return False


def post_visible_notification(
    device_id: str,
    title: str,
    text: str,
    verbose: bool = False
) -> bool:
    """Post a visible notification using monkey / test framework.

    This creates an actual visible notification on the device.
    """
    try:
        notification_id = int(datetime.now().timestamp() * 1000) % 2147483647

        adb_cmd = [
            "adb", "-s", device_id, "shell",
            "cmd", "notification", "post",
            "-t", title,
            "-i", "@android:drawable/ic_dialog_info",
            "-S", "bigtext",
            f"SCB_LINE_{notification_id}",
            text,
        ]

        result = subprocess.run(
            adb_cmd,
            capture_output=True,
            text=True,
            timeout=10
        )

        if verbose and result.returncode == 0:
            print(f"[VISIBLE] Notification posted (ID: {notification_id})")

        return result.returncode == 0

    except Exception as e:
        if verbose:
            print(f"DEBUG: Visible notification failed: {e}")
        return False


def send_mock_notification(
    amount: float,
    bank: str = "SCB",
    account: Optional[str] = None,
    device_id: Optional[str] = None,
    verbose: bool = False
) -> bool:
    """Send a mock LINE notification to the Android device.

    Args:
        amount: Payment amount in Thai Baht
        bank: Bank name ("SCB" or "KTB")
        account: Account number (e.g., "X-7985" or "XX7157")
        device_id: ADB device ID (auto-detected if None)
        verbose: Enable verbose output

    Returns:
        True if notification was sent successfully
    """
    # Validate inputs
    if bank.upper() not in BANKS:
        print(f"ERROR: Unsupported bank '{bank}'. Supported banks: {', '.join(BANKS.keys())}")
        return False

    if amount <= 0:
        print(f"ERROR: Amount must be positive, got {amount}")
        return False

    bank = bank.upper()

    # Get device ID if not provided
    if device_id is None:
        device_id = get_adb_device_id()
        if device_id is None:
            print("ERROR: No ADB device found. Make sure your Android device is connected.")
            return False

    if verbose:
        print(f"[CONFIG] Device: {device_id}")
        print(f"[CONFIG] Bank: {bank}")
        print(f"[CONFIG] Amount: {format_thb_amount(amount)} THB")
        if account:
            print(f"[CONFIG] Account: {account}")

    # Generate notification content
    bank_title = BANKS[bank]["title"]
    notification_text = generate_line_notification_text(amount, account)

    if verbose:
        print(f"\n[CONTENT] Title: {bank_title}")
        print(f"[CONTENT] Text: {notification_text}")
        print(f"[CONTENT] Package: {LINE_PACKAGE_NAME}")

    # Attempt to send notification
    print(f"\nSending mock {bank} LINE notification to {device_id}...")

    success = simulate_notification_to_app(
        device_id,
        bank_title,
        notification_text,
        LINE_PACKAGE_NAME,
        verbose
    )

    if success:
        print("✓ Mock notification sent successfully!")
        print(f"  Check the Notification Agent app to see the captured notification.")
        return True
    else:
        print("✗ Failed to send mock notification via standard methods.")
        print("  Trying alternative method...")

        # Try posting visible notification as fallback
        if post_visible_notification(device_id, bank_title, notification_text, verbose):
            print("✓ Sent visible notification (may require manual verification)")
            return True
        else:
            print("✗ All notification methods failed.")
            return False


def main():
    parser = argparse.ArgumentParser(
        description="Mock SCB/KTB LINE bank notifications for Notification Agent testing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Send 1,000 THB SCB payment notification
  %(prog)s --amount 1000 --bank SCB --account X-7985

  # Send 50,000.50 THB KTB payment (verbose)
  %(prog)s --amount 50000.50 --bank KTB --account XX7157 --verbose

  # Send multiple notifications with different amounts
  for amt in 100 500 1000 5000; do
    %(prog)s --amount $amt --bank SCB --account X-7985 --quiet
    sleep 2
  done
        """
    )

    parser.add_argument(
        "--amount", type=float, required=True,
        help="Payment amount in Thai Baht (e.g., 1000, 50000.50)"
    )
    parser.add_argument(
        "--bank", choices=["SCB", "KTB"], default="SCB",
        help="Bank name (default: SCB)"
    )
    parser.add_argument(
        "--account", type=str,
        help="Account number (e.g., X-7985 for SCB, XX7157 for KTB)"
    )
    parser.add_argument(
        "--device", "-d", type=str,
        help="ADB device ID (auto-detect if not specified)"
    )
    parser.add_argument(
        "--verbose", "-v", action="store_true",
        help="Enable verbose output"
    )
    parser.add_argument(
        "--quiet", "-q", action="store_true",
        help="Minimal output"
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Show what would be sent without actually sending"
    )

    args = parser.parse_args()

    # Check ADB availability
    if not check_adb_device():
        print("ERROR: No ADB device available.")
        print("Make sure:")
        print("  1. Android SDK Platform Tools are installed")
        print("  2. Your Android device is connected via USB with developer mode enabled")
        print("  3. You've approved USB debugging on your device")
        return 1

    # Show welcome message
    if not args.quiet:
        print("=" * 70)
        print("SCB/KTB LINE Mock Notification Generator")
        print("=" * 70)

    if args.dry_run:
        print("\n[DRY RUN] No notification will be sent. Preview:")
        bank_title = BANKS[args.bank]["title"]
        notification_text = generate_line_notification_text(args.amount, args.account)
        print(f"  Title: {bank_title}")
        print(f"  Text: {notification_text}")
        print(f"  Package: {LINE_PACKAGE_NAME}")
        return 0

    # Send the notification
    success = send_mock_notification(
        amount=args.amount,
        bank=args.bank,
        account=args.account,
        device_id=args.device,
        verbose=args.verbose
    )

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())

