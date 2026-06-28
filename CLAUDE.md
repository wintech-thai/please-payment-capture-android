# Notification Agent — Claude Instructions

## Project Overview

Native Android app that captures incoming notifications and SMS messages, applies user-configurable per-source filters, stores data locally in a Room database, and forwards payment events to a remote webhook endpoint. A periodic liveness heartbeat reports device health and flushes pending crash logs.

- **Target SDK**: Android 11 (API 30) through latest
- **Build**: `./gradlew :app:assembleDebug`

## Tech Stack

- Kotlin 2.1, Coroutines + Flow
- Android Gradle Plugin 9.x
- AndroidX: AppCompat, Fragment, Navigation, ViewModel, RecyclerView, Material 3
- Room 2.6 + KSP
- XML layouts + ViewBinding (no Compose)

## Architecture

| Layer | Key classes |
|---|---|
| Data | Room entities, DAOs, `AppDatabase` (v5), `MessageRepository` |
| System | `NotificationCaptureService` (NotificationListenerService) |
| System | `SmsCaptureReceiver` (BroadcastReceiver) |
| Network | `LivenessProbe` — heartbeat POST with device telemetry + crash log flush |
| Network | `BankWebhookDispatcher` — primary multi-bank payment forwarding |
| Network | `WebhookDispatcher` — legacy single-URL forwarding (deprecated) |
| Crash | `CrashReporter` — persists fatal/non-fatal errors to `crash_logs` Room table |
| Worker | `AgentWatchdogWorker` — 15-min watchdog; prunes messages and crash logs |
| UI | Fragments + RecyclerView, `MainViewModel` (StateFlow) |
| App | `NotificationAgentApp` — manual DI only, `PermissionHelper` |

### Room Database

- **Version**: 5
- **Entities**: `MessageEntity` (messages), `FilterRuleEntity` (filter_rules), `CrashLogEntity` (crash_logs)
- **Migrations**: 1→2 (forwardToWebhook column), 2→3 (dedupeKey), 3→4 (rebuild messages table), 4→5 (crash_logs table)

## Key Conventions

**Data access**
- DAOs: `suspend` for writes, `Flow` for reads — never access DAOs from the main thread
- Centralized filtering lives in `MessageRepository.shouldCapture()` — never duplicate logic elsewhere

**Coroutines**
- Services and receivers: `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
- Use `BroadcastReceiver.goAsync()` when needed
- ViewModels: `viewModelScope`; Fragments: `lifecycleScope.launch { repeatOnLifecycle(...) { } }`

**UI**
- ViewBinding only; null binding in `onDestroyView`
- Prefer StateFlow/SharedFlow over LiveData for new code
- All user-facing strings in `res/values/strings.xml` — no hard-coded strings

**Permissions**
- Runtime: RECEIVE_SMS, READ_SMS, POST_NOTIFICATIONS (Android 13+)
- Use `ActivityResultContracts.RequestMultiplePermissions`
- Gate Android version differences with `Build.VERSION.SDK_INT`

## AndroidManifest Rules

- Declare new permissions adjacent to the SMS/notification permission block with a justification comment
- `NotificationListenerService` must have `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` and the matching intent-filter
- `SmsCaptureReceiver` must require `android.permission.BROADCAST_SMS` and listen for `android.provider.Telephony.SMS_RECEIVED`
- **Never** set `android:exported="false"` on system-bound components

## Crash Logging

`CrashReporter` is an `object` that intercepts uncaught exceptions and accepts manual `report()` calls.

- **Fatal crashes**: persisted synchronously via `runBlocking` before the default handler kills the process
- **Non-fatal errors**: persisted via a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` launch
- Stack traces are capped at 4 000 characters before storage
- `LivenessProbe.ping()` fetches up to 20 unsent logs, embeds them as a flat `crashes` array in the heartbeat JSON, then marks them sent on HTTP 2xx
- Sent logs older than 48 h are deleted immediately after a successful ping
- `AgentWatchdogWorker` hard-deletes all crash logs older than 7 days regardless of sent status

### Heartbeat payload shape (Option A — flat)

```json
{
  "CPU": "8", "Memory": "4", "OsVersion": "11",
  "AppVersion": "1.2.3", "Battery": "87", "Model": "Google Pixel 6",
  "DeviceId": "abc123", "Storage": "12/64 GB", "Network": "wifi", "Uptime": "3",
  "crashes": [
    {
      "id": 1,
      "level": "FATAL",
      "tag": "CrashReporter",
      "thread": "main",
      "exception": "java.lang.NullPointerException",
      "message": "Cannot invoke method on null receiver",
      "stackTrace": "java.lang.NullPointerException\n\tat com.example...",
      "occurredAt": 1751053100000,
      "appVersion": "1.2.3"
    }
  ]
}
```

## Things to Avoid

- Do not make this app the default SMS handler
- No Compose, Hilt, Dagger, or Retrofit
- No Room DAO calls on the main thread
- Do not add a second crash reporting SDK — all crash persistence goes through `CrashReporter` → `CrashLogDao`

## Documentation Rule

**After every code change, update `CLAUDE.md` before committing.** This includes:
- Architecture table — add/remove/rename classes
- Room Database version and migration list
- Any new feature sections (e.g., Crash Logging, Heartbeat payload shape)
- "Things to Avoid" if new constraints are introduced
- Remove stale statements (e.g., claims about what the app does/doesn't do)

This doc is the authoritative reference for future sessions. Stale docs cause wrong assumptions.

## Code Quality (Codacy)

- After editing any file, run `codacy_cli_analyze` on the edited files if the CLI is available
- After any dependency change (gradle, etc.), run `codacy_cli_analyze` with `tool="trivy"` to check for vulnerabilities
- Fix security issues before continuing; do not check duplicated code or complexity metrics

## CLI / Shell Commands

All shell commands use the `rtk` prefix (transparent hook, zero overhead):

```bash
# Instead of:  git status
# Run:         rtk git status

rtk gain          # show token savings
rtk discover      # find missed rtk opportunities
```
