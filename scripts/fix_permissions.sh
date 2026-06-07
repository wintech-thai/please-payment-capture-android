#!/bin/bash
# Grant all necessary permissions using different methods

echo "╔═══════════════════════════════════════════════╗"
echo "║  Fixing Notification Permissions              ║"
echo "╚═══════════════════════════════════════════════╝"

DEVICE=$(adb devices | grep "emulator\|device" | head -1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "ERROR: No device connected"
    exit 1
fi

echo "Device: $DEVICE"
echo ""

# Grant individual runtime permissions
echo "Granting runtime permissions..."
adb -s "$DEVICE" shell pm grant com.example.notification_agent android.permission.POST_NOTIFICATIONS 2>&1 | grep -v "Exception" || true
adb -s "$DEVICE" shell pm grant com.example.notification_agent android.permission.INTERNET 2>&1 | grep -v "Exception" || true

# Enable notification listener in settings
echo "Enabling notification listener in system settings..."
adb -s "$DEVICE" shell settings put secure enabled_notification_listeners \
    "com.example.notification_agent/.service.NotificationCaptureService"

# Alternative: Try to enable via am command
echo "Attempting to enable listener service..."
adb -s "$DEVICE" shell cmd notification set_listener_access \
    com.example.notification_agent/.service.NotificationCaptureService allow 2>&1 | grep -v "Error" || true

echo ""
echo "═══════════════════════════════════════════════"
echo "✓ Permissions updated!"
echo "═══════════════════════════════════════════════"
echo ""
echo "IMPORTANT: You must also do this on the emulator:"
echo ""
echo "1. Open Settings on the emulator"
echo "2. Go to: Apps → Notification Agent"
echo "3. Tap 'Notifications'"
echo "4. Toggle 'Allow notifications' ON"
echo ""
echo "5. Alternatively, go to:"
echo "   Settings → Advanced → Notification access (or Accessibility)"
echo "   Find 'Notification Agent' and enable it"
echo ""

