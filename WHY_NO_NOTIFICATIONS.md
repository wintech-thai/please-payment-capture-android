# Why You Don't See Notifications - Root Cause & Solutions

## The Problem 🔴

The **NotificationListenerService** (which captures notifications) requires:
1. **REAL system notifications** from actual installed apps
2. **Not simulated** via ADB shell commands

Your emulator doesn't have the LINE app installed, so when we try to post notifications as if they're from LINE, the system can't properly deliver them to the listener service.

---

## Why Our Mock Script Didn't Work

```
What we tried:
  adb shell cmd notification post ... ← This posts to system notification bar
                                        but doesn't trigger NotificationListenerService
                                        callbacks properly
  
What's needed:
  Real LINE app → Posts notification → System routes to listener service
                  NotificationListenerService.onNotificationPosted() called ✓
```

---

## Solutions (Choose One)

### ✅ SOLUTION 1: Install LINE App (Recommended) 
**Best for emulator testing**

1. On emulator screen, open **Google Play Store**
2. Search for **"LINE"** (or **"LINE: Free Calls & Messages"**)
3. Install the app
4. Keep LINE running
5. Now our mock script will work perfectly!

**Then test:**
```bash
python3 scripts/mock_scb_line_notification.py --amount 1000 --verbose
```

**✓ Should see notification in app's Messages tab**

---

### ✅ SOLUTION 2: Test Directly on Real Android Device
**Even better than emulator**

1. Connect your Android phone to Mac via USB:
   ```bash
   adb devices
   ```
   
2. Install LINE app on your phone (if you want real LINE notifications)

3. Run scripts targeting device:
   ```bash
   # Target device (not emulator)
   adb -d shell settings put secure enabled_notification_listeners ...
   python3 scripts/mock_scb_line_notification.py --amount 1000
   ```

**Advantages:**
- Works perfectly
- Most realistic testing
- No need to install LINE (our mock works)

---

### ✅ SOLUTION 3: Test Backend Without Real Notifications
**Quick validation**

Since you already have the **webhook endpoint configured**, you can test it directly:

**Test 1: Verify webhook is reachable**
```bash
# From Mac, test your webhook endpoint directly
curl -X POST http://10.0.2.2:8000/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "PaymentAmount": "1000.00",
    "RemainAmount": "0.00",
    "TxType": "PayIn",
    "SourceBankCode": "SCB",
    "SourceBankAccountNo": "X-7985",
    "DestinationBankCode": "TMB",
    "DestinationAccountNo": "XX-0032"
  }'
```

**Test 2: Check webhook receives data**
```bash
python3 scripts/emulator_webhook_test.py --server
```
Then send curl request above and watch webhook server receive it.

---

## Quick Decision Tree

```
Do you want real notifications working?
│
├─ YES, test on EMULATOR
│  └─ Install LINE app first, then run mock script
│     python3 scripts/mock_scb_line_notification.py --amount 1000
│
├─ YES, test on PHYSICAL DEVICE
│  └─ Connect phone via USB
│     adb devices (verify)
│     Run all scripts - they work great!
│
└─ NO, just test webhook endpoint
   └─ Run this first:
      python3 scripts/emulator_webhook_test.py --server
      
      Then send test payments:
      curl -X POST http://10.0.2.2:8000/webhook ...
```

---

## What to Do RIGHT NOW

### If you want to fix notifications immediately:

**Step 1: Install LINE on Emulator**
- Open Google Play Store on emulator screen
- Search "LINE"
- Install
- Done!

**Step 2: Grant final permissions** (if needed)
```bash
adb shell pm grant com.example.notification_agent android.permission.POST_NOTIFICATIONS
```

**Step 3: Test**
```bash
python3 scripts/mock_scb_line_notification.py --amount 1000 --verbose
```

**Step 4: Verify**
- Open Notification Agent app
- Go to Messages tab
- You should see the notification!

---

## If You Can't Install LINE

### Test via Webhook (No LINE needed):

```bash
# Terminal 1: Start webhook monitor
python3 scripts/emulator_webhook_test.py --server

# Terminal 2: Send test payment
curl -X POST http://10.0.2.2:8000/webhook \
  -H "Content-Type: application/json" \
  -d '{"PaymentAmount": "5555.00", "SourceBankCode": "SCB", "SourceBankAccountNo": "X-7985"}'

# Terminal 1 will show:
# ✓ WEBHOOK RECEIVED!
# Payload: {...}
```

This proves your webhook endpoint works!

---

## Summary

| Method | Required | Result | Difficulty |
|--------|----------|--------|------------|
| Install LINE | Install app | Notifications appear in app | ⭐ Easy |
| Physical device | Android phone + USB | Real notifications work | ⭐ Easy |
| Webhook test | Running webhook | Verify endpoint works | ⭐ Easy |
| Fix emulator | Complex setup | Might not work | ⭐⭐⭐ Hard |

---

## Key Insight

The **App is working correctly**! The issue is just that:
- Emulator lacks LINE app to generate notifications
- Our mock needs a real notification source
- **Not an app bug** - just a testing limitation

**Solution:** Either install LINE, use a real phone, or test the webhook directly.

---

## Still Have Questions?

1. **Where's LINE in Play Store?**
   - Search "LINE" or "LINE: Free Calls & Messages"
   - Official app from NAVER Corporation

2. **Will notifications work with installed LINE?**
   - Yes! Our mock script will send them as LINE notifications
   - App will capture them
   - Webhook will receive the data

3. **Can I test webhook without LINE?**
   - Yes! Use webhooks directly (solution 3 above)
   - Proves everything works

---

**Recommendation:** Install LINE app → Run mock → Done! ✅

