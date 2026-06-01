---
applyTo: "**/*.kt"
---
# Kotlin & Android style

- Target JVM 11, Kotlin 2.1, explicit `Lifecycle`-aware coroutine scopes (`viewModelScope`, `lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }`).
- Prefer `StateFlow` / `SharedFlow` over `LiveData` for new code.
- Data classes for entities & UI models; `enum class` over magic strings.
- Use Room `@Insert(onConflict = REPLACE)` + `suspend` writes; reads return `Flow`.
- For background components (Service, BroadcastReceiver):
  - Create `CoroutineScope(SupervisorJob() + Dispatchers.IO)` for the lifetime of the component.
  - Use `BroadcastReceiver.goAsync()` for any work that suspends.
- Fragments: ViewBinding only, null out `_binding` in `onDestroyView`.
- Permissions: use `ActivityResultContracts.RequestMultiplePermissions`; gate Android 13+ APIs behind `Build.VERSION.SDK_INT >= TIRAMISU`.
- Never hard-code user-facing strings — use `res/values/strings.xml`.

