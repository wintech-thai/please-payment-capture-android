 # Mock SCB/KTB LINE Notifications - Usage Index

## 📋 Overview

This directory contains a complete system for mocking Thai bank (SCB/KTB) payment notifications via LINE for testing the **Notification Agent** app.

**Status:** ✅ All scripts created, validated, and ready to use

## 🚀 Quick Start in 3 Steps

1. **Validate system setup:**
   ```bash
   python3 scripts/validate_mock_setup.py
   ```

2. **Send your first notification:**
   ```bash
   scripts/mock_notification.sh 1000 SCB X-7985
   ```

3. **Check the Notification Agent app** - you should see a new SCB payment notification!

## 📁 Created Files

### Core Scripts

#### 1. **mock_scb_line_notification.py** (Main)
The primary Python script for sending individual notifications.

**Usage:**
```bash
python3 scripts/mock_scb_line_notification.py --amount 1000 --bank SCB
```

**Features:**
- Send single notifications
- Configurable amount, bank, account
- Verbose/quiet/dry-run modes
- Device auto-detection or manual selection
- Fallback notification methods
- No external dependencies (stdlib only)

**Options:**
```
--amount AMOUNT          (required) Thai Baht amount
--bank {SCB,KTB}       (default: SCB) Bank name
--account ACCOUNT      Account number (optional)
--device DEVICE_ID     ADB device ID (auto-detect)
--verbose              Verbose output
--quiet                Minimal output
--dry-run              Preview without sending
```

**Examples:**
```bash
# Basic SCB notification
python3 scripts/mock_scb_line_notification.py --amount 1000

# Full specification
python3 scripts/mock_scb_line_notification.py --amount 5000 --bank KTB --account XX7157

# For debugging
python3 scripts/mock_scb_line_notification.py --amount 1000 --verbose

# Preview without sending
python3 scripts/mock_scb_line_notification.py --amount 1000 --dry-run
```

---

#### 2. **batch_mock_notifications.py** (Batch Testing)
Send multiple notifications with automatic amount generation or custom lists.

**Usage:**
```bash
python3 scripts/batch_mock_notifications.py --count 10 --bank SCB
```

**Features:**
- Multiple notifications in sequence
- Auto-generated amounts (logarithmic distribution)
- Custom amount lists
- Configurable delays between sends
- Progress tracking
- Batch dry-run mode

**Options:**
```
--count N              (default: 5) Number of notifications
--amounts LIST         Comma-separated amounts (overrides --count)
--min-amount N         (default: 100) Minimum for auto-generation
--max-amount N         (default: 10000) Maximum for auto-generation
--bank {SCB,KTB}      (default: SCB) Bank name
--account ACCOUNT     Account number
--interval SECONDS    (default: 1) Delay between sends
--device DEVICE_ID    ADB device ID
--verbose             Verbose output
--dry-run             Preview without sending
```

**Examples:**
```bash
# 10 notifications with auto amounts
python3 scripts/batch_mock_notifications.py --count 10 --bank SCB

# Specific amounts
python3 scripts/batch_mock_notifications.py --amounts 100,500,1000,5000

# With 2-second intervals
python3 scripts/batch_mock_notifications.py --count 20 --interval 2

# Both banks
python3 scripts/batch_mock_notifications.py --count 5 --bank SCB
python3 scripts/batch_mock_notifications.py --count 5 --bank KTB
```

---

#### 3. **mock_notification.sh** (Shell Wrapper)
Convenient shell wrapper for the Python script with shorter syntax.

**Usage:**
```bash
scripts/mock_notification.sh [AMOUNT] [BANK] [ACCOUNT] [OPTIONS]
```

**Features:**
- Shorter syntax than full Python command
- Smart defaults per bank
- Shell-friendly integration
- Help menu included

**Examples:**
```bash
# Basic usage (defaults to SCB, X-7985)
scripts/mock_notification.sh 1000

# Full specification
scripts/mock_notification.sh 5000 KTB XX7157

# With options
scripts/mock_notification.sh 1000 SCB X-7985 --verbose
scripts/mock_notification.sh 1000 SCB X-7985 --dry-run

# Show help
scripts/mock_notification.sh --help
```

---

#### 4. **validate_mock_setup.py** (Validation)
System validation and diagnostic tool to ensure everything is configured correctly.

**Usage:**
```bash
python3 scripts/validate_mock_setup.py
```

