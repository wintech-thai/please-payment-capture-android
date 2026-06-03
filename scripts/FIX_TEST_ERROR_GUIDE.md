# Test Webhook Payload - Error Fixing Guide

## What Was Improved

The webhook test error handling has been enhanced to provide **detailed, actionable error messages** instead of generic "error" text.

---

## New Error Messages You'll See

### ✅ Success (200-299)
```
Test OK: HTTP 200
```
✓ Webhook payload was successfully sent and received

---

### ❌ Configuration Errors (Before sending)

#### 1. "❌ Webhook is disabled..."
```
Failed: ❌ Webhook is disabled. Please enable the webhook toggle in settings.
```

**Fix:**
1. Open Settings in the app
2. Find the **Webhook** section
3. Toggle it **ON** (should be blue/enabled)
4. Try again

---

#### 2. "❌ Webhook URL is empty..."
```
Failed: ❌ Webhook URL is empty. Please enter: https://your-webhook-endpoint.com/webhook
```

**Fix:**
1. Open Settings
2. Find the **Webhook URL** field
3. Enter: `https://your-webhook-endpoint.com/webhook`
4. Make sure to include `/webhook` at the end!
5. Try again

---

#### 3. "❌ Invalid URL format..."
```
Failed: ❌ Invalid URL format. Must start with http:// or https://
```

**Fix:**
1. Check your URL starts with `http://` or `https://`
2. Example: `https://your-webhook-endpoint.com/webhook`
3. Not: `your-webhook-endpoint.com/webhook` (missing https://)

---

#### 4. "⚠️ Bearer token is empty..."
```
Failed: ⚠️  Bearer token is empty. Webhook may fail on protected endpoints.
```

**Fix:**
1. Open Settings
2. Find the **Bearer Token** field
3. Enter: `YOUR_BEARER_TOKEN`
4. Try again

---

### ❌ Network Errors (When sending)

#### 5. "❌ DNS Error: Cannot resolve hostname..."
```
Failed: ❌ DNS Error: Cannot resolve hostname 'your-webhook-endpoint.com'.
         Check URL and internet connection.
```

**Causes:**
- Internet connection is down
- URL is misspelled
- DNS server unreachable
- Firewall blocking

**Fix:**
1. Check device is connected to WiFi/mobile data
2. Check URL spelling
3. Try: `https://your-webhook-endpoint.com`
4. Test on your computer:
   ```bash
   python3 scripts/test_webhook.py "https://your-webhook-endpoint.com/webhook" \
     --token "YOUR_BEARER_TOKEN" --verbose
   ```

---

#### 6. "❌ Connection refused: Server not reachable..."
```
Failed: ❌ Connection refused: Server not reachable at 'https://your-webhook-endpoint.com/webhook'
```

**Causes:**
- Server is offline
- Port blocked by firewall
- Wrong endpoint path

**Fix:**
1. Verify webhook URL: `https://your-webhook-endpoint.com/webhook`
2. Check if `/webhook` path is included
3. Verify server is running
4. Test connectivity:
   ```bash
   curl https://your-webhook-endpoint.com/webhook
   ```

---

#### 7. "❌ Network error: ..."
```
Failed: ❌ Network error: [specific error]
```

**Generic network error - could be multiple causes**

**Fix:**
1. Check internet connection
2. Try again after a few seconds
3. Restart the app
4. Test with:
   ```bash
   python3 scripts/test_webhook.py "https://your-webhook-endpoint.com/webhook" \
     --token "YOUR_BEARER_TOKEN" --verbose
   ```

---

### ❌ HTTP Response Errors

#### 8. "HTTP 400 Bad Request"
```
Failed: HTTP 400
```

**Cause:** Server rejected the payload (invalid JSON format)

**Fix:**
- This is rare with the current implementation
- Contact server admin to check logs

---

#### 9. "HTTP 401 Unauthorized"
```
Failed: HTTP 401: {"error": "Invalid API key"}
```

**Cause:** Bearer token is wrong or expired

**Fix:**
1. Check Bearer Token field: should be `YOUR_BEARER_TOKEN`
2. Verify no extra spaces or typos
3. Token may have expired - get new one from admin

---

#### 10. "HTTP 500 Internal Server Error"
```
Failed: HTTP 500: Failed to write log
```

**Cause:** Server-side error

**Fix:**
- Check server logs
- Contact server admin
- Try again after server is fixed

---

## Quick Troubleshooting Flowchart

```
Click "Test Webhook"
    ↓
Error shown?
    ├─ NO → Success! ✅
    │
    ├─ "webhook disabled" → Enable webhook toggle
    │
    ├─ "webhook url empty" → Enter URL with /webhook path
    │
    ├─ "Invalid URL format" → Check URL starts with https://
    │
    ├─ "Bearer token is empty" → Enter token: YOUR_BEARER_TOKEN
    │
    ├─ "DNS Error" → Check URL and internet connection
    │
    ├─ "Connection refused" → Check URL path and server
    │
    ├─ "HTTP 401" → Check bearer token
    │
    ├─ "HTTP 500" → Contact server admin
    │
    └─ Other error → Check device logs with logcat
```

---

## Testing on Your Computer

To verify the webhook endpoint is working before trying from the app:

```bash
# Test with the provided script
python3 scripts/test_webhook.py \
  "https://your-webhook-endpoint.com/webhook" \
  --token "YOUR_BEARER_TOKEN" \
  --verbose
```

Expected output:
```
=== RESULT ===
Success: True
HTTP Code: 200
Message: HTTP 200 OK
Response: {"message": "Webhook received and logged", "status": "success"}
```

---

## Checking App Logs

If you need more details, check Android Logcat:

```bash
# Watch app logs in real-time
adb logcat -s "WebhookDispatcher"

# Look for these patterns in logs:
# - "webhook success: HTTP 200" → Success
# - "webhook failure: HTTP XXX" → Server error
# - "webhook exception..." → Network error
```

---

## Configuration Checklist

Before testing, verify all these:

```
✅ Webhook Toggle
   Location: Settings → Webhook section
   Status: Should be ON (blue/enabled)
   
✅ Webhook URL
   Location: Settings → Webhook URL field
   Value: https://your-webhook-endpoint.com/webhook
   Note: MUST include /webhook at end
   
✅ Bearer Token
   Location: Settings → Bearer Token field
   Value: YOUR_BEARER_TOKEN
   Note: Check for no extra spaces
   
✅ Internet Connection
   Check: Device has WiFi or mobile data active
   Test: Open browser to any website
   
✅ Server Status
   Check: https://your-webhook-endpoint.com is reachable
   Test: python3 scripts/test_webhook.py ... --verbose
```

---

## Still Having Issues?

1. **Gather info:**
   ```bash
   # Show expanded error message from logcat
   adb logcat -s "WebhookDispatcher" -v long
   ```

2. **Test locally:**
   ```bash
   python3 scripts/test_webhook.py \
     "https://your-webhook-endpoint.com/webhook" \
     --token "YOUR_BEARER_TOKEN" \
     --verbose
   ```

3. **Share details:**
   - Exact error message from app toast
   - Output from test_webhook.py script
   - URL and token values (sanitized)
   - Device model and Android version

---

## Code Changes Made

The improvements were made to `WebhookDispatcher.kt`:

1. **Line 82-86:** Better validation messages for configuration
2. **Line 96-134:** Enhanced error handling for network issues
3. **Line 122-125:** Catches specific exception types (DNS, Connection, URL format, IO)
4. **Line 126-134:** Returns descriptive error messages

These changes ensure every failure shows you exactly what to fix! 🎯

