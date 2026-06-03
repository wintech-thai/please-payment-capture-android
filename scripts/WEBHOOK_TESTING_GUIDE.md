# Webhook Testing Guide

## Quick Start

To test your webhook endpoint, use the provided test script:

```bash
python3 scripts/test_webhook.py "YOUR_WEBHOOK_URL" --token "YOUR_TOKEN" --verbose
```

### Example

```bash
python3 scripts/test_webhook.py "https://your-webhook-endpoint.com/webhook" --token "YOUR_BEARER_TOKEN" --verbose
```

## Common Issues & Solutions

### 1. **HTTP 404 Not Found**
**Problem:** Webhook endpoint returns 404 error.

**Solution:** Check your URL includes the correct path:
- ❌ `https://your-webhook-endpoint.com` (incorrect - missing path)
- ✅ `https://your-webhook-endpoint.com/webhook` (correct)

### 2. **HTTP 401 Unauthorized**
**Problem:** Webhook returns 401 error.

**Cause:** Bearer token is missing, invalid, or expired.

**Check:**
```bash
# Verify token is not empty
echo "Token: YOUR_TOKEN"

# Check Authorization header format in verbose output
python3 scripts/test_webhook.py "YOUR_URL" --token "YOUR_TOKEN" --verbose
```

**Expected header:** `Authorization: Bearer YOUR_TOKEN`

### 3. **Connection Timeout**
**Problem:** Request times out (no response).

**Possible causes:**
- Webhook server is down
- Network/firewall blocking the connection
- DNS resolution failing
- URL is incorrect

**Debug:**
```bash
# Test DNS resolution
nslookup your-webhook-endpoint.com

# Test basic connectivity
curl -I https://your-webhook-endpoint.com/webhook

# Test with full verbose output
python3 scripts/test_webhook.py "https://your-webhook-endpoint.com/webhook" --token "YOUR_BEARER_TOKEN" --verbose
```

### 4. **HTTP 400 Bad Request**
**Problem:** Webhook returns 400 error.

**Possible causes:**
- Invalid JSON format in payload
- Missing required fields
- Wrong content-type header

**Check with verbose output:**
```bash
python3 scripts/test_webhook.py "YOUR_URL" --token "YOUR_TOKEN" --verbose
```

## Webhook Payload Format

The app sends notifications in this JSON format:

```json
{
  "id": 0,
  "sourceType": "NOTIFICATION",
  "sourceKey": "agent.test",
  "sourceLabel": "Agent test",
  "title": "Test from Notification Agent",
  "text": "Hello from test",
  "timestamp": 1780478802181,
  "deviceId": "device-id",
  "device": "Manufacturer Model",
  "agentVersion": "1.0.0",
  "notificationAppPackage": "com.example.app",
  "notificationAppName": "App Name"
}
```

## Request Headers

The app always sends these headers:

```
POST /webhook HTTP/1.1
Host: your-webhook-server.com
Content-Type: application/json; charset=utf-8
User-Agent: NotificationAgent/1.0.0
Accept: application/json
Authorization: Bearer YOUR_TOKEN
Content-Length: 368
```

## Configuration in Settings

In the Android app settings:
1. Enable webhook: Toggle **Webhook** ON
2. Set URL: `https://your-server.com/webhook`
3. Set bearer token: `YOUR_TOKEN`
4. Click **Test Webhook** to send a test payload

## Success Response

When the webhook succeeds:
- ✅ HTTP Status: 200-299 (success range)
- ✅ Message: "HTTP 200" displayed in app
- ✅ Count incremented: `webhookSentCount` in status

## References

- See `WebhookDispatcher.kt` for implementation
- See `AgentHttpClient.kt` for header formatting
- See `SettingsFragment.kt` for UI integration

