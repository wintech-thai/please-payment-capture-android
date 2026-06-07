#!/usr/bin/env python3
"""
Example: Advanced notification testing scenarios

This demonstrates various ways to use the mock notification scripts
for different testing needs. Uncomment and modify as needed.
"""

import subprocess
import sys
from pathlib import Path

# Get the scripts directory
SCRIPTS_DIR = Path(__file__).parent

def run_amount_range_test():
    """Test various payment amounts that might be problematic."""
    print("Testing Amount Range...")

    amounts = [
        0.01,      # Minimum
        1.00,      # Simple
        99.99,     # Just under 100
        100.00,    # Round number
        999.99,    # Just under 1000
        1000.00,   # With comma separator
        10000.00,  # Large amount
        99999.99,  # Very large
        1000000.00 # Million baht
    ]

    for amount in amounts:
        cmd = [
            sys.executable,
            str(SCRIPTS_DIR / "mock_scb_line_notification.py"),
            "--amount", str(amount),
            "--bank", "SCB",
            "--quiet"
        ]
        print(f"  Testing {amount:,.2f} THB...", end=" ", flush=True)

        result = subprocess.run(cmd, capture_output=True, timeout=10)
        if result.returncode == 0:
            print("✓")
        else:
            print("✗")


def run_decimal_precision_test():
    """Test various decimal places."""
    print("Testing Decimal Precision...")

    amounts = [
        100.0,     # One decimal
        100.1,     # One decimal (non-zero)
        100.12,    # Two decimals
        100.123,   # Three decimals (should be truncated)
        100.1234,  # Four decimals
    ]

    for amount in amounts:
        cmd = [
            sys.executable,
            str(SCRIPTS_DIR / "mock_scb_line_notification.py"),
            "--amount", str(amount),
            "--bank", "SCB",
            "--quiet"
        ]
        print(f"  Testing {amount} THB...", end=" ", flush=True)

        result = subprocess.run(cmd, capture_output=True, timeout=10)
        if result.returncode == 0:
            print("✓")
        else:
            print("✗")


def run_account_format_test():
    """Test various account number formats."""
    print("Testing Account Formats...")

    scb_accounts = ["X-0001", "X-5555", "X-9999", "1234567"]
    ktb_accounts = ["XX0001", "XX7157", "XX9999", "000000"]

    for account in scb_accounts:
        cmd = [
            sys.executable,
            str(SCRIPTS_DIR / "mock_scb_line_notification.py"),
            "--amount", "1000",
            "--bank", "SCB",
            "--account", account,
            "--quiet"
        ]
        print(f"  SCB {account}...", end=" ", flush=True)

        result = subprocess.run(cmd, capture_output=True, timeout=10)
        if result.returncode == 0:
            print("✓")
        else:
            print("✗")

    for account in ktb_accounts:
        cmd = [
            sys.executable,
            str(SCRIPTS_DIR / "mock_scb_line_notification.py"),
            "--amount", "1000",
            "--bank", "KTB",
            "--account", account,
            "--quiet"
        ]
        print(f"  KTB {account}...", end=" ", flush=True)

        result = subprocess.run(cmd, capture_output=True, timeout=10)
        if result.returncode == 0:
            print("✓")
        else:
            print("✗")


def run_both_banks_test():
    """Send same amount for both banks to verify parsing."""
    print("Testing Both Banks with Same Amount...")

    amount = 5555.50

    print(f"  Sending {amount} THB as SCB...", end=" ", flush=True)
    result1 = subprocess.run([
        sys.executable,
        str(SCRIPTS_DIR / "mock_scb_line_notification.py"),
        "--amount", str(amount),
        "--bank", "SCB",
        "--account", "X-7985",
        "--quiet"
    ], capture_output=True, timeout=10)
    print("✓" if result1.returncode == 0 else "✗")

    print(f"  Sending {amount} THB as KTB...", end=" ", flush=True)
    result2 = subprocess.run([
        sys.executable,
        str(SCRIPTS_DIR / "mock_scb_line_notification.py"),
        "--amount", str(amount),
        "--bank", "KTB",
        "--account", "XX7157",
        "--quiet"
    ], capture_output=True, timeout=10)
    print("✓" if result2.returncode == 0 else "✗")


def run_rapidly_sequenced_test():
    """Test rapid-fire notifications."""
    print("Testing Rapid Sequence (no delay)...")

    cmd = [
        sys.executable,
        str(SCRIPTS_DIR / "batch_mock_notifications.py"),
        "--amounts", "100,200,300,400,500",
        "--bank", "SCB",
        "--interval", "0.1",
        "--quiet"
    ]

    result = subprocess.run(cmd, capture_output=True, timeout=30)
    print("✓" if result.returncode == 0 else "✗")


def main():
    """Run all advanced tests."""
    print("=" * 70)
    print("Advanced Notification Testing Suite")
    print("=" * 70)
    print()

    tests = [
        run_amount_range_test,
        run_decimal_precision_test,
        run_account_format_test,
        run_both_banks_test,
        run_rapidly_sequenced_test,
    ]

    for test_func in tests:
        try:
            test_func()
            print()
        except Exception as e:
            print(f"ERROR: {e}")
            print()

    print("=" * 70)
    print("Advanced Testing Complete")
    print("=" * 70)


if __name__ == "__main__":
    main()