**Checks:**
- ✓ Python 3.6+ installed
- ✓ ADB in PATH
- ✓ Mock scripts present
- ✓ Script permissions (executable)
- ✓ Device connected
- ✓ Dry-run test

**Output:** Shows status of all checks and provides quick start examples

---

#### 5. **advanced_testing_examples.py** (Advanced)
Examples and helpers for advanced testing scenarios.

**Usage:**
```bash
python3 scripts/advanced_testing_examples.py
```

**Tests:**
- Amount range testing (0.01 to 1,000,000 THB)
- Decimal precision validation
- Account format variations
- Both bank comparison
- Rapid-fire sequence testing

---

### Documentation Files

#### 📖 **QUICK_START_MOCKS.md**
One-page quick reference - start here!

**Contents:**
- 30-second setup
- Common recipes
- Quick command reference
- Troubleshooting link

**Read:** `cat scripts/QUICK_START_MOCKS.md` or open in editor

---

#### 📖 **MOCK_NOTIFICATIONS_GUIDE.md**
Comprehensive 60+ page guide with everything you need.

**Contents:**
- Complete prerequisites & setup
- Detailed command reference
- Usage examples (20+)
- Troubleshooting section
- Integration recipes
- Advanced automation

**Best for:** Learning all capabilities and solving issues

---

#### 📖 **IMPLEMENTATION_SUMMARY.md**
Technical overview and implementation details.

**Contents:**
- System architecture & flow diagram
- Message format specification
- Feature comparison
- Use case examples
- Technical details
- Customization guide

**Best for:** Understanding how the system works

---

#### 📖 **QUICK_REFERENCE.md**
(Existing in scripts dir) Original quick reference

---

## 🎯 Use Cases & Quick Links

### Testing Scenarios

