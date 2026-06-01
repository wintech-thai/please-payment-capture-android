---
applyTo: "**/AndroidManifest.xml"
---
# Manifest rules

- Declare new permissions next to the SMS / notification block. Add `tools:ignore` only with a justification comment.
- `NotificationListenerService` must declare:
  - `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`
  - intent-filter `android.service.notification.NotificationListenerService`
- `SmsCaptureReceiver` must require `android:permission="android.permission.BROADCAST_SMS"` and listen for `android.provider.Telephony.SMS_RECEIVED`.
- Do not lower `android:exported` to `false` for these components; the system needs to bind them.

