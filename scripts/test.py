#!/usr/bin/env python3
"""Generate and inject SCB/KTB LINE test notifications into the app."""

import argparse
import base64
import json
import subprocess
import sys
import shlex
from dataclasses import dataclass
from datetime import datetime


ACTION = "com.example.notification_agent.action.MOCK_LINE_NOTIFICATION"
COMPONENT = "com.example.notification_agent/.receiver.MockLineNotificationReceiver"
PACKAGE_NAME = "jp.naver.line.android"


@dataclass(frozen=True)
class BankCase:
    title: str
    account: str
    amount: float


BANK_CASES = {
    "SCB": BankCase(title="SCB Connect", account="X-7985", amount=100.0),
    "KTB": BankCase(title="Krungthai Connext", account="XX7157", amount=100.0),
}


def build_notification_text(amount: float, account: str, timestamp_text: str) -> str:
    return f"รายการเงินเข้า {amount:,.2f} บาท เข้าบัญชี {account} วันที่ {timestamp_text}"


def build_raw_preview(title: str, text: str, amount: float) -> str:
    now = int(datetime.now().timestamp() * 1000)
    payload = {
        "id": now % 10_000,
        "sourceType": "NOTIFICATION",
        "sourceKey": PACKAGE_NAME,
        "sourceLabel": "LINE",
        "title": title,
        "text": text,
        "timestamp": now,
        "deviceId": "mock-device-id",
        "device": "mock device",
        "agentVersion": "debug",
        "notificationAppPackage": PACKAGE_NAME,
        "notificationAppName": "LINE",
        "amount": amount,
    }
    return json.dumps(payload, ensure_ascii=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="Inject SCB or KTB LINE test notifications")
    parser.add_argument("--bank", choices=sorted(BANK_CASES), default="SCB")
    parser.add_argument("--amount", type=float)
    parser.add_argument("--account", type=str)
    parser.add_argument("--timestamp-text", type=str, default=datetime.now().strftime("%d/%m/%Y %H:%M"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    bank_case = BANK_CASES[args.bank]
    amount = args.amount if args.amount is not None else bank_case.amount
    account = args.account if args.account else bank_case.account
    title = bank_case.title
    text = build_notification_text(amount, account, args.timestamp_text)

    print("========================================")
    print(f"Injecting Fake {args.bank} LINE Notification...")
    print("========================================")
    print("Raw:", build_raw_preview(title, text, amount))

    if args.dry_run:
        return 0

    title_b64 = base64.b64encode(title.encode("utf-8")).decode("ascii")
    text_b64 = base64.b64encode(text.encode("utf-8")).decode("ascii")
    label_b64 = base64.b64encode("LINE".encode("utf-8")).decode("ascii")

    command = (
        "adb shell am broadcast "
        f"-n {shlex.quote(COMPONENT)} "
        f"-a {shlex.quote(ACTION)} "
        f"--es extra_package_name {shlex.quote(PACKAGE_NAME)} "
        f"--es extra_source_label_b64 {shlex.quote(label_b64)} "
        f"--es extra_title_b64 {shlex.quote(title_b64)} "
        f"--es extra_text_b64 {shlex.quote(text_b64)}"
    )

    result = subprocess.run(command, shell=True, capture_output=True, text=True)

    if result.stdout:
        print(result.stdout.rstrip())
    if result.stderr:
        print(result.stderr.rstrip(), file=sys.stderr)

    if result.returncode == 0:
        print("Notification injected successfully!")
        print("Check the Notification Listener app for the captured event.")
        return 0

    print("Failed to inject notification.")
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())