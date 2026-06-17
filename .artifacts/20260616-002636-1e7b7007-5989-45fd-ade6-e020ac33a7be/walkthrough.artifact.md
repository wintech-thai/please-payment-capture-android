# Walkthrough: Bank SMS Filtering and Channel Selection

I have implemented support for SMS filtering for SCB bank and added a granular channel selection (LINE/SMS) for each supported bank in the "Bank Endpoints" settings.

## Key Accomplishments

### 1. Bank SMS Support
- Updated `SupportedBank` to include SMS support for SCB via sender `+6627777777`.
- Implemented `SmsBankPaymentParser` to extract payment details (amount, account) from SCB SMS messages.
- Added unit tests for the new parser.

### 2. Granular Channel Selection
- Enhanced the "Bank Endpoints" UI to allow users to:
    - Enable/disable specific channels (LINE and SMS) for each bank.
    - Toggle "Forward to Webhook" independently for each channel.
- Updated `BankGlobalConfig` and `BankConfigRepository` to persist these granular settings.

### 3. Automated Rule Sync
- Updated `NotificationAgentApp` to automatically manage SMS capture rules in `MessageRepository`.
- When a bank's SMS channel is enabled, the corresponding SMS sender (e.g., `+6627777777`) is added to the capture filters.
- When disabled, the capture rule is removed.

### 4. Intelligent Routing
- The `onCaptured` callback now checks both LINE and SMS parsers.
- It consults the global bank configuration to decide whether to process the payment and whether to forward it to the webhook based on the source channel.

## Verification Summary

### Automated Tests
- Created `SmsBankPaymentParserTest` to verify correct parsing of SCB SMS.
- Fixed a minor regression in `BankWebhookDispatcherTest`.
- All unit tests in the `:app` module passed.

### Manual Verification Steps
1. Open **Settings** -> **Bank Endpoints**.
2. Enable **SCB**.
3. You will see **LINE** and **SMS** options under SCB.
4. Enable **SMS** and toggle **Forward to Webhook**.
5. Simulate an SCB SMS using adb:
   ```bash
   adb emu sms send +6627777777 "SCB: ได้รับเงิน 100.00 บ. จาก x1234 เข้าบัญชี x7890"
   ```
6. Verify in the app's **Messages** tab that the SMS is captured and the status shows successful webhook delivery.
7. Disable **Forward to Webhook** for SMS and verify that new SMS are captured but not forwarded.
8. Disable **SMS** for SCB and verify that new SMS from `+6627777777` are no longer captured.
