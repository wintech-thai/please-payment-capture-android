#!/usr/bin/env python3
"""
Setup and Test SCB Bank Webhook Integration on Emulator

This Guide explains how to test the SCB LINE payment notifications
and verify they are forwarded to your configured bank endpoint.
"""

# ============================================================================
# STEP 1: ENABLE NOTIFICATION LISTENER PERMISSION ON EMULATOR
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 1: Enable Notification Listener Permission on Emulator          ║
╚════════════════════════════════════════════════════════════════════════╝

The app needs permission to listen to notifications from other apps (LINE).

Method 1 (Recommended - Using ADB):
──────────────────────────────────
1. Run this command to grant the permission:
   
   adb shell pm grant com.example.notification_agent \\
     android.permission.BIND_NOTIFICATION_LISTENER_SERVICE

2. Verify it was granted:
   
   adb shell pm list permissions com.example.notification_agent | grep -i bind

Method 2 (Manual - On Emulator):
────────────────────────────────
1. Open emulator Settings app
2. Search for "Notification Agent" 
3. Go to Notifications → Notification access
4. Enable "Notification Agent"

OR

1. Settings → Apps → See all apps → Notification Agent
2. Tap "Permissions"
3. Look for "Notification" or "Notification Access"
4. Enable it

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 2: VERIFY SCB BANK ENDPOINT CONFIGURATION
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 2: Verify SCB Bank Endpoint is Configured                        ║
╚════════════════════════════════════════════════════════════════════════╝

You mentioned you've configured the SCB bank endpoint. Let's verify it.

