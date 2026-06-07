# Mock SCB/KTB LINE Bank Notifications - User Guide

This guide explains how to use the mock notification scripts to test the Notification Agent app with realistic SCB/KTB LINE bank payment notifications.

## Overview

The Notification Agent app captures notifications from other apps. These scripts allow you to:

1. **Generate realistic SCB/KTB LINE bank notifications** with configurable amounts
2. **Send them to your Android device** via ADB (Android Debug Bridge)
3. **Verify the app captures and processes them correctly**
4. **Batch test with multiple amounts** to validate filtering rules and parsing

## Prerequisites

### Required:
- **Android device** (or emulator) with Android 11+ connected via USB
- **ADB** (Android Debug Bridge) - part of Android SDK Platform Tools
- **Python 3.6+** for running the test scripts
- **Notification Agent app** installed and running on the device

### Setup Instructions

#### 1. Install Android SDK Platform Tools (if needed)

**macOS (Homebrew):**
```bash
brew install android-platform-tools
```

**macOS (Manual):**
```bash
# Download from: https://developer.android.com/tools/releases/platform-tools
# Extract and add to PATH:
export PATH="$PATH:/path/to/platform-tools"
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get install android-tools-adb
```

**Windows:**
- Download from: https://developer.android.com/tools/releases/platform-tools
- Extract and add to PATH or use the full path to adb.exe

#### 2. Enable USB Debugging on Your Android Device

1. Open **Settings** → **About Phone**
2. Tap **Build Number** 7 times to enable Developer Options
3. Go back to **Settings** → **System** → **Developer Options** (or **Advanced**)
4. Enable **USB Debugging**
5. Connect your device via USB cable
6. Approve the "Allow USB debugging?" prompt on your device

#### 3. Verify ADB Connection

```bash
adb devices
```

You should see your device listed:
```
List of attached devices
emulator-5554          device
# or:
192.168.1.100:5555    device
```

## Usage

### Quick Start

Send a single notification:

```bash
# Basic usage (uses default account for SCB: X-7985)
python3 scripts/mock_scb_line_notification.py --amount 1000 --bank SCB

# Or use the shell wrapper for shorter syntax:
scripts/mock_notification.sh 1000 SCB
```

### Common Use Cases

#### 1. Send SCB Notification with Custom Account

```bash
# 50,000 THB to account X-7985
scripts/mock_notification.sh 50000 SCB X-7985

# Verbose output for debugging
scripts/mock_notification.sh 50000 SCB X-7985 --verbose
```

#### 2. Send KTB Notification

```bash
# 5,000 THB KTB payment (default account: XX7157)
scripts/mock_notification.sh 5000 KTB XX7157

# Or use full Python script
python3 scripts/mock_scb_line_notification.py --amount 5000 --bank KTB --account XX7157
```

#### 3. Send to Specific Device

```bash
# If you have multiple devices/emulators
scripts/mock_notification.sh 1000 SCB X-7985 --device emulator-5554
```

#### 4. Preview Without Sending (Dry Run)

```bash
# See what would be sent without actually sending
scripts/mock_notification.sh 1000 SCB X-7985 --dry-run
```

#### 5. Batch Testing with Multiple Amounts

```bash
# Send 10 notifications with log-distributed amounts
python3 scripts/batch_mock_notifications.py --count 10 --bank SCB --account X-7985

# Send specific amounts
python3 scripts/batch_mock_notifications.py --amounts 100,500,1000,5000,10000 --bank KTB

# Send with 3 second interval between each
python3 scripts/batch_mock_notifications.py --count 20 --bank SCB --interval 3

# With verbose output
python3 scripts/batch_mock_notifications.py --count 5 --bank SCB --verbose
```

#### 6. Manual Shell Loop

```bash
# Send 5 notifications at different amounts
for amt in 100 500 1000 5000 10000; do
    scripts/mock_notification.sh $amt SCB X-7985 --quiet
    sleep 2  # Wait 2 seconds between sends
done
```

### Command Reference

#### Main Script: `mock_scb_line_notification.py`

