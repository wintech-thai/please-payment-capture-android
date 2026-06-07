#!/usr/bin/env python3
"""
Test script for bank endpoint payment submission.
Simulates the BankWebhookDispatcher from the Android app.
"""

import json
import requests
from datetime import datetime
import base64

# Configuration from payment-confirm.md
API_KEY = "ae933e77-a3f4-4f1b-8125-69643698248b"
ENDPOINT_URL = "https://api-dev.please-payment.com/api/PaymentRequest/org/gabx01/action/SubmitPaymentRequest/eab2eae2-ab83-4d49-bff6-a30226663d09"

# Constants from BankWebhookDispatcher
DEFAULT_APPLICATION_TYPE = "backend"

def to_money_value(amount: float) -> float:
    """Convert amount to a JSON number rounded to 2 decimal places."""
    return round(amount, 2)

def build_payment_payload(amount: float, bank_code: str = "SCB", source_account: str = None) -> dict:
    """
    Build JSON payload matching BankWebhookDispatcher.buildJson()

    Args:
        amount: Payment amount (e.g., 10.45)
        bank_code: Source bank code (SCB or KTB)
        source_account: Source account number

    Returns:
        Dictionary with payment details
    """
    payload = {
        "PaymentAmount": to_money_value(amount),
        "RemainAmount": to_money_value(0.0),
        "TxType": "PayIn",
        "SourceBankCode": bank_code,
    }

    if source_account:
        payload["SourceBankAccountNo"] = source_account

    return payload

def build_auth_header(api_key: str) -> str:
    """Build Basic auth header matching Credentials.basic('api', apiKey)"""
    credentials = f"api:{api_key}"
    encoded = base64.b64encode(credentials.encode()).decode()
    return f"Basic {encoded}"

def send_payment_request(amount: float = 10.45, bank_code: str = "SCB", source_account: str = None):
    """
    Send payment request to bank endpoint.

    Args:
        amount: Payment amount
        bank_code: Source bank (SCB or KTB)
        source_account: Account number
    """
    payload = build_payment_payload(amount, bank_code, source_account)

    headers = {
        "Accept": "application/json",
        "Onix-Application-Type": DEFAULT_APPLICATION_TYPE,
        "Authorization": build_auth_header(API_KEY),
        "Content-Type": "application/json; charset=utf-8"
    }

    print("=" * 70)
    print(f"Bank Payment Request Test - {datetime.now().isoformat()}")
    print("=" * 70)
    print()
    print("🛰️ Method: POST")
    print()
    print(f"📤 Endpoint: {ENDPOINT_URL}")
    print()
    print("🔐 Headers:")
    for key, value in headers.items():
        if key == "Authorization":
            # Mask the full token for security
            masked_auth = value[:20] + "..."
            print(f"  {key}: {masked_auth}")
        else:
            print(f"  {key}: {value}")
    print()
    print("💰 Payload:")
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    print()
    print("-" * 70)
    print("Sending request...")
    print("-" * 70)
    print()

    try:
        response = requests.post(
            ENDPOINT_URL,
            json=payload,
            headers=headers,
            timeout=10
        )

        print(f"✓ Status Code: {response.status_code}")
        print()
        print("Response Headers:")
        for key, value in response.headers.items():
            print(f"  {key}: {value}")
        print()
        print("Response Body:")
        try:
            response_json = response.json()
            print(json.dumps(response_json, indent=2, ensure_ascii=False))
        except:
            print(response.text)

        print()
        if response.status_code in [200, 201]:
            print("✅ SUCCESS - Payment request sent successfully")
        else:
            print(f"⚠️  Server returned {response.status_code}")

    except requests.exceptions.ConnectionError as e:
        print(f"❌ CONNECTION ERROR: {e}")
        print("   Check if the endpoint URL is reachable")
    except requests.exceptions.Timeout:
        print("❌ TIMEOUT: Request took too long")
    except Exception as e:
        print(f"❌ ERROR: {e}")

    print()
    print("=" * 70)

if __name__ == "__main__":
    import sys

    # Parse arguments: [amount] [bank_code] [source_account]
    amount = float(sys.argv[1]) if len(sys.argv) > 1 else 10.45
    bank_code = sys.argv[2] if len(sys.argv) > 2 else "SCB"
    source_account = sys.argv[3] if len(sys.argv) > 3 else None

    # Validate bank code
    if bank_code not in ["SCB", "KTB"]:
        print(f"❌ Unsupported bank code: {bank_code}")
        print("   Supported: SCB, KTB")
        sys.exit(1)

    send_payment_request(amount, bank_code, source_account)

