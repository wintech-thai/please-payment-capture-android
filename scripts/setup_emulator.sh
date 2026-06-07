#!/bin/bash
# Setup Notification Agent for Emulator Testing
# This script grants all necessary permissions and configures the emulator

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  Setting up Notification Agent for Emulator Testing            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Check if device is connected
echo "Checking for connected devices..."
if ! adb devices | grep -q "emulator\|device"; then
    echo "ERROR: No emulator or device connected!"
    echo "Please start your emulator and try again."
    exit 1
fi

DEVICE=$(adb devices | grep -E "emulator|device" | head -1 | awk '{print $1}')
echo "✓ Found device: $DEVICE"
echo ""

# Grant notification listener permission
echo "Granting notification listener permission..."
adb -s "$DEVICE" shell pm grant com.example.notification_agent \
    android.permission.BIND_NOTIFICATION_LISTENER_SERVICE 2>/dev/null || true

# Grant SMS permissions (if needed)
echo "Granting SMS permissions..."
adb -s "$DEVICE" shell pm grant com.example.notification_agent \
    android.permission.RECEIVE_SMS 2>/dev/null || true
adb -s "$DEVICE" shell pm grant com.example.notification_agent \
    android.permission.READ_SMS 2>/dev/null || true

# Grant POST_NOTIFICATIONS (Android 13+)
echo "Granting notification posting permission..."
adb -s "$DEVICE" shell pm grant com.example.notification_agent \
    android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# Enable notification listener in settings
echo "Enabling notification listener in settings..."
adb -s "$DEVICE" shell settings put secure enabled_notification_listeners \
    "com.example.notification_agent/.service.NotificationCaptureService"

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  ✓ Setup Complete!                                             ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
echo "Next steps:"
echo "1. Verify SCB bank endpoint is configured in app:"
echo "   - Open Notification Agent app"
echo "   - Go to Bank Config"
echo "   - Check SCB endpoint is configured and enabled"
echo ""
echo "2. Send test notification:"
echo "   python3 scripts/mock_scb_line_notification.py --amount 5555 --verbose"
echo ""
echo "3. Check for webhook call:"
echo "   Monitor your webhook endpoint (default: http://10.0.2.2:8000/webhook)"
echo ""
echo "4. View messages in app:"
echo "   Open Notification Agent → Messages tab"
echo ""

