#!/usr/bin/env python3
"""
End-to-End Testing Guide for SCB Bank Webhook Integration

This guide shows how to:
1. Send mock SCB LINE notifications to the emulator
2. Verify they are captured by the app
3. Monitor webhook forwarding to your SCB bank endpoint
"""

import subprocess
import json
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# ============================================================================
# WEBHOOK RECEIVER - To monitor what the app sends to your endpoint
# ============================================================================

class WebhookHandler(BaseHTTPRequestHandler):
    """Simple HTTP server to receive and log webhook calls"""

    webhook_calls = []

    def do_POST(self):
        """Handle POST requests from the app"""
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)

        try:
            payload = json.loads(body.decode('utf-8'))
            self.webhook_calls.append({
                'timestamp': datetime.now().isoformat(),
                'path': self.path,
                'payload': payload
            })

            print("\n" + "="*70)
            print("✓ WEBHOOK RECEIVED!")
            print("="*70)
            print(f"Timestamp: {self.webhook_calls[-1]['timestamp']}")
            print(f"Path: {self.path}")
            print(f"Payload:\n{json.dumps(payload, indent=2)}")
            print("="*70 + "\n")

            # Send 200 OK response
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'ok': True}).encode())

        except Exception as e:
            print(f"ERROR processing webhook: {e}")
            self.send_response(400)
            self.end_headers()

    def log_message(self, format, *args):
        """Suppress default logging"""
        pass