```bash
python3 scripts/mock_scb_line_notification.py \
    --amount AMOUNT          # Required: Amount in THB (e.g., 1000, 50000.50)
    --bank BANK              # Optional: SCB or KTB (default: SCB)
    --account ACCOUNT        # Optional: Account number (default: X-7985 for SCB, XX7157 for KTB)
    --device DEVICE_ID       # Optional: ADB device ID (auto-detect if not specified)
    --verbose                # Optional: Enable verbose output
    --quiet                  # Optional: Minimal output
    --dry-run                # Optional: Show what would be sent
```

#### Batch Script: `batch_mock_notifications.py`

```bash
python3 scripts/batch_mock_notifications.py \
    --count COUNT            # Number of notifications (default: 5)
    --amounts AMOUNTS        # Comma-separated list (overrides --count)
    --min-amount MIN         # Minimum amount for auto-generation (default: 100)
    --max-amount MAX         # Maximum amount for auto-generation (default: 10000)
    --bank BANK              # SCB or KTB (default: SCB)
    --account ACCOUNT        # Account number
    --interval SECONDS       # Delay between sends (default: 1)
    --device DEVICE_ID       # ADB device ID
    --verbose                # Verbose output
    --dry-run                # Preview without sending
```

#### Shell Wrapper: `mock_notification.sh`

```bash
scripts/mock_notification.sh [AMOUNT] [BANK] [ACCOUNT] [OPTIONS]

# Quick examples:
scripts/mock_notification.sh 1000                    # SCB, 1000 THB, default account
scripts/mock_notification.sh 1000 SCB X-7985         # Full specification
scripts/mock_notification.sh 5000 KTB XX7157         # KTB bank
scripts/mock_notification.sh 1000 SCB X-7985 --dry-run
```

## How It Works

### Notification Format

The scripts generate Thai-language bank notifications in this format:

```
Title:  "SCB Connect" (or "Krungthai Connext" for KTB)

Text:   "รายการเงินเข้า 1,000.00 บาท เข้าบัญชี X-7985 วันที่ 05/06/2026 14:30"
         └─────────────── Amount ──────────┘ └─ Account ─┘         └─ Timestamp ─┘
```

### Message Parsing

The app automatically:
1. ✓ Detects notifications from LINE app (`jp.naver.line.android`)
2. ✓ Extracts the title to identify the bank (SCB vs KTB)
3. ✓ Parses the Thai amount (handles comma separators)
4. ✓ Extracts the account number
5. ✓ Stores in local database with timestamp
6. ✓ Applies filter rules to determine if message is captured

### Supported Banks

| Bank | Title | Account Format | Default Account |
|------|-------|-----------------|------------------|
| **SCB** | SCB Connect | X-XXXX | X-7985 |
| **KTB** | Krungthai Connext | XX9999 | XX7157 |

## Verification

After sending a notification, you can verify it was captured:

1. Open the **Notification Agent** app
2. Navigate to the **Messages** tab
3. Look for the recently added message
4. The message should show:
   - Source: LINE
   - Title: Bank name (SCB Connect / Krungthai Connext)
   - Text: Payment details in Thai
   - Timestamp: When the notification was sent

### Expected Filter Behavior

- **By default:** All LINE notifications are captured (filter rule enabled)
- **If disabled:** No LINE notifications are captured
- **With custom rule:** Only messages matching the rule are captured

## Troubleshooting

### ADB Device Not Found

```
ERROR: No ADB device found.
```

**Solutions:**
```bash
# Check if device is connected
adb devices

# Try reconnecting USB
# 1. Disconnect USB cable
# 2. Reconnect USB cable
# 3. Approve "Allow USB debugging?" on device

# Or use TCP connection (if both on same network)
adb connect 192.168.1.100:5555

# Restart ADB server
adb kill-server
adb start-server
```

### Script Not Executable

```
Permission denied: ./mock_notification.sh
```

**Solution:**
```bash
chmod +x scripts/mock_scb_line_notification.py
chmod +x scripts/batch_mock_notifications.py
chmod +x scripts/mock_notification.sh
```

### Python Module Not Found

```
ModuleNotFoundError: No module named 'subprocess'
```

**Solution:**
- Ensure Python 3.6+ is installed
- Try: `python3 --version`

### Notification Not Appearing in App

**Possible causes & solutions:**

1. **Filter rule disabled:**
   - Open app → Notification Filters → check if LINE rule is enabled

2. **Notification listener permission:**
   - Settings → Apps → Notification Agent → Permissions → Notification access → Enable

