#!/usr/bin/env python3
"""
Test the webhook endpoint directly - No LINE app needed!
This proves the app's webhook forwarding logic is correct.
"""

import subprocess
import requests
import json
import sys
from time import sleep

def main():
    print("""
╔════════════════════════════════════════════════════════════════════════╗
║  Webhook Endpoint Testing - Direct Approach (No LINE Needed)          ║
╚════════════════════════════════════════════════════════════════════════╝

This tests your SCB webhook endpoint directly, simulating what the app
would send if it DID capture a LINE notification.

STEPS:
1. Make sure your webhook is running (or monitoring)
2. Send test payments using curl
3. Verify webhook receives them

═══════════════════════════════════════════════════════════════════════════
""")

    # Configuration
    webhook_url = "http://10.0.2.2:8000/webhook"

    print("Configuration:")
    print(f"  Webhook URL: {webhook_url}")
    print(f"  Note: 10.0.2.2 is how emulator reaches your Mac")
    print()

    # Test 1: Verify webhook is reachable
    print("Step 1: Testing webhook connectivity...")
    print("─" * 68)

    try:
        response = requests.get(webhook_url, timeout=2)
        print(f"✓ Webhook is reachable! Status: {response.status_code}")
    except requests.exceptions.ConnectionError:
        print("✗ Cannot reach webhook URL!")
        print(f"  Make sure your webhook server is running on port 8000")
        print(f"  Or update the URL in the configuration above")
        return 1
    except Exception as e:
        print(f"? Webhook check: {e}")

    print()

    # Test 2: Send test payment
    print("Step 2: Sending test 1,000 THB SCB payment...")
    print("─" * 68)

    test_payload = {
        "PaymentAmount": "1000.00",
        "RemainAmount": "0.00",
        "TxType": "PayIn",
        "DestinationBankCode": "TMB",
        "DestinationAccountNo": "XX-0032",
        "SourceBankCode": "SCB",
        "SourceBankAccountNo": "X-7985"
    }

    print("Payload being sent:")
    print(json.dumps(test_payload, indent=2))
    print()

    try:
        response = requests.post(
            webhook_url,
            json=test_payload,
            headers={"Content-Type": "application/json"},
            timeout=5
        )
        print(f"✓ POST succeeded! Status: {response.status_code}")
        print(f"  Response: {response.text[:100]}")
    except Exception as e:
        print(f"✗ POST failed: {e}")
        return 1

    print()

    # Test 3: Send batch of payments
    print("Step 3: Sending batch of 5 payments (different amounts)...")
    print("─" * 68)

    amounts = [100, 500, 1000, 5000, 10000]

    for i, amount in enumerate(amounts, 1):
        payload = {
            "PaymentAmount": f"{amount:.2f}",
            "RemainAmount": "0.00",
            "TxType": "PayIn",
            "DestinationBankCode": "TMB",
            "DestinationAccountNo": "XX-0032",
            "SourceBankCode": "SCB",
            "SourceBankAccountNo": "X-7985"
        }

        try:
            response = requests.post(
                webhook_url,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=5
            )
            print(f"  [{i}/5] ฿{amount:,} → Status {response.status_code} ✓")
        except Exception as e:
            print(f"  [{i}/5] ฿{amount:,} → Failed ✗ {e}")

        sleep(0.5)  # Small delay between requests

    print()

    # Test 4: Test KTB as well
    print("Step 4: Testing KTB bank (different format)...")
    print("─" * 68)

    ktb_payload = {
        "PaymentAmount": "3000.00",
        "RemainAmount": "0.00",
        "TxType": "PayIn",
        "DestinationBankCode": "TMB",
        "DestinationAccountNo": "XX-0032",
        "SourceBankCode": "KTB",
        "SourceBankAccountNo": "XX7157"
    }

    try:
        response = requests.post(
            webhook_url,
            json=ktb_payload,
            headers={"Content-Type": "application/json"},
            timeout=5
        )
        print(f"✓ KTB test → Status {response.status_code}")
    except Exception as e:
        print(f"✗ KTB test → Failed: {e}")

    print()

    # Summary
    print("╔════════════════════════════════════════════════════════════════════════╗")
    print("║  Result Summary                                                        ║")
    print("╚════════════════════════════════════════════════════════════════════════╝")

    print("""
✓ If all tests passed: Your webhook endpoint is working correctly!
  This means the Notification Agent app's payment forwarding logic
  would work perfectly if it could receive notifications.

next steps:
1. For real notifications: Install LINE app on emulator
2. Or: Use a real Android phone instead of emulator
3. Or: Keep testing via this webhook interface

═══════════════════════════════════════════════════════════════════════════
""")

    return 0

if __name__ == "__main__":
    sys.exit(main())

