# Action Guide: Fix Missing Notifications

## The Problem
You don't see notifications because **your emulator doesn't have the LINE app**.

## The Solution (Choose 1)

### Option A: Install LINE (Fastest - 2 min)

**On your emulator screen:**
1. Tap Play Store app
2. Search "LINE"
3. Find "LINE: Free Calls & Messages" by NAVER
4. Tap Install
5. Wait for installation
6. Done!

**Then test:**
```bash
python3 scripts/mock_scb_line_notification.py --amount 1000 --verbose
```

**Result:** Notifications appear in app ✓

---

### Option B: Test Webhook (No wait - 5 min)

You don't need LINE to test if the webhook works.

**Terminal 1:**
```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
python3 scripts/emulator_webhook_test.py --server
```

**Terminal 2:**
```bash
python3 scripts/test_webhook_direct.py
```

**Result:** Webhook receives test payments ✓

(Once LINE is installed, notifications flow through automatically)

---

### Option C: Use Real Phone (Most Reliable)

1. Connect Android phone to Mac via USB cord
2. Run: `adb devices` (verify connection)
3. All scripts work on real device!

**Result:** Everything works perfectly ✓

---

## Files to Know

**Read First:**
- `NOTIFICATION_TESTING_SOLUTION.md` - Complete guide

**Diagnostic:**
- `scripts/diagnose_notifications.py` - Check system status
- `scripts/test_webhook_direct.py` - Test webhook now
- `scripts/emulator_webhook_test.py --server` - Monitor webhooks

---

## Recommendation

**Install LINE on emulator** (fastest way to success)

Then all the mock notification scripts you've created will work perfectly!

---

**That's it! One of these three options will get you testing.** ✅