1. Open Notification Agent app on emulator
2. Navigate to "Bank Config" (settings/menu)
3. Look for SCB entry:
   - Bank: SCB
   - Endpoint URL: your_webhook_url (e.g., http://10.0.2.2:8000/webhook)
   - API Key: (optional, if configured)
   - Enabled: ✓ (checked)

4. If you see this configuration, the app is ready to forward payments.

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 3: TEST THE END-TO-END FLOW
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 3: Send Mock SCB Payment and Monitor Webhook                    ║
╚════════════════════════════════════════════════════════════════════════╝

Now let's send a test notification and verify it gets forwarded.

A. First, start monitoring your webhook endpoint:

   If using localhost/ngrok:
   
   # Listen for webhook calls
   nc -l 0.0.0.0 8000
   
   OR use Python:
   
   python3 -m http.server 8000

B. Send mock SCB payment from your Mac:

   python3 scripts/mock_scb_line_notification.py \\
     --amount 5555 \\
     --bank SCB \\
     --account X-7985 \\
     --verbose

C. Watch for these events:

   1. Notification posted to emulator (terminal output shows ✓)
   2. App captures it in Messages tab
   3. Webhook receives POST request with payment data

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 4: MONITOR WHAT HAPPENS
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 4: Monitor the Flow Using Logs and App UI                       ║
╚════════════════════════════════════════════════════════════════════════╝

A. Check Emulator Logs:

   # Real-time logs
   adb logcat -c
   python3 scripts/mock_scb_line_notification.py --amount 1000 --quiet
   adb logcat | grep -i "bank\|webhook\|forward\|notification_agent" &
   
   LOOK FOR:
   ✓ "Captured message" - notification was parsed
   ✓ "Sending webhook" - forwarding to bank endpoint
   ✓ "Bank forward" - result of webhook call

B. Check App UI:

   1. Open Notification Agent app
   2. Messages tab - Should show new SCB payment
   3. Click on message - See parsed details:
      - Bank: SCB
      - Amount: 1,000.00 THB
      - Account: X-7985
      - Timestamp: When sent

C. Check Webhook Endpoint:

   If listening on port 8000:
   
   You should see POST request like:
   
   POST /webhook HTTP/1.1
   Content-Type: application/json
   
   {
     "PaymentAmount": "1000.00",
     "RemainAmount": "0.00",
     "TxType": "PayIn",
     "SourceBankCode": "SCB",
     "SourceBankAccountNo": "X-7985",
     "DestinationBankCode": "TMB",
     "DestinationAccountNo": "XX-0032"
   }

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 5: BATCH TESTING
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 5: Batch Testing Multiple Amounts                               ║
╚════════════════════════════════════════════════════════════════════════╝

Test various amounts to ensure proper parsing:

python3 scripts/batch_mock_notifications.py \\
  --amounts 100,500,1000,5000,10000 \\
  --bank SCB \\
  --account X-7985 \\
  --interval 2

Expected Results:
- 5 notifications sent (one every 2 seconds)
- App should capture each one
- Each should be forwarded to webhook with correct amount
- All appear in Messages tab

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 6: TROUBLESHOOTING
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 6: Troubleshooting                                              ║
╚════════════════════════════════════════════════════════════════════════╝

ISSUE: Permission denied for NotificationCaptureService
SOLUTION:
  adb shell pm grant com.example.notification_agent \\
    android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
  adb shell settings put secure enabled_notification_listeners \\
    com.example.notification_agent/.service.NotificationCaptureService

ISSUE: Notification not appearing in Messages
SOLUTION:
  1. Check notification listener is enabled (Settings)
  2. Check filter rules are not blocking (Messages → Filters)
  3. Try granting permission via UI:
     Settings → Apps → Notification Agent → Permissions

ISSUE: Webhook not being called
SOLUTION:
  1. Check endpoint URL is correct in Bank Config
  2. Check endpoint is running and listening
  3. Check internet connectivity in emulator
  4. For localhost, use: http://10.0.2.2:PORT (not localhost)

ISSUE: Webhook receives request but amount is wrong
SOLUTION:
  Check the parsing in LineBankPaymentParser.kt
  Look for: "เงินเข้า" (incoming payment) pattern

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 7: QUICK COMMANDS REFERENCE
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 7: Useful Commands                                              ║
╚════════════════════════════════════════════════════════════════════════╝

# Enable notification listener permission
adb shell pm grant com.example.notification_agent \\
  android.permission.BIND_NOTIFICATION_LISTENER_SERVICE

# List connected devices
adb devices

# Clear app data
adb shell pm clear com.example.notification_agent

# View all notifications (rich view)
adb shell settings get secure enabled_notification_listeners

# Send 5555 THB test
python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose

# Batch test
python3 scripts/batch_mock_notifications.py --count 5 --bank SCB

# View logs
adb logcat | grep -i "notification_agent\|bank\|webhook"

# Forward ports (if webhook on localhost)
adb reverse tcp:8000 tcp:8000

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 8: EXPECTED WEBHOOK PAYLOAD
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 8: Expected Webhook Payload Format                              ║
╚════════════════════════════════════════════════════════════════════════╝

When you send a mock SCB payment notification, the app will forward
this JSON payload to your configured bank endpoint:

{
  "PaymentAmount": "5555.00",
  "RemainAmount": "0.00",
  "TxType": "PayIn",
  "DestinationBankCode": "TMB",
  "DestinationAccountNo": "XX-0032",
  "SourceBankCode": "SCB",
  "SourceBankAccountNo": "X-7985"  ← Your account
}

Field Mapping:
  - PaymentAmount:  Extracted from "เงินเข้า XXX" in notification
  - SourceBankCode: "SCB" (from title "SCB Connect")
  - SourceBankAccountNo: "X-7985" (from "เข้าบัญชี X-7985")
  - DestinationBankCode: Hardcoded as "TMB"
  - DestinationAccountNo: Hardcoded as "XX-0032"
  - TxType: Always "PayIn" (incoming payment)

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# STEP 9: FULL TESTING WORKFLOW
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  STEP 9: Complete Testing Workflow                                    ║
╚════════════════════════════════════════════════════════════════════════╝

1. SETUP (5 min):
   ✓ Grant notification listener permission to app
   ✓ Verify SCB endpoint is configured in Bank Config
   ✓ Start webhook server/monitor (nc or Python)

2. SEND TEST (2 min):
   ✓ Run: python3 scripts/mock_scb_line_notification.py \\
           --amount 1000 --bank SCB --verbose

3. VERIFY IN APP (1 min):
   ✓ Open Notification Agent app
   ✓ Go to Messages tab
   ✓ See new SCB payment entry

4. VERIFY WEBHOOK (1 min):
   ✓ Check webhook server received POST request
   ✓ Verify amount is correct in payload
   ✓ Verify account number is correct

5. BATCH TESTING (5 min):
   ✓ Run batch script with multiple amounts
   ✓ Verify each generates webhook call
   ✓ Check logs for any errors

Total Time: ~15 minutes

═══════════════════════════════════════════════════════════════════════════
""")

# ============================================================================
# QUICK START
# ============================================================================

print("""
╔════════════════════════════════════════════════════════════════════════╗
║  🚀 QUICK START - Run These Commands Now                              ║
╚════════════════════════════════════════════════════════════════════════╝

Terminal 1 - Grant permission:
  adb shell pm grant com.example.notification_agent \\
    android.permission.BIND_NOTIFICATION_LISTENER_SERVICE

Terminal 2 - Listen for webhooks:
  nc -l 0.0.0.0 8000

Terminal 3 - Send test notification:
  python3 scripts/mock_scb_line_notification.py \\
    --amount 5555 --bank SCB --account X-7985 --verbose

Terminal 1 - Watch logs:
  adb logcat | grep -i "bank\|webhook\|forward"

═══════════════════════════════════════════════════════════════════════════

Done! Your SCB notification mock system is ready to test.
""")