| Scenario | Command | Documentation |
|----------|---------|---------------|
| **Single notification** | `scripts/mock_notification.sh 1000` | [Guide](#) |
| **Batch test (10)** | `batch_mock_notifications.py --count 10` | [Guide](#) |
| **Custom amounts** | `batch_mock_notifications.py --amounts 100,500,1000` | [Guide](#) |
| **Load testing (50+)** | `batch_mock_notifications.py --count 50 --interval 5` | [Guide](#) |
| **Both banks** | Send SCB, then KTB separately | [Guide](#) |
| **Specific device** | `--device emulator-5554` | [Guide](#) |
| **Debug output** | `--verbose` flag | [Guide](#) |

### Automation Scenarios

```bash
# Shell loop: daily transactions
for amt in 100 500 1000 5000; do
    scripts/mock_notification.sh $amt --quiet
    sleep 2
done

# Cron job
0 9 * * * cd /path && python3 scripts/batch_mock_notifications.py --count 5 --quiet

# CI/CD pipeline
scripts/mock_notification.sh 1000 && echo "Test passed"
```

### Advanced Scenarios

```bash
# Test amount edge cases
python3 scripts/advanced_testing_examples.py

# Specific amount ranges
python3 scripts/batch_mock_notifications.py \
    --amounts 99.99,100.00,100.01,999.99,1000.00,1000.01

# Performance testing
time python3 scripts/batch_mock_notifications.py --count 100 --interval 0.5
```

## 🔧 Setup & Prerequisites

### Quick Check
```bash
python3 scripts/validate_mock_setup.py
```

### Manual Setup
1. **Python 3.6+**
   ```bash
   python3 --version
   ```

2. **Android SDK Platform Tools (adb)**
   - macOS: `brew install android-platform-tools`
   - Linux: `sudo apt-get install android-tools-adb`
   - Windows: Download from developer.android.com

3. **USB Debugging on Device**
   - Settings → About Phone → Build Number (tap 7x)
   - Settings → Developer Options → USB Debugging (enable)
   - Connect device via USB

4. **Verify Connection**
   ```bash
   adb devices
   ```

## 📊 Notification Format

The scripts generate Thai-language notifications in this format:

```
Title:   "SCB Connect" (for SCB) or "Krungthai Connext" (for KTB)

Text:    "รายการเงินเข้า 1,000.00 บาท เข้าบัญชี X-7985 วันที่ 05/06/2026 14:30"
```

### Parsing Details
- **Bank identification:** Title → "SCB Connect" or "Krungthai Connext"
- **Amount extraction:** Thai pattern with comma separator handling
- **Account detection:** "เข้าบัญชี [ACCOUNT]" format
- **Timestamp:** Thai date/time format

## 🔄 Validation Flow

```
Python/Shell Script
    ↓
ADB Connection
    ↓
Android Device
    ↓
Notification System
    ↓
NotificationCaptureService
    ↓
LineBankPaymentParser
    ├─ Package check: "jp.naver.line.android" ✓
    ├─ Bank detection: "SCB Connect" → SCB ✓
    ├─ Amount parsing: "เงินเข้า 1,000.00" ✓
    ├─ Account extract: "เข้าบัญชี X-7985" ✓
    ↓
Message stored in Database
    ↓
Visible in Notification Agent UI ✓
```

## 🐛 Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| `adb: command not found` | Install Android SDK Platform Tools |
| `No devices found` | Enable USB Debugging, connect via USB |
| `Permission denied` | `chmod +x scripts/*.py scripts/*.sh` |
| Notification not appearing | Check: Listener enabled, Filter rules, Device logs |
| Script errors | Run with `--verbose` for details |

**Full troubleshooting:** See `MOCK_NOTIFICATIONS_GUIDE.md`

## 📝 Examples

### Example 1: Single Notification
```bash
$ scripts/mock_notification.sh 5555 SCB X-7985

======================================================================
SCB/KTB LINE Mock Notification Generator
======================================================================
...
Sending mock SCB LINE notification to emulator-5554...
✓ Sent visible notification
```

### Example 2: Batch Testing
```bash
$ python3 scripts/batch_mock_notifications.py --count 3 --interval 2

======================================================================
Batch SCB/KTB LINE Mock Notification Generator
======================================================================
Bank: SCB
Account: X-7985
Notifications: 3
Interval: 2.0s
Amounts: 100.00, 1000.00, 10000.00

[1/3] Sending 100.00 THB... ✓
[2/3] Sending 1000.00 THB... ✓
[3/3] Sending 10000.00 THB... ✓

======================================================================
Results: 3 sent, 0 failed
======================================================================
```

### Example 3: Dry Run
```bash
$ scripts/mock_notification.sh 1000 --dry-run

[DRY RUN] No notification will be sent. Preview:
  Title: SCB Connect
  Text: รายการเงินเข้า 1,000.00 บาท เข้าบัญชี X-7985 วันที่ 05/06/2026 11:14
  Package: jp.naver.line.android
```

## 🎓 Learning Path

1. **Start here:** `QUICK_START_MOCKS.md`
2. **First test:** `scripts/mock_notification.sh 1000`
3. **Learn all options:** `MOCK_NOTIFICATIONS_GUIDE.md`
4. **Advanced usage:** `advanced_testing_examples.py`
5. **Understand system:** `IMPLEMENTATION_SUMMARY.md`

## 📞 Support

- **Quick issues:** Check `QUICK_START_MOCKS.md`
- **Detailed help:** See `MOCK_NOTIFICATIONS_GUIDE.md` → Troubleshooting
- **System check:** Run `python3 scripts/validate_mock_setup.py`
- **Device logs:** `adb logcat | grep NotificationAgent`

## ✅ Created Files Summary

```
scripts/
├── mock_scb_line_notification.py       ← Main script
├── batch_mock_notifications.py          ← Batch testing
├── mock_notification.sh                 ← Shell wrapper
├── validate_mock_setup.py               ← System validation
├── advanced_testing_examples.py         ← Advanced tests
├── QUICK_START_MOCKS.md                 ← Quick reference
├── MOCK_NOTIFICATIONS_GUIDE.md          ← Full guide (60+ pages)
├── IMPLEMENTATION_SUMMARY.md            ← Technical details
└── README_MOCK_SCRIPTS.md               ← This file
```

## 🎯 Next Steps

1. **Validate:** `python3 scripts/validate_mock_setup.py`
2. **Test:** `scripts/mock_notification.sh 1000`
3. **Verify:** Open Notification Agent app and check Messages tab
4. **Explore:** Read `QUICK_START_MOCKS.md` or `MOCK_NOTIFICATIONS_GUIDE.md`
5. **Automate:** Use in CI/CD or shell scripts

---

**System Status:** ✅ Ready to use  
**Last Updated:** 2026-06-05  
**Python Version:** 3.6+  
**Tested On:** Android 11-15

