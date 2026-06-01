# Copilot Instructions — Notification Agent

This is a native Android app (Kotlin) that captures **incoming notifications** and **SMS messages**, lets the user filter sources, and stores them locally in Room. Target devices: **Android 11 (API 30) → latest**.

## Tech stack
- Kotlin 2.1, Coroutines + Flow
- Android Gradle Plugin 9.x, Gradle version catalogs (`gradle/libs.versions.toml`)
- AndroidX: AppCompat, Fragment, Navigation Component (xml graph), Lifecycle/ViewModel, RecyclerView, Material 3, ConstraintLayout
- Persistence: Room 2.6 + KSP
- View system: **XML layouts + ViewBinding** (no Jetpack Compose in this project — keep it that way unless asked)

## Architecture
- `data/` — Room entities (`MessageEntity`, `FilterRuleEntity`), DAOs, `AppDatabase`, `MessageRepository`
- `service/NotificationCaptureService` — `NotificationListenerService`, captures other apps' notifications
- `receiver/SmsCaptureReceiver` — `BroadcastReceiver` for `SMS_RECEIVED`, multipart-aware
- `ui/` — Fragments, RecyclerView adapters, `MainViewModel` (AndroidViewModel + StateFlow), `PermissionHelper`
- `NotificationAgentApp` — process-wide manual DI container (`database`, `repository`)
- A single `MainActivity` hosts a `BottomNavigationView` + Navigation Component graph (`res/navigation/nav_graph.xml`) with three destinations: `messagesFragment`, `notificationFiltersFragment`, `smsFiltersFragment`.

## Conventions
- Always use **suspend functions** in DAOs for writes; expose **Flow** for reads.
- Filtering rule is centralized in `MessageRepository.shouldCapture()`. **Do not duplicate** the "no rules → allow all" logic in services/receivers.
- Long-running work in services/receivers must use a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and use `BroadcastReceiver.goAsync()` to avoid ANRs.
- ViewBinding only — never `findViewById`. Null `_binding` in `onDestroyView`.
- Strings always go in `res/values/strings.xml`. No hard-coded user-facing text in Kotlin.
- Permissions:
  - Runtime: `RECEIVE_SMS`, `READ_SMS`, `POST_NOTIFICATIONS` (Android 13+).
  - Notification listener: open `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. Verify with `PermissionHelper.isNotificationListenerEnabled`.
- Handle Android 11–15 differences (e.g. `Build.VERSION.SDK_INT >= TIRAMISU` for `POST_NOTIFICATIONS`).

## Things to avoid
- Becoming the default SMS app (`RoleManager.ROLE_SMS`) — out of scope, would require READ/WRITE/SEND SMS handler & ContentProvider.
- Adding network/HTTP code without explicit user request.
- Switching to Compose, Hilt, Dagger, or Retrofit unless the user asks.
- Accessing Room DAOs from the main thread.

## Build / verify
- `./gradlew :app:assembleDebug` (requires Android SDK in `local.properties`).
- After editing a Fragment/ViewModel, also confirm related XML layouts & string resources exist.

<!-- rtk-instructions v2 -->
# RTK — Token-Optimized CLI

**rtk** is a CLI proxy that filters and compresses command outputs, saving 60-90% tokens.

## Rule

Always prefix shell commands with `rtk`:

```bash
# Instead of:              Use:
git status                 rtk git status
git log -10                rtk git log -10
cargo test                 rtk cargo test
docker ps                  rtk docker ps
kubectl get pods           rtk kubectl pods
```

## Meta commands (use directly)

```bash
rtk gain              # Token savings dashboard
rtk gain --history    # Per-command savings history
rtk discover          # Find missed rtk opportunities
rtk proxy <cmd>       # Run raw (no filtering) but track usage
```
<!-- /rtk-instructions -->