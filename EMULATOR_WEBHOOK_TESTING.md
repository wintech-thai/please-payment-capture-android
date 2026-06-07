# Testing SCB Bank Webhook Integration on Emulator

## Quick Start (5 Minutes)

### Terminal 1: Start Webhook Monitor
```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
python3 scripts/emulator_webhook_test.py --server
```
You'll see: `✓ Webhook server is running!`

### Terminal 2: Setup Emulator
```bash
# Enable notification listener
adb shell settings put secure enabled_notification_listeners \
  "com.example.notification_agent/.service.NotificationCaptureService"

echo "✓ Notification listener enabled"
```

### Terminal 3: Send Test Notification
```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
python3 scripts/mock_scb_line_notification.py \
  --amount 5555 \
  --bank SCB \
  --account X-7985 \
  --verbose
```

### Expected Result
Your Terminal 1 (webhook monitor) will show:
```
✓ WEBHOOK RECEIVED!
Payload:
{
  "PaymentAmount": "5555.00",
  "SourceBankCode": "SCB",
  "SourceBankAccountNo": "X-7985"
  ...
}
```

---

## Complete Testing Workflow

### 1. **Configure SCB Endpoint in App** (One Time)
   - Open Notification Agent app on emulator
   - Go to Bank Config / Settings
   - Add SCB entry:
     - **Bank:** SCB
     - **Endpoint:** `http://10.0.2.2:8000/webhook`  ⚠️ Use 10.0.2.2, not localhost
     - **Enabled:** ✓

### 2. **Enable System Notification Listener** (One Time)
```bash
adb shell settings put secure enabled_notification_listeners \
  "com.example.notification_agent/.service.NotificationCaptureService"
```

### 3. **Start Webhook Monitor**
```bash
python3 scripts/emulator_webhook_test.py --server
```

### 4. **Send Mock SCB Notification**
```bash
# Single notification
python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose

# Or batch test
python3 scripts/batch_mock_notifications.py \
  --amounts 100,500,1000,5000 \
  --bank SCB \
  --interval 1
```

### 5. **Verify in Multiple Places**

**A. Webhook Server (Terminal 1)** - Should show POST with JSON payload

**B. App UI (Emulator screen)**
- Open Notification Agent
- Go to **Messages** tab
- See new SCB payment notification
- Click to view parsed details (amount, account, etc.)

**C. Device Logs**
```bash
adb logcat | grep -i "bank\|webhook\|forward"
```

---

## What Gets Sent to Webhook

When you send a 5,555 THB SCB payment notification, your webhook receives:

```json
{
  "PaymentAmount": "5555.00",
  "RemainAmount": "0.00",
  "TxType": "PayIn",
  "DestinationBankCode": "TMB",
  "DestinationAccountNo": "XX-0032",
  "SourceBankCode": "SCB",
  "SourceBankAccountNo": "X-7985"
}
```

**Key Fields:**
- `PaymentAmount` - Extracted from Thai notification text
- `SourceBankCode` - SCB (from notification title)
- `SourceBankAccountNo` - Your account (from notification)
- `DestinationBankCode` - TMB (hardcoded)
- `DestinationAccountNo` - XX-0032 (hardcoded)

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Webhook not receiving calls | Check endpoint in app uses `http://10.0.2.2:PORT` not `localhost` |
| Amount in webhook is wrong | Check Thai text in notification (should contain "เงินเข้า XXX") |
| Notification not in app | Enable notification listener: `adb shell settings put...` |
| Permission errors | Run: `bash scripts/setup_emulator.sh` |
| Emulator can't reach host | Try: `adb shell ping 10.0.2.2` |

---

## All Available Commands

```bash
# Webhook monitoring
python3 scripts/emulator_webhook_test.py --server

# Single notification (various amounts)
python3 scripts/mock_scb_line_notification.py --amount 1000
python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose
python3 scripts/mock_scb_line_notification.py --amount 10000 --dry-run

# Batch testing
python3 scripts/batch_mock_notifications.py --count 10
python3 scripts/batch_mock_notifications.py --amounts 100,500,1000,5000

# KTB testing
python3 scripts/mock_scb_line_notification.py --amount 3000 --bank KTB --account XX7157

# System setup
bash scripts/setup_emulator.sh
adb shell settings put secure enabled_notification_listeners \
  "com.example.notification_agent/.service.NotificationCaptureService"

# Device info
adb devices
adb logcat | grep notification_agent
adb shell pm list permissions com.example.notification_agent
```

---

## File Reference

| File | Purpose |
|------|---------|
| `mock_scb_line_notification.py` | Send single SCB/KTB notifications |
| `batch_mock_notifications.py` | Send multiple notifications |
| `emulator_webhook_test.py` | Webhook monitor and testing guide |
| `setup_emulator.sh` | Auto-setup permissions |
| EMULATOR_TESTING_GUIDE.md | Detailed testing steps |

---

## Expected Results

### ✓ Success Indicators

1. **Terminal shows**: `✓ Sent visible notification`
2. **Webhook monitor shows**: `✓ WEBHOOK RECEIVED!` with JSON payload
3. **App shows**: New message in Messages tab
4. **Amount is correct**: 5555.00 → 5,555.00 in JSON

### ✗ Common Issues

1. **No webhook call** → Check endpoint URL (use 10.0.2.2)
2. **Notification not in app** → Enable notification listener
3. **Wrong amount in webhook** → Check Thai text parsing
4. **Permission denied** → Run setup script

---

## That's It!

You now have a complete testing system to verify SCB bank webhook integration on the Android emulator.

**To start testing right now:**

```bash
# Terminal 1
python3 scripts/emulator_webhook_test.py --server

# Terminal 2  
python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose
```

Watch for the webhook notification in Terminal 1! 🎯

