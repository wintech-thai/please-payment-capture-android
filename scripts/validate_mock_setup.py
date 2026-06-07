#!/usr/bin/env python3
"""Validation and diagnostic script for mock notification setup.

This script checks if all prerequisites are met and performs a dry-run test
to ensure the notification system is properly configured.
"""

import subprocess
import sys
import shutil
from pathlib import Path


def check_python_version():
    """Check if Python 3.6+ is available."""
    print("Checking Python version...", end=" ")
    version_info = sys.version_info
    if version_info.major >= 3 and version_info.minor >= 6:
        print(f"✓ Python {version_info.major}.{version_info.minor}.{version_info.micro}")
        return True
    else:
        print(f"✗ Python {version_info.major}.{version_info.minor} (need 3.6+)")
        return False


def check_adb():
    """Check if adb is available in PATH."""
    print("Checking ADB installation...", end=" ")
    adb_path = shutil.which("adb")
    if adb_path:
        print(f"✓ Found at {adb_path}")
        return True
    else:
        print("✗ Not found in PATH")
        print("  Install Android SDK Platform Tools and add to PATH")
        return False


def check_adb_device():
    """Check if any ADB device is connected."""
    print("Checking connected devices...", end=" ")
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=5
        )
        lines = result.stdout.strip().split('\n')
        devices = [line.split()[0] for line in lines[1:]
                   if line.strip() and "device" in line and "emulator" not in line[:1]]

        if not devices:
            # Try to find emulators
            devices = [line.split()[0] for line in lines[1:]
                       if line.strip() and "device" in line]

        if devices:
            print(f"✓ Found {len(devices)} device(s)")
            for device in devices:
                print(f"     - {device}")
            return True, devices
        else:
            print("✗ No devices connected")
            return False, []
    except Exception as e:
        print(f"✗ Error: {e}")
        return False, []


def check_mock_scripts():
    """Check if all mock notification scripts exist."""
    print("Checking mock notification scripts...", end=" ")
    script_dir = Path(__file__).parent
    required_scripts = [
        "mock_scb_line_notification.py",
        "batch_mock_notifications.py",
        "mock_notification.sh"
    ]

    missing = []
    for script in required_scripts:
        script_path = script_dir / script
        if not script_path.exists():
            missing.append(script)

    if not missing:
        print(f"✓ All scripts found")
        return True
    else:
        print(f"✗ Missing: {', '.join(missing)}")
        return False


def check_scripts_executable():
    """Check if shell scripts are executable."""
    print("Checking script permissions...", end=" ")
    script_dir = Path(__file__).parent
    shell_scripts = [
        "mock_notification.sh",
        "mock_scb_line_notification.py",
        "batch_mock_notifications.py"
    ]

    all_executable = True
    for script in shell_scripts:
        script_path = script_dir / script
        if script_path.exists():
            # Check executable permission
            import os
            is_exec = os.access(script_path, os.X_OK)
            if not is_exec:
                all_executable = False

    if all_executable:
        print("✓ All scripts are executable")
        return True
    else:
        print("✗ Some scripts need chmod +x")
        print("  Run: chmod +x mock_scb_line_notification.py batch_mock_notifications.py mock_notification.sh")
        return False


def dry_run_test(device_id=None):
    """Perform a dry-run test of the notification system."""
    print("\nPerforming dry-run test...", end=" ")
    try:
        script_dir = Path(__file__).parent
        script_path = script_dir / "mock_scb_line_notification.py"

        cmd = [
            sys.executable, str(script_path),
            "--amount", "1000",
            "--bank", "SCB",
            "--account", "X-7985",
            "--dry-run"
        ]

        if device_id:
            cmd.extend(["--device", device_id])

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)

        if result.returncode == 0:
            print("✓ Dry-run successful")
            return True
        else:
            print("✗ Dry-run failed")
            if result.stderr:
                print(f"  Error: {result.stderr[:200]}")
            return False
    except Exception as e:
        print(f"✗ Error during dry-run: {e}")
        return False


def show_quick_start(device_id=None):
    """Show quick start examples."""
    print("\n" + "=" * 70)
    print("Quick Start Examples")
    print("=" * 70)

    device_arg = f" --device {device_id}" if device_id else ""

    print("""
1. Send a single SCB notification (1,000 THB):
   python3 scripts/mock_scb_line_notification.py --amount 1000

2. Or use the shell wrapper:
   scripts/mock_notification.sh 1000 SCB X-7985

3. Send KTB notification:
   scripts/mock_notification.sh 5000 KTB XX7157

4. Batch test (10 notifications):
   python3 scripts/batch_mock_notifications.py --count 10 --bank SCB

5. Verbose output for debugging:
   scripts/mock_notification.sh 1000 SCB X-7985 --verbose

For full documentation, see: MOCK_NOTIFICATIONS_GUIDE.md
""")


def main():
    print("=" * 70)
    print("Mock Notification System - Validation & Diagnostics")
    print("=" * 70)
    print()

    checks_passed = 0
    checks_total = 0

    # Perform checks
    checks = [
        ("Python version", check_python_version),
        ("ADB installation", check_adb),
        ("Mock scripts", check_mock_scripts),
        ("Script permissions", check_scripts_executable),
    ]

    device_ok = False
    devices = []

    for name, check_func in checks:
        checks_total += 1
        if check_func():
            checks_passed += 1
        print()

    # Check devices separately (may return multiple results)
    checks_total += 1
    device_ok, devices = check_adb_device()
    if device_ok:
        checks_passed += 1
    print()

    # Show summary
    print("=" * 70)
    print(f"Validation Summary: {checks_passed}/{checks_total} passed")
    print("=" * 70)

    if checks_passed < checks_total:
        print("\n⚠ Issues found. Please fix the errors above.")
        if not device_ok:
            print("\nTo fix ADB device issues:")
            print("1. Connect your Android device via USB")
            print("2. Enable Developer Options in Settings")
            print("3. Enable USB Debugging")
            print("4. Approve USB debugging permission on device")
            print("5. Run: adb devices")
        return 1

    print("\n✓ All checks passed!")

    # Perform dry-run
    selected_device = devices[0] if devices else None
    if dry_run_test(selected_device):
        show_quick_start(selected_device)
        print("\n✓ System is ready! You can now use the mock notification scripts.")
        return 0
    else:
        print("\n⚠ Dry-run test failed.")
        print("This may indicate an issue with the installation or ADB connection.")
        return 1


if __name__ == "__main__":
    sys.exit(main())

