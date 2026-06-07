#!/usr/bin/env python3
import base64
import pathlib
import re
import sys

import requests

ROOT = pathlib.Path(__file__).resolve().parents[1]
PAYMENT_CONFIRM = ROOT / "docs" / "payment-confirm.md"

text = PAYMENT_CONFIRM.read_text(encoding="utf-8")
api_key_match = re.search(r"api-key\s*:\s*(\S+)", text)
url_match = re.search(r"request URL:\s*(\S+)", text)
if not api_key_match or not url_match:
    raise SystemExit("Could not parse api-key or request URL from docs/payment-confirm.md")

api_key = api_key_match.group(1)
url = url_match.group(1)

payload = {
    "PaymentAmount": 10.45,
    "RemainAmount": 0.00,
    "TxType": "PayIn",
    "SourceBankCode": "SCB",
}

common_headers = {
    "Accept": "application/json",
    "Content-Type": "application/json; charset=utf-8",
    "Onix-Application-Type": "backend",
}

def auth_headers(mode: str) -> dict:
    if mode == "basic":
        token = base64.b64encode(f"api:{api_key}".encode()).decode()
        return {**common_headers, "Authorization": f"Basic {token}"}
    if mode == "bearer":
        token = base64.b64encode(api_key.encode()).decode()
        return {**common_headers, "Authorization": f"Bearer {token}"}
    raise ValueError(f"unsupported mode: {mode}")

for mode in ("basic", "bearer"):
    headers = auth_headers(mode)
    print(f"=== {mode.upper()} ===")
    print("Headers:")
    for key, value in headers.items():
        display = value if key != "Authorization" else value[:24] + "..."
        print(f"  {key}: {display}")
    print("Payload:")
    print(payload)
    try:
        response = requests.post(url, json=payload, headers=headers, timeout=15)
        print(f"Status: {response.status_code}")
        print(f"WWW-Authenticate: {response.headers.get('WWW-Authenticate', '<none>')}")
        body = response.text.strip() or "<empty>"
        print(f"Body: {body[:500]}")
    except Exception as exc:
        print(f"Error: {exc!r}")
    print()


