# Please Payment Capture Android / Notification Agent

> Android agent สำหรับรับ SMS และ Notification จากเครื่อง Android แล้วส่งต่อไปยัง webhook พร้อมส่ง liveness probe เพื่อตรวจสอบว่า agent ยังทำงานอยู่
>
> Android agent that captures SMS and notifications on-device, forwards selected events to a webhook, and sends periodic liveness probes so the backend can verify the agent is still running.

---

## สารบัญ / Table of Contents

- [1. ภาพรวมระบบ / Overview](#1-ภาพรวมระบบ--overview)
- [2. ความสามารถหลัก / Main Features](#2-ความสามารถหลัก--main-features)
- [3. ความต้องการของระบบ / Requirements](#3-ความต้องการของระบบ--requirements)
- [4. การติดตั้งและตั้งค่า / Installation and Setup](#4-การติดตั้งและตั้งค่า--installation-and-setup)
- [5. สิทธิ์ที่ต้องอนุญาต / Required Permissions](#5-สิทธิ์ที่ต้องอนุญาต--required-permissions)
- [6. วิธีใช้งานหน้าจอ / UI Guide](#6-วิธีใช้งานหน้าจอ--ui-guide)
- [7. การตั้งค่า Webhook และ Probe / Webhook and Probe Setup](#7-การตั้งค่า-webhook-และ-probe--webhook-and-probe-setup)
- [8. การเลือก Source ที่ต้องการรับ / Selecting Sources](#8-การเลือก-source-ที่ต้องการรับ--selecting-sources)
- [9. การทำงานแบบ Always-on / Always-on Mode](#9-การทำงานแบบ-always-on--always-on-mode)
- [10. การ build / Building](#10-การ-build--building)
- [11. การทดสอบบน Emulator / Emulator Testing](#11-การทดสอบบน-emulator--emulator-testing)
- [12. Stability / Soak Test](#12-stability--soak-test)
- [13. Troubleshooting](#13-troubleshooting)
- [14. Install via GitHub Releases](#14-install-via-github-releases)
- [15. Bank Endpoint Configuration (Webhook Forwarding)](#15-bank-endpoint-configuration-webhook-forwarding)
- [16. App Lock (PIN)](#16-app-lock-pin)
- [17. Upgrading the App](#17-upgrading-the-app)
- [18. CI/CD & Release Signing](#18-cicd--release-signing)

---

## 1. ภาพรวมระบบ / Overview

### ไทย
แอปนี้เป็น native Android agent ที่ทำงานบน Android 11 ขึ้นไป โดยมีหน้าที่หลักดังนี้:
- รับ **Notification** จากแอปอื่นผ่าน `NotificationListenerService`
- รับ **SMS** โดยไม่ต้องตั้งเป็น default SMS app
- บันทึกข้อมูลไว้ในเครื่องด้วย Room database
- เลือกได้ว่าจะรับข้อมูลจาก source ใดบ้าง
- เลือกได้ว่าจะส่ง source ใดต่อไปยัง webhook
- ยิง **liveness probe** เป็นช่วงเวลาเพื่อแจ้ง backend ว่า agent ยังทำงานอยู่
- รองรับการทำงานแบบ **always-on foreground service**

### English
This app is a native Android agent for Android 11+ with the following responsibilities:
- Capture **notifications** from other apps via `NotificationListenerService`
- Capture **incoming SMS** without becoming the default SMS app
- Store captured data locally using Room
- Let the user choose which sources should be captured
- Let the user choose which sources should also be forwarded to a webhook
- Send periodic **liveness probes** so the backend can verify the agent is alive
- Support **always-on foreground service** mode

---

## 2. ความสามารถหลัก / Main Features

### ไทย
- รับ Notification จากแอปต่าง ๆ
- รับ SMS แบบ multipart-aware
- filter ราย source สำหรับ Notification และ SMS
- switch แยกสำหรับ “เก็บข้อมูล” และ “ส่งต่อ webhook”
- ตั้งค่า Webhook URL
- ตั้งค่า Probe URL, interval, timeout
- ใช้ Bearer token ที่ฝังจาก `local.properties`
- รองรับ `http://` และ `https://`
- มีหน้า Status สำหรับดูสถานะ service / permissions / counters
- มีโหมด keep-alive ด้วย foreground service + alarm watchdog + boot restore

### English
- Capture notifications from installed apps
- Capture multipart SMS messages
- Per-source filtering for notifications and SMS
- Separate control for “capture locally” and “forward to webhook”
- Configurable Webhook URL
- Configurable Probe URL, interval, and timeout
- Uses an embedded Bearer token from `local.properties`
- Supports both `http://` and `https://`
- Includes a Status screen for service / permission / counter visibility
- Includes keep-alive mode with foreground service + alarm watchdog + boot restore

---

## 3. ความต้องการของระบบ / Requirements

### ไทย
- Android 11 (API 30) หรือใหม่กว่า
- Android SDK ในเครื่องสำหรับการ build
- Java Runtime จาก Android Studio (`jbr`) หรือ JDK 11+
- Python 3 หากต้องการใช้ local webhook receiver หรือ soak test harness

### English
- Android 11 (API 30) or newer
- Android SDK installed locally for builds
- Android Studio JBR or JDK 11+
- Python 3 if you want to use the local webhook receiver or soak test harness

---

## 4. การติดตั้งและตั้งค่า / Installation and Setup

### ไทย
1. ติดตั้ง APK ลงอุปกรณ์หรือ emulator
2. เปิดแอปครั้งแรก
3. อนุญาตสิทธิ์ SMS และ Notifications ตามที่แอปร้องขอ
4. เปิด Notification access ให้แอป
5. ไปที่หน้า `Settings` เพื่อใส่ Webhook URL และ Probe URL
6. ไปที่หน้า filter เพื่อเลือก source ที่ต้องการเก็บ/ส่งต่อ
7. หากต้องการให้ agent ทำงานต่อเนื่อง ให้เปิด keep-alive

### English
1. Install the APK on a device or emulator
2. Open the app for the first time
3. Grant SMS and notification-related permissions when prompted
4. Enable Notification access for the app
5. Go to `Settings` and configure the Webhook URL and Probe URL
6. Open the filter screens and choose which sources should be captured/forwarded
7. Enable keep-alive if the agent must remain active continuously

---

## 5. สิทธิ์ที่ต้องอนุญาต / Required Permissions

### ไทย
แอปนี้อาจต้องใช้สิทธิ์ต่อไปนี้:
- `RECEIVE_SMS`
- `READ_SMS`
- `POST_NOTIFICATIONS` (Android 13+)
- Notification listener access
- Battery optimization exemption (ถ้าเปิด keep-alive)
- Exact alarm / foreground service permissions สำหรับการทำงานต่อเนื่อง

### English
The app may require the following permissions:
- `RECEIVE_SMS`
- `READ_SMS`
- `POST_NOTIFICATIONS` (Android 13+)
- Notification listener access
- Battery optimization exemption (when keep-alive is enabled)
- Exact alarm / foreground service permissions for persistent operation

---

## 6. วิธีใช้งานหน้าจอ / UI Guide

### 6.1 หน้า Status / Status Screen

#### ไทย
ใช้สำหรับดูสถานะของระบบ เช่น:
- foreground service ทำงานหรือไม่
- uptime
- สิทธิ์ที่อนุญาตแล้ว/ยังไม่อนุญาต
- webhook counters
- probe counters และเวลาการ ping ล่าสุด

#### English
Used to inspect runtime state, such as:
- whether the foreground service is running
- uptime
- granted/missing permissions
- webhook counters
- probe counters and last ping information

### 6.2 หน้า Messages / Messages Screen

#### ไทย
แสดงข้อความที่ถูก capture แล้วจาก SMS และ Notification โดยเรียงตามเวลา

#### English
Displays captured SMS and notification records in time order.

### 6.3 หน้า Settings / Settings Screen

#### ไทย
ใช้สำหรับ:
- ตั้งค่า Webhook URL
- ตั้งค่า Probe URL
- ตั้งค่า interval / timeout
- เปิด keep-alive
- เปิดหน้าจัดการ Notification Filters / SMS Filters

#### English
Used to:
- configure the Webhook URL
- configure the Probe URL
- configure interval / timeout
- enable keep-alive
- navigate to Notification Filters / SMS Filters

---

## 7. การตั้งค่า Webhook และ Probe / Webhook and Probe Setup

### ไทย
ในหน้า `Settings`:

#### Webhook
- เปิด switch `Enable webhook forwarding`
- ใส่ URL ปลายทาง เช่น:
  - `https://example.com/webhook`
  - `http://10.0.2.2:8088/webhook`
- กด `Send test payload` เพื่อทดสอบการส่ง

#### Probe
- เปิด switch `Enable liveness probe`
- ใส่ Probe URL เช่น:
  - `https://example.com/probe`
  - `http://10.0.2.2:8088/probe`
- ตั้งค่า:
  - Interval (ค่า default 15 วินาที)
  - Timeout (ค่า default 3000 ms)
- กด `Send test ping` เพื่อทดสอบ

### English
In `Settings`:

#### Webhook
- Enable `Enable webhook forwarding`
- Enter a target URL such as:
  - `https://example.com/webhook`
  - `http://10.0.2.2:8088/webhook`
- Tap `Send test payload` to verify delivery

#### Probe
- Enable `Enable liveness probe`
- Enter a probe URL such as:
  - `https://example.com/probe`
  - `http://10.0.2.2:8088/probe`
- Configure:
  - Interval (default 15 seconds)
  - Timeout (default 3000 ms)
- Tap `Send test ping` to verify probe delivery

---

## 8. การเลือก Source ที่ต้องการรับ / Selecting Sources

### ไทย
แอปมี 2 กลุ่ม source:
- Notification sources (แอปต่าง ๆ)
- SMS sources (เบอร์, sender ID, keyword)

การตั้งค่ามี 2 ระดับ:
1. `enabled` = จะเก็บข้อความจาก source นี้หรือไม่
2. `forwardToWebhook` = ถ้าเก็บแล้ว จะส่งต่อไป webhook หรือไม่

> หมายเหตุ: หากไม่มี rule ของ source type นั้นเลย ระบบจะ allow all สำหรับการ capture แต่ **จะไม่ forward** จนกว่าจะมี rule ที่เปิด `forwardToWebhook`

### English
There are two source groups:
- Notification sources (installed apps)
- SMS sources (phone number, sender ID, keyword)

Each source has two controls:
1. `enabled` = whether the source should be captured locally
2. `forwardToWebhook` = whether matching captured messages should also be sent to the webhook

> Note: If a source type has no rules at all, capture defaults to allow-all, but **forwarding does not happen** until a matching rule with `forwardToWebhook=true` exists.

---

## 9. การทำงานแบบ Always-on / Always-on Mode

### ไทย
ถ้าต้องการให้ agent ทำงานต่อเนื่อง:
- เปิด `Run as always-on foreground service`
- อนุญาต battery optimization exemption
- อนุญาต notification access
- ตรวจสอบว่า foreground notification ของ agent แสดงอยู่

ระบบจะใช้:
- foreground service
- exact alarm watchdog
- boot receiver
- periodic worker เป็นตัวช่วยฟื้นการทำงาน

### English
If the agent must stay alive continuously:
- Enable `Run as always-on foreground service`
- Allow battery optimization exemption
- Allow notification access
- Confirm the agent foreground notification is visible

The system uses:
- a foreground service
- an exact-alarm watchdog
- a boot receiver
- a periodic worker as a recovery mechanism

---

## 10. การ build / Building

### ไทย
ใช้คำสั่งนี้เพื่อ build debug APK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/linus/AndroidStudioProjects/notificationagent
./gradlew :app:assembleDebug
```

### English
Use this command to build the debug APK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/linus/AndroidStudioProjects/notificationagent
./gradlew :app:assembleDebug
```

---

## 11. การทดสอบบน Emulator / Emulator Testing

### ไทย
ตัวอย่างการส่ง SMS และ Notification บน emulator:

```bash
export ANDROID_HOME=/Users/linus/Library/Android/sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

adb -s emulator-5554 emu sms send +66812345678 "hello from emulator"
adb -s emulator-5554 shell cmd notification post -t 'Test' tag 'Body from shell'
```

ถ้าใช้ local webhook receiver บนเครื่อง host:

```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
python3 scripts/agent_capture_server.py --log build/stability/manual-requests.jsonl
```

### English
Example commands for sending SMS and notifications on an emulator:

```bash
export ANDROID_HOME=/Users/linus/Library/Android/sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

adb -s emulator-5554 emu sms send +66812345678 "hello from emulator"
adb -s emulator-5554 shell cmd notification post -t 'Test' tag 'Body from shell'
```

If you want to use a local webhook receiver on the host machine:

```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
python3 scripts/agent_capture_server.py --log build/stability/manual-requests.jsonl
```

---

## 12. Stability / Soak Test

### ไทย
มี harness สำหรับรันทดสอบเสถียรภาพอัตโนมัติ โดยจะ:
- ส่ง SMS/notification เป็นรอบ ๆ
- เก็บ memory samples (`dumpsys meminfo`)
- ตรวจ logcat หา crash/process death
- นับ webhook/probe requests

รันได้ด้วย:

```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
DEVICE_SERIAL=emulator-5554 ./scripts/run_stability_soak.sh 300 10
```

ผลลัพธ์จะอยู่ใน:

```text
build/stability/<timestamp>/
```

ไฟล์สำคัญ:
- `summary.txt`
- `requests.jsonl`
- `meminfo.csv`
- `logcat.txt`

### English
An automated stability harness is included. It will:
- send SMS/notifications in a loop
- collect memory samples via `dumpsys meminfo`
- inspect logcat for crashes/process death
- count webhook/probe requests

Run it with:

```bash
cd /Users/linus/AndroidStudioProjects/notificationagent
DEVICE_SERIAL=emulator-5554 ./scripts/run_stability_soak.sh 300 10
```

Results are written under:

```text
build/stability/<timestamp>/
```

Important files:
- `summary.txt`
- `requests.jsonl`
- `meminfo.csv`
- `logcat.txt`

---

## 13. Troubleshooting

### ไทย
**ปัญหา: ไม่ได้รับ Notification**
- ตรวจว่าเปิด Notification access แล้ว
- เปิดแอปอย่างน้อยหนึ่งครั้งหลังจากเปิด listener
- ตรวจ rule ว่า source นั้น `enabled=true` และ `forwardToWebhook=true`

**ปัญหา: ไม่ได้รับ SMS**
- ตรวจสิทธิ์ `RECEIVE_SMS` / `READ_SMS`
- ตรวจว่า emulator รองรับ telephony
- ตรวจ SMS rule ว่าตรงกับ sender หรือไม่

**ปัญหา: webhook ไม่เข้า**
- ตรวจ URL ว่าเข้าถึงได้จากอุปกรณ์ (`10.0.2.2` สำหรับ emulator)
- ตรวจว่า bearer token ถูกฝังจาก `local.properties`
- ตรวจว่า source ถูกเปิด `forwardToWebhook`

**ปัญหา: agent ถูกหยุด**
- เปิด keep-alive
- อนุญาต battery optimization exemption
- ตรวจ foreground notification ของ agent

### English
**Problem: Notifications are not captured**
- Verify Notification access is enabled
- Launch the app at least once after enabling the listener
- Check that the source rule has `enabled=true` and `forwardToWebhook=true`

**Problem: SMS are not captured**
- Verify `RECEIVE_SMS` / `READ_SMS`
- Verify the emulator/device supports telephony
- Check that the SMS rule matches the sender

**Problem: Webhook does not receive requests**
- Verify the URL is reachable from the device (`10.0.2.2` for emulator)
- Verify the bearer token is embedded from `local.properties`
- Verify the source has `forwardToWebhook=true`

**Problem: Agent stops running**
- Enable keep-alive
- Allow battery optimization exemption
- Confirm the agent foreground notification is present

---

## Local Build Configuration / การตั้งค่า local.properties

### ไทย
ไฟล์ `local.properties` ควรมีอย่างน้อย:

```properties
sdk.dir=/path/to/Android/sdk
agent.webhook.token=YOUR_REAL_TOKEN
```

### English
Your `local.properties` should contain at least:

```properties
sdk.dir=/path/to/Android/sdk
agent.webhook.token=YOUR_REAL_TOKEN
```

> `agent.webhook.token` will be embedded into `BuildConfig.AGENT_WEBHOOK_TOKEN` and sent as `Authorization: Bearer <token>`.


## 14. Install via GitHub Releases

Pre-built APKs are published automatically on the repository's
[**Releases**](../../releases) page whenever a version tag (`v1.2.3`) is pushed.

### Download & install
1. Open the **Releases** page of this repository.
2. Pick the release you want (the latest is at the top).
3. Under **Assets**, download the APK named `app-v<version>-release.apk`
   (e.g. `app-v1.0.0-release.apk`).
4. Copy/transfer the APK to the Android device.
5. On the device, open the file. If prompted, allow
   **Install unknown apps** for the browser / file manager you used
   (Settings → Apps → Special access → Install unknown apps).
6. Tap **Install**, then open **Notification Agent**.
7. Grant the requested permissions (SMS, notifications) and enable
   **Notification access** when prompted.

> The APK is signed in CI using the project release keystore (see
> [section 18](#18-cicd--release-signing)). If you build locally without a
> keystore the release APK is produced **unsigned** and must be signed before
> a device will install it.

---

## 15. Bank Endpoint Configuration (Webhook Forwarding)

The app can forward payment notifications to one or more **bank endpoints**.
Each device stores its own independent list of endpoints (persisted locally
with **DataStore**, so it survives reboots and app upgrades).

### Open the screen
From the main screen, open the toolbar overflow menu (⋮) and tap
**Manage bank endpoints**.

### Add / edit an endpoint
Tap the **+** (Add Bank) button and fill in:

| Field          | Description                                                            |
| -------------- | ---------------------------------------------------------------------- |
| **Bank name**  | Source bank code, e.g. `KTB`, `SCB`. Sent as `SourceBankCode`.          |
| **Endpoint URL** | Full webhook URL, e.g. `https://api-dev.example.com/.../{bankAccountId}`. Must start with `http://` or `https://`. |
| **API key**    | Optional. When set, the request uses ONIX-style Basic Auth (`Authorization: Basic ...` with username `api`) and also sends `Onix-Application-Type: backend`. |
| **Enabled**    | Toggle to enable/disable forwarding for this bank without deleting it.  |

Use the switch on each row to enable/disable a bank, the pencil to edit, and
the trash icon to delete. The current **app version** is shown at the bottom of
the list.

### Payload format
Each forwarded request is a JSON body. These keys are **always** present:

```json
{
  "PaymentAmount": 1234.50,
  "RemainAmount": 0.00,
  "TxType": "PayIn",
  "DestinationBankCode": "TMB",
  "DestinationAccountNo": "XX-0032"
}
```

These keys are **only included when they have a non-blank value**:

| Key                   | Source                                  |
| --------------------- | --------------------------------------- |
| `SourceBankCode`      | the bank's **Bank name**                |
| `SourceBankAccountNo` | the source account passed by the caller |

The forwarding API is `BankWebhookDispatcher.sendWebhook(configId, amount, fromAccount)`.

> **Note:** This multi-bank dispatcher is the primary forwarding mechanism. The
> older single-URL webhook in **Settings** is deprecated and kept only for
> backwards compatibility / instrumentation tests.

---

## 16. App Lock (PIN)

You can optionally protect the bank-endpoint screens with a local 4-digit PIN
(stored on-device in DataStore — no server/account is involved).

- Open **Manage bank endpoints** → tap the **lock** icon in the toolbar.
- Enter a 4-digit PIN and tap **Set PIN** to enable the lock.
- Once enabled, the PIN is requested every time the bank screens are opened.
- To turn it off, open the lock screen again and tap **Disable PIN lock**.

The PIN is optional and **off by default**.

---

## 17. Upgrading the App

Installing a newer APK over an existing install **keeps all local data**,
including your bank endpoints, API keys, and PIN.

1. Download the new `app-v<version>-release.apk` from
   [Releases](../../releases).
2. Open it on the device and tap **Install** (Android performs an in-place
   update because the package name and signing key are unchanged).
3. Open the app and confirm your bank endpoints are still listed.

Why your configuration is preserved:
- Bank endpoints + PIN live in **DataStore**, and captured messages in **Room**.
  Both are app-private storage that Android retains across in-place updates.
- The app also declares `android:allowBackup="true"` with backup rules.

> ⚠️ Do **not** uninstall the old version first — uninstalling deletes
> app-private storage and you will lose your endpoints/PIN. Always install the
> new APK **on top** of the existing one.
>
> The update must be signed with the **same** keystore as the installed build,
> otherwise Android rejects it with a signature-mismatch error and you'd have to
> uninstall (losing data). Keep your release keystore safe and reuse it.

---

## 18. CI/CD & Release Signing

CI is defined in [`.github/workflows/build-deploy.yml`](.github/workflows/build-deploy.yml).

**Triggers**
- Push to `develop` or `main` → build APK and upload it as a temporary
  workflow **artifact**.
- Push a tag matching `v*.*.*` (e.g. `v1.0.0`) → build, then create a
  **GitHub Release** and attach the APK as a release asset.

**Pipeline**
1. Set up JDK 17.
2. `./gradlew :app:assembleRelease`.
3. Read `versionName` from the AGP `output-metadata.json` and rename the APK to
   `app-v<version>-release.apk`.
4. Upload artifact (branch pushes) / publish release (tag pushes).

**Signing secrets** (repository → Settings → Secrets and variables → Actions):

| Secret              | Description                                              |
| ------------------- | ------------------------------------------------------- |
| `KEYSTORE`          | Base64 of your `.jks`/`.keystore` (`base64 -i release.keystore`). |
| `KEYSTORE_PASSWORD` | Keystore password.                                      |
| `KEY_ALIAS`         | Signing key alias.                                      |
| `KEY_PASSWORD`      | Key password.                                           |

The Gradle release `signingConfig` reads these from the environment
(`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). When they
are absent (local builds, forked PRs) the release APK is built **unsigned**.

To cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## 19. Troubleshooting — Bank Webhook Forwarding

**A bank endpoint never receives requests**
- Confirm the endpoint row is **Enabled** (row switch is on).
- Confirm the **Endpoint URL** is correct and reachable from the device
  (use `10.0.2.2` to reach the host machine from an emulator).
- If the endpoint requires auth, confirm the **API key** is set — it is sent
  through ONIX-style Basic Auth (`Authorization: Basic ...` with username
  `api`).
- Check the **Status** screen for webhook sent/failed counters and the last
  error (e.g. `HTTP 401`, `HTTP 404`).

**Requests are rejected with 401/403**
- The API key is wrong or missing. Re-enter it in the endpoint editor.

**Requests are rejected with 404**
- The URL path is wrong (e.g. a `{bankAccountId}` placeholder was not replaced).

**Notifications are captured but nothing is forwarded**
- Forwarding requires the captured event to be parsed and dispatched via
  `BankWebhookDispatcher.sendWebhook(...)`. Verify your notification parser is
  wired to call it with the right `configId`.
- Verify **Notification access** is enabled (see section 13).

**Network errors / timeouts**
- The device has no connectivity, or the host is blocking the port.
- For `http://` (cleartext) endpoints, confirm the URL host is permitted by the
  app network-security config.

**Lost my configuration after updating**
- You likely uninstalled the previous version. Reinstall and re-add the
  endpoints; in future install the new APK **on top** of the old one
  (see [section 17](#17-upgrading-the-app)).