3. **App in background:**
   - Bring app to foreground
   - Or check Recent Messages list

4. **Network/webhook issues (if configured):**
   - Check app logs: `adb logcat NotificationCaptureService`

### View Debug Logs

```bash
# Real-time logs from the app
adb logcat | grep "NotificationAgent"

# Save logs to file
adb logcat > notification_logs.txt

# Clear previous logs then capture new
adb logcat -c
scripts/mock_notification.sh 1000
adb logcat | grep "NotificationAgent"
```

## Configuration Management

### Filter Rules

The app stores notification filter rules in Room database. Default behavior:

- **LINE (SCB/KTB):** Enabled by default
- **SMS:** Can be enabled/disabled per-sender
- **Custom rules:** Create in Settings → Filters

### Bank Forwarding (if configured)

If bank webhook endpoints are configured in the app:

1. Captured SCB/KTB notifications are sent to configured webhook
2. Payload includes: bank name, amount, source account
3. See [Bank Configuration Guide](../README.md#bank-configuration) for details

## Examples & Recipes

### Load Testing

```bash
# Send 100 notifications over 5 minutes
python3 scripts/batch_mock_notifications.py \
    --count 100 \
    --bank SCB \
    --account X-7985 \
    --interval 3 \
    --quiet
```

### Testing Multiple Accounts

```bash
# Send to different SCB accounts
for acct in X-7985 X-1234 X-9876; do
    scripts/mock_notification.sh 5000 SCB $acct
    sleep 1
done

# Or KTB with different accounts
for acct in XX7157 XX0001 XX0002; do
    scripts/mock_notification.sh 5000 KTB $acct
    sleep 1
done
```

### Testing Both Banks

```bash
# Compare SCB vs KTB parsing
echo "Testing SCB..."
scripts/mock_notification.sh 1000 SCB X-7985 --verbose

sleep 2

echo "Testing KTB..."
scripts/mock_notification.sh 1000 KTB XX7157 --verbose
```

### Real-world Amount Patterns

```bash
# Typical daily transactions
python3 scripts/batch_mock_notifications.py \
    --amounts 45.50,125.00,850.00,2500.00,15000.00 \
    --bank SCB

# Large transfers
python3 scripts/batch_mock_notifications.py \
    --amounts 500000,1000000,5000000 \
    --bank SCB --account X-7985
```

### Automated Integration Testing

```bash
#!/bin/bash
# Save as: test_notification_capture.sh

PASS=0
FAIL=0

test_amount() {
    local amt=$1
    local bank=$2
    local acct=$3
    
    echo -n "Testing $amt THB ($bank)... "
    
    if python3 scripts/mock_scb_line_notification.py \
        --amount $amt --bank $bank --account $acct \
        2>/dev/null | grep -q "successfully"; then
        echo "✓"
        ((PASS++))
    else
        echo "✗"
        ((FAIL++))
    fi
}

# Run tests
test_amount 100 SCB X-7985
test_amount 1000 SCB X-7985
test_amount 5000 KTB XX7157

echo ""
echo "Results: $PASS passed, $FAIL failed"
```

## Advanced: Custom Amount Formulas

To generate custom amounts programmatically:

```python
#!/usr/bin/env python3
import subprocess
import sys

# Examples of custom amount generation

# Fibonacci sequence
def fibonacci_amounts(count):
    amounts = [1, 1]
    for _ in range(count - 2):
        amounts.append(amounts[-1] + amounts[-2])
    return amounts

# Rounded amounts (common in payments)
def rounded_amounts(count):
    return [round(100 * (i + 1) / 10) * 100 for i in range(count)]

# Test with Fibonacci
for amount in fibonacci_amounts(5):
    subprocess.run([
        "python3", "scripts/mock_scb_line_notification.py",
        "--amount", str(amount),
        "--bank", "SCB",
        "--quiet"
    ])
```

## Support & Issues

If you encounter problems:

1. **Check the troubleshooting section** above
2. **Run with `--verbose` flag** for detailed output
3. **Check device logs:** `adb logcat`
4. **Check app settings:** Open Notification Agent → Settings → Filters
5. **Verify ADB connection:** `adb devices`

---

**Last Updated:** 2026-06-05  
**Tested on:** Android 11, 12, 13, 14, 15  
**Python Version:** 3.6+

