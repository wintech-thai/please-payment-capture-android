#!/usr/bin/env python3
"""Test webhook endpoint with the same format as the Notification Agent app."""

import argparse
import json
import sys
from datetime import datetime
import urllib.request
import urllib.error

def send_webhook_test(url: str, token: str = None, verbose: bool = False) -> dict:
    """Send a test webhook payload identical to the app's format."""

    # Build the test payload (same as WebhookDispatcher.kt buildJson)
    payload = {
        "id": 0,
        "sourceType": "NOTIFICATION",
        "sourceKey": "agent.test",
        "sourceLabel": "Agent test",
        "title": "Test from Notification Agent",
        "text": f"Hello from test script at {int(datetime.now().timestamp() * 1000)}",
        "timestamp": int(datetime.now().timestamp() * 1000),
        "deviceId": "test-script",
        "device": "Test Device",
        "agentVersion": "test",
        "notificationAppPackage": "agent.test",
        "notificationAppName": "Agent test"
    }

    # Convert to JSON
    body = json.dumps(payload).encode('utf-8')

    # Create request with headers (same as AgentHttpClient.kt withAgentHeaders)
    req = urllib.request.Request(url, data=body)
    req.add_header('Content-Type', 'application/json; charset=utf-8')
    req.add_header('User-Agent', 'NotificationAgent/test-script')
    req.add_header('Accept', 'application/json')

    if token:
        req.add_header('Authorization', f'Bearer {token}')

    if verbose:
        print("\n=== REQUEST ===")
        print(f"URL: {req.full_url}")
        print(f"Method: {req.get_method()}")
        print("Headers:")
        for header, value in req.headers.items():
            if header.lower() == 'authorization':
                print(f"  {header}: Bearer {'*' * len(token)}")
            else:
                print(f"  {header}: {value}")
        print(f"\nBody ({len(body)} bytes):")
        print(json.dumps(payload, indent=2))

    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            response_body = response.read().decode('utf-8')
            result = {
                'success': True,
                'http_code': response.code,
                'message': f'HTTP {response.code} OK',
                'response_body': response_body
            }
            if verbose:
                print("\n=== RESPONSE ===")
                print(f"Status: {response.code}")
                print(f"Headers:")
                for header, value in response.headers.items():
                    print(f"  {header}: {value}")
                print(f"\nBody:")
                try:
                    print(json.dumps(json.loads(response_body), indent=2))
                except:
                    print(response_body)
            return result
    except urllib.error.HTTPError as e:
        result = {
            'success': False,
            'http_code': e.code,
            'message': f'HTTP {e.code} {e.reason}',
            'error': str(e)
        }
        if verbose:
            print("\n=== RESPONSE (ERROR) ===")
            print(f"Status: {e.code} {e.reason}")
            print(f"Headers:")
            for header, value in e.headers.items():
                print(f"  {header}: {value}")
            try:
                body = e.read().decode('utf-8')
                print(f"\nBody:")
                print(json.dumps(json.loads(body), indent=2))
            except:
                pass
        return result
    except Exception as e:
        return {
            'success': False,
            'http_code': None,
            'message': f'Error: {type(e).__name__}: {str(e)}',
            'error': str(e)
        }

def main():
    parser = argparse.ArgumentParser(description='Test webhook endpoint')
    parser.add_argument('url', help='Webhook URL (e.g., https://your-webhook-endpoint.com)')
    parser.add_argument('--token', '-t', help='Bearer token')
    parser.add_argument('--verbose', '-v', action='store_true', help='Verbose output')

    args = parser.parse_args()

    print(f"Testing webhook: {args.url}")
    if args.token:
        print(f"Token: Bearer {args.token[:10]}...")

    result = send_webhook_test(args.url, args.token, args.verbose)

    print(f"\n=== RESULT ===")
    print(f"Success: {result['success']}")
    print(f"HTTP Code: {result['http_code']}")
    print(f"Message: {result['message']}")

    if result.get('response_body'):
        print(f"Response: {result['response_body']}")

    sys.exit(0 if result['success'] else 1)

if __name__ == '__main__':
    main()

