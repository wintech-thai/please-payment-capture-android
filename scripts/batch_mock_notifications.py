#!/usr/bin/env python3
"""Batch test script for SCB/KTB LINE mock notifications.

This script generates multiple notifications with varying amounts for stress
testing and validation of the Notification Agent app.

Usage:
    python3 batch_mock_notifications.py --count 10 --bank SCB
    python3 batch_mock_notifications.py --amounts 100,500,1000,5000 --bank KTB --interval 3
"""

import argparse
import subprocess
import sys
import time
from pathlib import Path
from typing import List


def get_script_dir() -> Path:
    """Get the directory containing this script."""
    return Path(__file__).parent


def run_mock_notification(
    amount: float,
    bank: str,
    account: str,
    device_id: str = None,
    verbose: bool = False
) -> bool:
    """Run the mock notification script for a single amount."""
    script_path = get_script_dir() / "mock_scb_line_notification.py"

    cmd = [
        sys.executable,
        str(script_path),
        "--amount", str(amount),
        "--bank", bank,
        "--account", account,
        "--quiet"
    ]

    if device_id:
        cmd.extend(["--device", device_id])

    if verbose:
        cmd.append("--verbose")

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return result.returncode == 0
    except Exception as e:
        print(f"ERROR: Failed to send notification for amount {amount}: {e}")
        return False


def generate_amount_sequence(
    min_amount: float = 100,
    max_amount: float = 10000,
    count: int = 10
) -> List[float]:
    """Generate a sequence of amounts for testing.

    Creates a logarithmic distribution to test both small and large amounts.
    """
    if count == 1:
        return [(min_amount + max_amount) / 2]

    # Generate logarithmic distribution
    import math
    log_min = math.log(min_amount)
    log_max = math.log(max_amount)

    amounts = []
    for i in range(count):
        log_amount = log_min + (log_max - log_min) * i / (count - 1)
        amount = math.exp(log_amount)
        amounts.append(amount)

    return amounts


def main():
    parser = argparse.ArgumentParser(
        description="Batch send mock LINE bank notifications for stress testing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Send 10 notifications with increasing amounts
  %(prog)s --count 10 --bank SCB --account X-7985

  # Send specific amounts
  %(prog)s --amounts 100,500,1000,5000,10000 --bank KTB --account XX7157

  # Send with 5 second interval between each
  %(prog)s --count 20 --bank SCB --interval 5 --verbose

  # Test both banks
  for bank in SCB KTB; do
    %(prog)s --count 5 --bank $bank --interval 2
  done
        """
    )

    parser.add_argument(
        "--count", type=int, default=5,
        help="Number of notifications to send (default: 5)"
    )
    parser.add_argument(
        "--amounts", type=str,
        help="Comma-separated list of amounts to send (overrides --count)"
    )
    parser.add_argument(
        "--min-amount", type=float, default=100,
        help="Minimum amount (default: 100)"
    )
    parser.add_argument(
        "--max-amount", type=float, default=10000,
        help="Maximum amount (default: 10000)"
    )
    parser.add_argument(
        "--bank", choices=["SCB", "KTB"], default="SCB",
        help="Bank name (default: SCB)"
    )
    parser.add_argument(
        "--account", type=str,
        help="Account number (e.g., X-7985 for SCB, XX7157 for KTB)"
    )
    parser.add_argument(
        "--device", "-d", type=str,
        help="ADB device ID (auto-detect if not specified)"
    )
    parser.add_argument(
        "--interval", type=float, default=1,
        help="Interval between notifications in seconds (default: 1)"
    )
    parser.add_argument(
        "--verbose", "-v", action="store_true",
        help="Enable verbose output"
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Show what would be sent without actually sending"
    )

    args = parser.parse_args()

    # Determine amounts to send
    if args.amounts:
        try:
            amounts = [float(a.strip()) for a in args.amounts.split(",")]
        except ValueError:
            print("ERROR: Invalid amounts format. Use comma-separated numbers (e.g., 100,500,1000)")
            return 1
    else:
        amounts = generate_amount_sequence(
            min_amount=args.min_amount,
            max_amount=args.max_amount,
            count=args.count
        )

    # Default account if not provided
    account = args.account or ("X-7985" if args.bank == "SCB" else "XX7157")

    # Show summary
    print("=" * 70)
    print("Batch SCB/KTB LINE Mock Notification Generator")
    print("=" * 70)
    print(f"Bank: {args.bank}")
    print(f"Account: {account}")
    print(f"Notifications: {len(amounts)}")
    print(f"Interval: {args.interval}s")
    print(f"Amounts: {', '.join([f'{a:.2f}' for a in amounts])}")
    print()

    if args.dry_run:
        print("[DRY RUN] No notifications will be sent.")
        return 0

    # Send notifications
    sent_count = 0
    failed_count = 0

    for idx, amount in enumerate(amounts, 1):
        print(f"[{idx}/{len(amounts)}] Sending {amount:.2f} THB...", end=" ", flush=True)

        success = run_mock_notification(
            amount=amount,
            bank=args.bank,
            account=account,
            device_id=args.device,
            verbose=args.verbose
        )

        if success:
            print("✓")
            sent_count += 1
        else:
            print("✗")
            failed_count += 1

        # Wait before sending next notification
        if idx < len(amounts):
            time.sleep(args.interval)

    # Summary
    print()
    print("=" * 70)
    print(f"Results: {sent_count} sent, {failed_count} failed")
    print("=" * 70)

    return 0 if failed_count == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

