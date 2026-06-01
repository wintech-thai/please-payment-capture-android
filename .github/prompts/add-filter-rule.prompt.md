---
description: Add a new notification or SMS filter rule
mode: agent
---
# Add filter rule

Goal: extend the user-configurable filter system.

Steps the agent must follow:
1. Decide whether the rule is for `SourceType.NOTIFICATION` (matches by package name) or `SourceType.SMS` (matches by sender / substring).
2. Update `MessageRepository.shouldCapture()` only if a brand new matching strategy is needed; otherwise just persist a `FilterRuleEntity` via `repo.upsertRule(...)`.
3. Add UI affordances in the corresponding Fragment (`NotificationFiltersFragment` or `SmsFiltersFragment`) and adapter.
4. Reuse existing strings from `res/values/strings.xml`; add new ones only when needed.
5. Verify on Android 11 (API 30) and the latest SDK — no API-gated code without `Build.VERSION.SDK_INT` check.

