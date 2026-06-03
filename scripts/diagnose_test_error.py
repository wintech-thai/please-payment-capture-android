#!/usr/bin/env python3
"""Diagnose why 'send test payload' is failing."""

import json

def diagnose_test_payload_error():
    """Explain the most common causes of test payload failure."""

    print("=" * 70)
    print("WEBHOOK TEST PAYLOAD - FAILURE DIAGNOSIS")
    print("=" * 70)

    errors = {
        "webhook disabled": {
            "cause": "❌ Webhook toggle is OFF in settings",
            "fix": "✅ Enable the webhook toggle in app settings",
            "code_location": "WebhookDispatcher.kt:82",
            "check_command": "Look for 'Webhook' toggle in Settings - must be ON"
        },
        "webhook url empty": {
            "cause": "❌ Webhook URL field is empty",
            "fix": "✅ Enter the webhook URL: https://your-webhook-endpoint.com/webhook",
            "code_location": "WebhookDispatcher.kt:83",
            "check_command": "Look for 'Webhook URL' field in Settings - must have value"
        },
        "error": {
            "cause": "❌ Network error with no message (could be multiple issues)",
            "possible_issues": [
                "DNS resolution failed",
                "Connection timeout",
                "Server unreachable",
                "Invalid URL format",
                "Token authentication failed",
                "SSL/TLS error"
            ],
            "fix": "✅ Check URL and token, then test with:",
            "test_command": "python3 scripts/test_webhook.py 'https://your-webhook-endpoint.com/webhook' --token 'YOUR_BEARER_TOKEN' --verbose"
        }
    }

    print("\n📋 COMMON ERRORS & SOLUTIONS:")
    print("-" * 70)

    print("\n1️⃣  ERROR MESSAGE: 'webhook disabled'")
    print("   Cause: " + errors["webhook disabled"]["cause"])
    print("   Fix:   " + errors["webhook disabled"]["fix"])
    print(f"   Code:  {errors['webhook disabled']['code_location']}")

    print("\n2️⃣  ERROR MESSAGE: 'webhook url empty'")
    print("   Cause: " + errors["webhook url empty"]["cause"])
    print("   Fix:   " + errors["webhook url empty"]["fix"])
    print(f"   Code:  {errors['webhook url empty']['code_location']}")

    print("\n3️⃣  ERROR MESSAGE: 'error' (generic error, no specific message)")
    print("   Cause: " + errors["error"]["cause"])
    print("   Possible issues:")
    for issue in errors["error"]["possible_issues"]:
        print(f"      • {issue}")
    print("   Fix:   " + errors["error"]["fix"])
    print(f"   Test:  {errors['error']['test_command']}")

    print("\n" + "=" * 70)
    print("CONFIGURATION CHECKLIST")
    print("=" * 70)

    checklist = [
        ("Webhook Toggle", "Must be ON/enabled", "🔲 CHECK"),
        ("Webhook URL", "Must be: https://your-webhook-endpoint.com/webhook", "🔲 CHECK"),
        ("Bearer Token", "Must be: YOUR_BEARER_TOKEN", "🔲 CHECK"),
        ("Internet Connection", "Device must have network access", "🔲 CHECK"),
        ("URL Validation", "Must match: https://your-webhook-endpoint.com/webhook", "🔲 CHECK"),
    ]

    print("\nBefore clicking 'Test Webhook', verify:")
    for i, (item, requirement, check) in enumerate(checklist, 1):
        print(f"\n{i}. {item}")
        print(f"   Requirement: {requirement}")
        print(f"   Status: {check}")

    print("\n" + "=" * 70)
    print("STEP-BY-STEP DEBUGGING")
    print("=" * 70)

    steps = [
        {
            "num": 1,
            "title": "Open Settings in the app",
            "details": "Navigate to Settings page"
        },
        {
            "num": 2,
            "title": "Check Webhook section",
            "details": "Look for 'Webhook' toggle switch - should be ON (enabled, blue)"
        },
        {
            "num": 3,
            "title": "Verify Webhook URL",
            "details": "URL field must contain: https://your-webhook-endpoint.com/webhook"
        },
        {
            "num": 4,
            "title": "Verify Bearer Token",
            "details": "Token field must contain: YOUR_BEARER_TOKEN"
        },
        {
            "num": 5,
            "title": "Check internet connection",
            "details": "Device must have active internet (WiFi or mobile data)"
        },
        {
            "num": 6,
            "title": "Click 'Test Webhook' button",
            "details": "Should show success message or specific error"
        }
    ]

    for step in steps:
        print(f"\n Step {step['num']}: {step['title']}")
        print(f"         → {step['details']}")

    print("\n" + "=" * 70)
    print("IF STILL FAILING: Quick Test")
    print("=" * 70)
    print("""
Run this command on your computer to test the endpoint directly:

    python3 scripts/test_webhook.py \\
        "https://your-webhook-endpoint.com/webhook" \\
        --token "YOUR_BEARER_TOKEN" \\
        --verbose

This tells us if:
  ✅ URL is correct
  ✅ Token is accepted
  ✅ Server is working
  ✅ Network is working
""")

    print("=" * 70)
    print("WEBHOOK DISPATCHER CODE FLOW")
    print("=" * 70)
    print("""
When you click "Test Webhook":

1. sendTestWebhook() in SettingsViewModel
   ↓
2. dispatcher.sendTest()
   ↓
3. Check: webhook enabled? (if NO → "webhook disabled")
   ↓
4. Check: webhook URL not empty? (if NO → "webhook url empty")
   ↓
5. Build test message
   ↓
6. Call send() to POST to webhook
   ↓
7. If success (200-299) → "HTTP 200" ✅
8. If failure → "HTTP XXX" (error code) ❌
9. If exception → "error" (network/parsing error) ❌
""")

    print("=" * 70)
    print("MOST LIKELY CAUSE")
    print("=" * 70)
    print("""
Based on typical issues, the most common reasons for "error" are:

    90% — Webhook URL or token is not saved correctly
    5% — Webhook toggle is OFF
    5% — Network/server issues

→ Check the URL field first! It must be exactly:
    https://your-webhook-endpoint.com/webhook

   (Make sure you have /webhook at the end!)
""")

if __name__ == "__main__":
    diagnose_test_payload_error()

