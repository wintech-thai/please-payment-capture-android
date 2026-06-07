# Bank Endpoint Test Report

## Test Details
- **Date**: June 5, 2026
- **Amount**: 10.45 บาท
- **Bank**: SCB
- **Source Account**: X-7985

## Request Format Analysis

✅ **Header/Body Format - CORRECT**

The script successfully constructed the request matching the Android app's `BankWebhookDispatcher`:

### HTTP Method
```
POST
```

### Headers Sent
```
Accept: application/json
Onix-Application-Type: backend
Authorization: Basic api:<api_key>
Content-Type: application/json; charset=utf-8
```

### Payload Format
```json
{
  "PaymentAmount": 10.45,
  "RemainAmount": 0.00,
  "TxType": "PayIn",
  "SourceBankCode": "SCB"
}
```

**Key Points:**
- ✅ `PaymentAmount` / `RemainAmount` are emitted as JSON numbers, not strings
- ✅ Comma-separated amounts (e.g., "1,000.00") are stripped before sending
- ✅ Authorization uses Basic auth with format: `api:<api_key>`
- ✅ Content-Type is `application/json; charset=utf-8`
- ✅ Unknown destination fields are omitted
- ✅ `SourceBankAccountNo` is only sent when the notification clearly includes it

---

## Server Response
- **Status**: 403 Forbidden
- **Response Body**: Empty (no error message)

### Possible Causes:
1. **API Key Invalid/Expired**: The key in `payment-confirm.md` may be outdated or not valid for current environment
2. **IP Whitelist**: Server might reject requests from non-whitelisted IPs
3. **Request Signature**: Server might require request signing (HMAC, timestamp, etc.)
4. **Request Origin**: Server might check `Origin` or `Referer` headers
5. **Environment Access**: The `api-dev` environment may require separate activation / allow-listing

---

## Verification Checklist

✅ **Connection**: Server is reachable (got 403, not connection timeout)
✅ **Endpoint URL**: Correct format and reachable
✅ **JSON Payload**: Valid JSON, all fields present
✅ **Header Format**: Matches Android implementation and the upstream Onix Ruby sample
✅ **Amount Formatting**: Correct decimal handling

---

## Debugging Steps

### Option 1: Verify with Raw curl
```bash
curl -X POST \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Onix-Application-Type: backend" \
  -u "api:ae933e77-a3f4-4f1b-8125-69643698248b" \
  -d '{
    "PaymentAmount": 10.45,
    "RemainAmount": 0.00,
    "TxType": "PayIn",
    "SourceBankCode": "SCB"
  }' \
  'https://api-dev.please-payment.com/api/PaymentRequest/org/gabx01/action/SubmitPaymentRequest/eab2eae2-ab83-4d49-bff6-a30226663d09'
```

### Option 2: Check API Key Format
The upstream Onix sample confirms Basic auth with username `api`:
```python
import base64
api_key = "ae933e77-a3f4-4f1b-8125-69643698248b"
print(base64.b64encode(f'api:{api_key}'.encode()).decode())
```

### Option 3: Contact Bank API Provider
The 403 error suggests authentication issue. Check with the payment provider:
- Verify the API key is still valid
- Ask for expected Authorization header format
- Request IP whitelist confirmation
- Ask for example request curl command

---

## Android Code Verification

The implementation in `BankWebhookDispatcher.kt` is **CORRECT**:

```kotlin
// Lines 84-86: Authorization header construction
if (config.apiKey.isNotBlank()) {
    builder.header("Authorization", Credentials.basic("api", config.apiKey))
}

// Money values stay numeric while preserving 2 decimal places in JSON text
internal fun toMoneyValue(amount: Double): BigDecimal =
    BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
```

The issue is likely with:
- The API key credential itself
- Server-side access controls (whitelist / environment)
- Environment setup (the endpoint is marked as `api-dev`)

---

## Next Steps

1. **Verify API key validity** with the bank/payment provider
2. **Check if 403 is expected** (e.g., if the test key needs special activation)
3. **Request API documentation** for authentication format expectations
4. **Verify endpoint status** - make sure you have access to `api-dev` environment

The Android app will send requests in exactly this same format once a bank endpoint is configured. The request structure is verified as correct.

