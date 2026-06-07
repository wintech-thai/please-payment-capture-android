# SCB/KTB LINE Mock Notifications - Quick Reference Card

## 🚀 30-Second Start

```bash
# 1. Check system is ready
python3 scripts/validate_mock_setup.py

# 2. Send first notification
scripts/mock_notification.sh 1000

# 3. Check app for new message
# (Open Notification Agent → Messages)
```

---

## 📋 Command Cheat Sheet

### SINGLE NOTIFICATION (Most Common)

```bash
# Default: SCB, 1000 THB, account X-7985
scripts/mock_notification.sh 1000

# Custom: KTB, 5000 THB, specific account
scripts/mock_notification.sh 5000 KTB XX7157

# Advanced: Verbose output
scripts/mock_notification.sh 1000 --verbose

# Preview: See what would be sent
scripts/mock_notification.sh 1000 --dry-run
```

### BATCH TESTING

```bash
# 10 notifications (auto-generated amounts)
python3 scripts/batch_mock_notifications.py --count 10

# Specific amounts
python3 scripts/batch_mock_notifications.py \
  --amounts 100,500,1000,5000

# With 2-second delays
python3 scripts/batch_mock_notifications.py \
  --count 20 --interval 2
```

### SYSTEM TOOLS

```bash
# Validate setup
python3 scripts/validate_mock_setup.py

# Advanced tests (edge cases)
python3 scripts/advanced_testing_examples.py

# Device list
adb devices
```

---

## 🎯 Common Scenarios

| Scenario | Command |
|----------|---------|
| Quick test | `scripts/mock_notification.sh 1000` |
| Different bank | `scripts/mock_notification.sh 5000 KTB XX7157` |
| Large amount | `scripts/mock_notification.sh 100000` |
| Many tests | `python3 scripts/batch_mock_notifications.py --count 10` |
| Specific amounts | `python3 scripts/batch_mock_notifications.py --amounts 100,500,1000` |
| Debug output | `scripts/mock_notification.sh 1000 --verbose` |
| Preview | `scripts/mock_notification.sh 1000 --dry-run` |

---

## 📚 Documentation

| Need | File |
|------|------|
| Quick reference | `QUICK_START_MOCKS.md` |
| Full guide | `MOCK_NOTIFICATIONS_GUIDE.md` |
| Technical | `IMPLEMENTATION_SUMMARY.md` |
| Navigation | `README_MOCK_SCRIPTS.md` |

---

## 🏦 Bank Details

### SCB Connect
- **Title:** "SCB Connect"
- **Account Format:** X-XXXX
- **Default Account:** X-7985
- **Example:** `scripts/mock_notification.sh 1000 SCB X-7985`

### KTB Connext
- **Title:** "Krungthai Connext"
- **Account Format:** XXXXX
- **Default Account:** XX7157
- **Example:** `scripts/mock_notification.sh 5000 KTB XX7157`

---

## 💻 Basic Python/Shell Usage

### Pure Python
```bash
python3 scripts/mock_scb_line_notification.py \
  --amount 1000 \
  --bank SCB \
  --account X-7985
```

### With Options
```bash
python3 scripts/mock_scb_line_notification.py \
  --amount 5000 \
  --bank KTB \
  --account XX7157 \
  --verbose          # Show details
  --quiet            # No output
  --dry-run          # Preview
  --device DEVICE_ID # Specific device
```

### Shell Commands
```bash
# Help
scripts/mock_notification.sh --help

# List devices
adb devices

# View logs
adb logcat | grep NotificationAgent

# Restart ADB
adb kill-server
adb start-server
```

---

## ⚡ Advanced Usage

### Shell Loop (Send Multiple)
```bash
for amt in 100 500 1000 5000 10000; do
  scripts/mock_notification.sh $amt --quiet
  sleep 2
done
```

### Rapid Testing
```bash
python3 scripts/batch_mock_notifications.py \
  --amounts 100,200,300,400,500 \
  --interval 0.5
```

### Different Devices
```bash
# List all devices
adb devices

# Send to specific device
scripts/mock_notification.sh 1000 -d emulator-5554
```

---

## 🔍 Troubleshooting

| Problem | Solution |
|---------|----------|
| **adb not found** | `brew install android-platform-tools` |
| **Device not found** | Enable USB Debugging, connect device |
| **Permission denied** | `chmod +x scripts/*.py scripts/*.sh` |
| **Notification missing** | Check app filter rules |
| **Script fails** | Run `python3 scripts/validate_mock_setup.py` |

**Full help:** `scripts/MOCK_NOTIFICATIONS_GUIDE.md` → Troubleshooting

---

## 📍 File Locations

All scripts are in:
```
/Users/linus/AndroidStudioProjects/notificationagent/scripts/
```

Executable scripts:
- `mock_scb_line_notification.py` (main)
- `batch_mock_notifications.py` (batch)
- `mock_notification.sh` (shell)
- `validate_mock_setup.py` (validation)
- `advanced_testing_examples.py` (advanced)

---

## 🎨 Message Format

Scripts generate messages like:

```
Title:  "SCB Connect"

Text:   "รายการเงินเข้า 5,555.00 บาท เข้าบัญชี X-7985 วันที่ 05/06/2026 11:14"
```

Parsed as:
- Bank: SCB ← From title
- Amount: 5,555.00 ← From Thai text
- Account: X-7985 ← From account field
- Time: Current timestamp

---

## ✅ Verification

After sending notification:

1. **Check terminal:** Look for ✓ symbol
2. **Open app:** Navigate to Messages tab
3. **Look for:** "SCB Connect" or "Krungthai Connext" message
4. **See details:** Should show amount and account

---

## 🎯 Next Steps

1. **Right now:** `scripts/mock_notification.sh 1000`
2. **Then:** Check app for notification
3. **If ok:** Read `QUICK_START_MOCKS.md`
4. **Then:** Try batch: `python3 scripts/batch_mock_notifications.py --count 10`

---

## 💬 Getting Help

1. **Quick question?** → `cat scripts/QUICK_START_MOCKS.md`
2. **Need details?** → `cat scripts/MOCK_NOTIFICATIONS_GUIDE.md`
3. **System issue?** → `python3 scripts/validate_mock_setup.py`
4. **Lost?** → `cat scripts/README_MOCK_SCRIPTS.md`

---

## 🔗 Quick Links

| Resource | Path |
|----------|------|
| Main guide | `scripts/MOCK_NOTIFICATIONS_GUIDE.md` |
| Quick ref | `scripts/QUICK_START_MOCKS.md` |
| Technical | `scripts/IMPLEMENTATION_SUMMARY.md` |
| Navigation | `scripts/README_MOCK_SCRIPTS.md` |

---

## ✨ Key Features at a Glance

- ✅ Send real-looking Thai bank notifications
- ✅ SCB & KTB support
- ✅ Any amount (0.01 → 1,000,000+ THB)
- ✅ Custom accounts and timestamps
- ✅ Batch processing
- ✅ Dry-run preview
- ✅ Verbose debugging
- ✅ No dependencies needed
- ✅ Production-ready

---

**Status:** ✅ Ready to use | **Version:** 1.0 | **Last Updated:** 2026-06-05