def run_webhook_server(port=8000):
    """Run webhook server in background"""
    server = HTTPServer(('0.0.0.0', port), WebhookHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print(f"✓ Webhook server listening on http://0.0.0.0:{port}")
    return server


# ============================================================================
# MAIN TESTING GUIDE
# ============================================================================

def main():
    print("\n")
    print("╔" + "="*78 + "╗")
    print("║" + "  SCB BANK WEBHOOK INTEGRATION - END-TO-END TESTING GUIDE".center(78) + "║")
    print("╚" + "="*78 + "╝")

    print("""
│
├─ This guide shows how to test the complete payment notification flow:
│  1. Send mock SCB LINE notification from your Mac
│  2. Receive it on the Android emulator
│  3. App captures and processes it
│  4. App forwards to your SCB bank webhook endpoint
│  5. Verify webhook received the correct data
│
╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 1: PREPARE YOUR SCB WEBHOOK ENDPOINT                                 │
╚════════════════════════════════════════════════════════════════════════════╝

Your SCB bank endpoint must be accessible from the emulator.

If your webhook is on localhost (Mac):
  • Use HTTP address: http://10.0.2.2:8000/webhook
    ↳ 10.0.2.2 is how emulator reaches host machine
  • DO NOT use: localhost:8000 or 127.0.0.1:8000

To configure it in the app:
  1. Open Notification Agent app on emulator
  2. Go to Settings/Bank Config
  3. Add/Edit SCB entry:
     - Bank Name: SCB
     - Endpoint URL: http://10.0.2.2:8000/webhook
     - API Key: (leave empty or add if needed)
     - Enabled: ✓

If you already configured it, skip to STEP 2.

╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 2: START MONITORING WEBHOOK CALLS (OPTIONAL)                          │
╚════════════════════════════════════════════════════════════════════════════╝

Option A: Use Python Server (Easiest)
──────────────────────────────────────
If you're running this script with the --server flag:
  python3 emulator_end_to_end_test.py --server

This starts a webhook server on port 8000 and logs all calls.
Configure your app to use: http://10.0.2.2:8000/webhook

Option B: Use nc (netcat)
─────────────────────────
In a separate terminal:
  nc -l 0.0.0.0 8000

Will show raw HTTP requests. Useful for debugging.

Option C: Use tcpdump
─────────────────────
In a separate terminal:
  sudo tcpdump -i lo port 8000

Shows all traffic on port 8000.

╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 3: ENABLE SYSTEM NOTIFICATION LISTENER                                │
╚════════════════════════════════════════════════════════════════════════════╝

The app needs system permission to listen to LINE notifications:

Run this command on your Mac:
  adb shell settings put secure enabled_notification_listeners \\
    "com.example.notification_agent/.service.NotificationCaptureService"

This registers the app as a system notification listener.

╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 4: SEND MOCK SCB PAYMENT NOTIFICATION                                 │
╚════════════════════════════════════════════════════════════════════════════╝

From your Mac, send a test notification:

  python3 scripts/mock_scb_line_notification.py \\
    --amount 5555 \\
    --bank SCB \\
    --account X-7985 \\
    --verbose

Expected Output:
  ✓ Sent visible notification

This sends a notification that looks like:
  From:   LINE (jp.naver.line.android)
  Title:  SCB Connect
  Text:   รายการเงินเข้า 5,555.00 บาท เข้าบัญชี X-7985 วันที่ ...

╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 5: WATCH FOR WEBHOOK CALL                                            │
╚════════════════════════════════════════════════════════════════════════════╝

If webhook server is running, look for output like:

  ======================================================================
  ✓ WEBHOOK RECEIVED!
  ======================================================================
  Timestamp: 2026-06-05T11:25:30.123456
  Path: /webhook
  Payload:
  {
    "PaymentAmount": "5555.00",
    "RemainAmount": "0.00",
    "TxType": "PayIn",
    "DestinationBankCode": "TMB",
    "DestinationAccountNo": "XX-0032",
    "SourceBankCode": "SCB",
    "SourceBankAccountNo": "X-7985"
  }
  ======================================================================

✓ If you see this: Webhook forwarding is working!

╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 6: VERIFY IN APP                                                     │
╚════════════════════════════════════════════════════════════════════════════╝

Check the Notification Agent app:

1. Open app on emulator
2. Go to "Messages" tab
3. You should see the SCB payment:
   - Title: "SCB Connect"
   - Text: "รายการเงินเข้า 5,555.00 บาท เข้าบัญชี X-7985 ..."
   - Time: Just now

4. Click the entry to see details:
   - Source: LINE
   - Bank: SCB (if parsed)
   - Amount: 5,555.00 (if parsed)

╔════════════════════════════════════════════════════════════════════════════╗
│ STEP 7: BATCH TEST - SEND MULTIPLE AMOUNTSC                               │
╚════════════════════════════════════════════════════════════════════════════╝

Test with different amounts to verify parsing:

  python3 scripts/batch_mock_notifications.py \\
    --amounts 100,500,1000,5000,10000 \\
    --bank SCB \\
    --account X-7985 \\
    --interval 2

This sends 5 notifications, 2 seconds apart, with different amounts.

Check webhook receives:
  - 5 separate POST requests
  - Correct amount in each (100.00, 500.00, 1000.00, 5000.00, 10000.00)
  - All other fields match

╔════════════════════════════════════════════════════════════════════════════╗
│ TROUBLESHOOTING                                                            │
╚════════════════════════════════════════════════════════════════════════════╝

ISSUE: Notification doesn't appear in Messages
SOLUTION:
  1. Check app filter rules:
     Messages → Filters → Check if LINE is enabled
  2. Check notification listener is registered:
     adb shell settings get secure enabled_notification_listeners
     Should show: com.example.notification_agent/.service.NotificationCaptureService
  3. Check app logs:
     adb logcat | grep -i notification_agent

ISSUE: Webhook not receiving calls
SOLUTION:
  1. Verify endpoint URL in app config:
     - Use http://10.0.2.2:PORT (not localhost)
  2. Check webhook server is running:
     - Should print "listening on..." message
  3. Check emulator can reach host:
     adb shell ping 10.0.2.2
     Should see responses

ISSUE: Webhook receives call but amount is wrong
SOLUTION:
  1. Check parsing in app logs
  2. Verify Thai text format is correct (should include "เงินเข้า")
  3. Check amount in notification text

ISSUE: "Permission denied" errors
SOLUTION:
  Run setup again:
  bash scripts/setup_emulator.sh

═══════════════════════════════════════════════════════════════════════════════

EXPECTED WEBHOOK PAYLOAD STRUCTURE

When a payment is captured, the app sends JSON like this:

{
  "PaymentAmount": "5555.00",           ← Amount extracted from notification
  "RemainAmount": "0.00",               ← Not used (always 0)
  "TxType": "PayIn",                    ← Always "PayIn" (incoming)
  "DestinationBankCode": "TMB",        ← Hardcoded destination
  "DestinationAccountNo": "XX-0032",   ← Hardcoded destination account
  "SourceBankCode": "SCB",             ← Your bank (from title)
  "SourceBankAccountNo": "X-7985"      ← Your account number
}

═══════════════════════════════════════════════════════════════════════════════

QUICK COMMAND REFERENCE

# Grant notification listener permission
adb shell settings put secure enabled_notification_listeners \\
  "com.example.notification_agent/.service.NotificationCaptureService"

# Send 5555 THB test
python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose

# Batch test 5 amounts
python3 scripts/batch_mock_notifications.py --count 5 --bank SCB

# Check logs
adb logcat | grep -i bank

# Start webhook server
python3 emulator_end_to_end_test.py --server

═══════════════════════════════════════════════════════════════════════════════

That's it! You now have a complete testing setup for SCB webhook integration.

Ready to test? Run this in Terminal 1:
  python3 emulator_end_to_end_test.py --server

Then in Terminal 2:
  python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose

Watch Terminal 1 for webhook calls!
    """)


if __name__ == "__main__":
    import sys

    if "--server" in sys.argv:
        # Run webhook server
        server = run_webhook_server()
        print("\n✓ Webhook server is running!")
        print("Configure your app to use: http://10.0.2.2:8000/webhook")
        print("\nWaiting for webhook calls... (Press Ctrl+C to stop)\n")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n\nShutting down...")
            server.shutdown()
    else:
        # Show guide
        main()

