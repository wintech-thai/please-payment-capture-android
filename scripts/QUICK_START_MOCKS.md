# Mock SCB/KTB LINE Notifications - Quick Start

> Send realistic Thai bank payment notifications to test your Notification Agent app

## 30-Second Setup

You have ADB connected to an Android device? Great! Just run:

```bash
python3 scripts/mock_scb_line_notification.py --amount 1000
```

That's it! Check your Notification Agent app - you should see a new SCB payment notification for 1,000 THB.

## Common Recipes

### Send SCB Payment
```bash
scripts/mock_notification.sh 1000 SCB X-7985
```

### Send KTB Payment  
```bash
scripts/mock_notification.sh 5000 KTB XX7157
```

### Batch Test (10 notifications)
```bash
python3 scripts/batch_mock_notifications.py --count 10 --bank SCB
```

### With Verbose Output
```bash
scripts/mock_notification.sh 1000 SCB X-7985 --verbose
```

### Preview Without Sending
```bash
scripts/mock_notification.sh 1000 SCB X-7985 --dry-run
```

## Full Documentation

See [`MOCK_NOTIFICATIONS_GUIDE.md`](./MOCK_NOTIFICATIONS_GUIDE.md) for:
- Complete command reference
- Troubleshooting
- Advanced usage & automation
- Real-world testing examples

## Verify Setup

```bash
python3 scripts/validate_mock_setup.py
```

This checks:
- ✓ Python 3.6+
- ✓ ADB installed
- ✓ Device connected
- ✓ All scripts present & executable

## Need Help?

| Issue | Solution |
|-------|----------|
| ADB device not found | [`See troubleshooting guide`](./MOCK_NOTIFICATIONS_GUIDE.md#adb-device-not-found) |
| Permission denied | `chmod +x scripts/*.py scripts/*.sh` |
| Notification not appearing | [`See troubleshooting guide`](./MOCK_NOTIFICATIONS_GUIDE.md#notification-not-appearing-in-app) |

---

**Available Scripts:**
- `mock_scb_line_notification.py` - Main Python script
- `batch_mock_notifications.py` - Batch testing tool
- `mock_notification.sh` - Shell wrapper for quick use
- `validate_mock_setup.py` - System validation
- `MOCK_NOTIFICATIONS_GUIDE.md` - Full documentation

