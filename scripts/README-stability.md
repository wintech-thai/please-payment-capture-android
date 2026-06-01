# Stability / Soak Test Harness

This folder contains a small host-side soak test harness for `Notification Agent`.

## Files
- `agent_capture_server.py` — local HTTP receiver for `/webhook` and `/probe`
- `run_stability_soak.sh` — builds, installs, configures the app on the emulator, then repeatedly sends SMS + notifications while collecting memory and crash signals

## What it checks
- app process stays alive during the soak window
- webhook deliveries continue to arrive
- probe deliveries continue to arrive
- no obvious `FATAL EXCEPTION` / `AndroidRuntime` / process-death lines appear in captured logcat
- `dumpsys meminfo` total PSS is sampled over time to spot large upward drift

## Quick run

```bash
chmod +x scripts/run_stability_soak.sh
./scripts/run_stability_soak.sh 300 10
```

Arguments:
- first arg = total duration in seconds (default `300`)
- second arg = interval between stimulus bursts in seconds (default `10`)

## Output
The script writes artifacts under:

```text
build/stability/<timestamp>/
```

Important files:
- `summary.txt`
- `requests.jsonl`
- `meminfo.csv`
- `logcat.txt`
- `setup_test.txt`
- `start_service.txt`

## Notes
- The emulator must already be booted and visible to `adb`.
- The script expects the host machine to have Python 3 and the Android SDK available.
- The current heuristic cannot *prove* the absence of leaks; it flags large observed PSS drift as `leak_suspect=yes`.

